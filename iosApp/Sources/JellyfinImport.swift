import Foundation
import CryptoKit
import ImageIO
import PDFKit
import ZIPFoundation
import LeafShared

/// Remote-only limits; local Files and SMB import policies remain unchanged.
enum JellyfinImportLimits {
    static let compressed: Int64 = 2 * 1024 * 1024 * 1024
    static let entry: UInt64 = 256 * 1024 * 1024
    static let expanded: UInt64 = 8 * 1024 * 1024 * 1024
    static let entries = 10_000
    static let pixels: Double = 100_000_000

    static func checkSpace(at url: URL, required: Int64) throws {
        let values = try url.resourceValues(forKeys: [.volumeAvailableCapacityForImportantUsageKey])
        if let free = values.volumeAvailableCapacityForImportantUsage, free < required + 256 * 1024 * 1024 {
            throw JellyfinError.storage
        }
    }

    static func digest(_ url: URL) throws -> String {
        let input = try FileHandle(forReadingFrom: url)
        defer { try? input.close() }
        var hash = SHA256()
        while let bytes = try input.read(upToCount: 1024 * 1024), !bytes.isEmpty {
            try Task.checkCancellation()
            hash.update(data: bytes)
        }
        return hash.finalize().map { String(format: "%02x", $0) }.joined()
    }

    static func validate(_ url: URL, fileExtension: String) throws -> String {
        let size = Int64(try url.resourceValues(forKeys: [.fileSizeKey]).fileSize ?? 0)
        guard size > 0, size <= compressed else { throw JellyfinError.limit }
        try checkSpace(at: url, required: size + Int64(entry))
        let headerFile = try FileHandle(forReadingFrom: url)
        let header = try headerFile.read(upToCount: 8) ?? Data()
        try headerFile.close()
        let bytes = KotlinByteArray(size: Int32(header.count))
        for (i, byte) in header.enumerated() { bytes.set(index: Int32(i), value: Int8(bitPattern: byte)) }
        let format = ComicFormatDetector.shared.detect(head: bytes, fileExtension: "")
        if fileExtension == "pdf" {
            guard format == .pdf, let pdf = PDFDocument(url: url), !pdf.isLocked,
                  pdf.pageCount > 0, pdf.pageCount <= entries else { throw ComicImportError.invalidComic }
            for index in 0..<pdf.pageCount {
                try Task.checkCancellation()
                guard let page = pdf.page(at: index) else { throw ComicImportError.invalidComic }
                let bounds = page.bounds(for: .mediaBox)
                guard bounds.width.isFinite, bounds.height.isFinite, bounds.width > 0, bounds.height > 0,
                      bounds.width * bounds.height <= pixels else { throw JellyfinError.limit }
            }
        } else {
            guard ["cbz", "zip"].contains(fileExtension), format == .cbz else { throw ComicImportError.invalidComic }
            let archive = try Archive(url: url, accessMode: .read)
            var count = 0
            var declared: UInt64 = 0
            var emitted: UInt64 = 0
            var readablePages = 0
            for item in archive {
                try Task.checkCancellation()
                count += 1
                guard count <= entries, item.uncompressedSize <= entry else { throw JellyfinError.limit }
                declared += item.uncompressedSize // Each addend is bounded above; count is bounded.
                guard declared <= expanded else { throw JellyfinError.limit }
                guard item.type == .file else { continue }
                let isImage = ComicArchiveKt.isImageEntry(name: item.path)
                let sample = url.deletingLastPathComponent().appendingPathComponent(UUID().uuidString)
                if isImage { guard FileManager.default.createFile(atPath: sample.path, contents: nil) else { throw JellyfinError.storage } }
                defer { if isImage { try? FileManager.default.removeItem(at: sample) } }
                let output = isImage ? try FileHandle(forWritingTo: sample) : nil
                defer { try? output?.close() }
                var bytes: UInt64 = 0
                let crc = try archive.extract(item, bufferSize: 64 * 1024, skipCRC32: false) { data in
                    try Task.checkCancellation()
                    bytes += UInt64(data.count)
                    emitted += UInt64(data.count)
                    guard bytes <= entry, emitted <= expanded else { throw JellyfinError.limit }
                    try output?.write(contentsOf: data)
                }
                guard crc == item.checksum, bytes == item.uncompressedSize else { throw ComicImportError.invalidComic }
                if isImage {
                    try output?.synchronize()
                    guard let image = CGImageSourceCreateWithURL(sample as CFURL, nil),
                          let properties = CGImageSourceCopyPropertiesAtIndex(image, 0, nil) as? [CFString: Any],
                          let width = properties[kCGImagePropertyPixelWidth] as? NSNumber,
                          let height = properties[kCGImagePropertyPixelHeight] as? NSNumber else { throw ComicImportError.invalidComic }
                    guard width.doubleValue > 0, height.doubleValue > 0,
                          width.doubleValue * height.doubleValue <= pixels else { throw JellyfinError.limit }
                    readablePages += 1
                }
            }
            guard readablePages > 0 else { throw ComicImportError.invalidComic }
        }
        return try digest(url)
    }
}

/// The pending record is the publication journal. A checksum proves ownership during recovery.
actor JellyfinImports {
    struct Record: Codable, Equatable, Sendable {
        let profileID: String
        let serverID: String
        let userID: String
        let itemID: String
        let filename: String
        let checksum: String
        let title: String
        let versionHint: String?
        var pending: Bool

        /// Profile IDs change after disconnect; remote identity is server + user + item.
        func belongs(to connection: JellyfinConnection) -> Bool {
            serverID == connection.serverID && userID == connection.userID
        }
    }
    struct FileState: Codable { let version: Int; let records: [Record] }
    static let shared = JellyfinImports()
    let file: URL
    init(file: URL? = nil) {
        self.file = file ?? FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("Jellyfin/imports-v1.json")
    }
    private func read() throws -> [Record] {
        guard FileManager.default.fileExists(atPath: file.path) else { return [] }
        let state = try JSONDecoder().decode(FileState.self, from: Data(contentsOf: file))
        guard state.version == 1 else { throw JellyfinError.response }
        return state.records
    }
    private func write(_ records: [Record]) throws {
        try FileManager.default.createDirectory(at: file.deletingLastPathComponent(), withIntermediateDirectories: true)
        try JSONEncoder().encode(FileState(version: 1, records: records)).write(to: file, options: .atomic)
    }
    private var pendingDirectory: URL {
        file.deletingPathExtension().appendingPathExtension("pending")
    }

    func publicationJournal() -> URL {
        pendingDirectory.appendingPathComponent(UUID().uuidString + ".json")
    }

    /// Each import owns a separate journal, writable synchronously from the import IO queue.
    nonisolated static func stage(_ record: Record, at url: URL) throws {
        try FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
        try JSONEncoder().encode(record).write(to: url, options: .atomic)
    }

    func finishPublication(at url: URL) throws {
        var record = try JSONDecoder().decode(Record.self, from: Data(contentsOf: url))
        record.pending = false
        var records = try read().filter { $0.filename != record.filename }
        records.append(record)
        try write(records)
        try FileManager.default.removeItem(at: url)
    }

    func begin(_ record: Record) throws {
        var records = try read()
        records.append(record)
        try write(records)
    }
    func finish(filename: String) throws {
        var records = try read()
        if let index = records.firstIndex(where: { $0.filename == filename }) { records[index].pending = false }
        try write(records)
    }
    func abandon(filename: String) throws { try write(read().filter { $0.filename != filename }) }
    func reconcile(localFiles: [URL]) throws -> [Record] {
        let urls = Dictionary(localFiles.map { ($0.lastPathComponent, $0) }, uniquingKeysWith: { first, _ in first })
        var records = try read()
        let journals = FileManager.default.fileExists(atPath: pendingDirectory.path)
            ? try FileManager.default.contentsOfDirectory(at: pendingDirectory, includingPropertiesForKeys: nil)
                .filter { $0.pathExtension == "json" } : []
        for journal in journals {
            let record = try JSONDecoder().decode(Record.self, from: Data(contentsOf: journal))
            records.removeAll { $0.filename == record.filename }
            records.append(record)
        }
        for index in records.indices {
            if let url = urls[records[index].filename], try JellyfinImportLimits.digest(url) == records[index].checksum {
                records[index].pending = false
            } else { records[index].pending = true }
        }
        // Missing files mean not downloaded. Neither reconciliation nor disconnect deletes comics.
        records.removeAll { urls[$0.filename] == nil || $0.pending }
        try write(records)
        for journal in journals { try FileManager.default.removeItem(at: journal) }
        return records
    }
}
