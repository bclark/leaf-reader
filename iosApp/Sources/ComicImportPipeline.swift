import Foundation
import Darwin
import ImageIO
import PDFKit
import ZIPFoundation
import LeafShared

enum ComicImportCollision: Sendable { case replace, keepBoth, cancel }
enum ComicImportPhase: Sendable { case preparing, publishing }

enum ComicImportError: Error, LocalizedError {
    case invalidName, invalidComic, sizeMismatch, collision
    var errorDescription: String? {
        switch self {
        case .invalidName: return String(localized: "The comic filename is invalid.")
        case .invalidComic: return String(localized: "The file is not a readable comic archive or PDF.")
        case .sizeMismatch: return String(localized: "The downloaded file is incomplete.")
        case .collision: return String(localized: "A comic with this name already exists.")
        }
    }
}

/// A serial IO queue keeps filename resolution and atomic publication together.
/// Cancellation waits for this queue to finish; no staging file outlives its owner.
final class ComicImportPipeline: @unchecked Sendable {
    struct Imported: Sendable {
        let url: URL
        let metadata: ComicMetadataRecord
    }

    private let queue = DispatchQueue(label: "leaf.comic-import", qos: .userInitiated)
    private let directory: URL

    init(directory: URL) {
        self.directory = directory
        queue.async {
            ImportStaging.removeStaleDirectories(in: directory.appendingPathComponent(".leaf-import-staging"))
            ImportStaging.removeStaleDirectories(in: ImportStaging.downloadRoot)
        }
    }

    func importFile(from source: URL, originalName: String, expectedBytes: Int64?,
                    collision: ComicImportCollision,
                    phase: @escaping @Sendable (ComicImportPhase) -> Void) async throws -> Imported {
        let cancellation = OperationCancellation()
        return try await withTaskCancellationHandler {
            try await withCheckedThrowingContinuation { continuation in
                queue.async {
                    // Only this queue resumes the continuation, including cancellation.
                    continuation.resume(with: Result {
                        try self.prepareAndPublish(source: source, name: originalName,
                            expectedBytes: expectedBytes, collision: collision,
                            cancellation: cancellation, phase: phase)
                    })
                }
            }
        } onCancel: { cancellation.cancel() }
    }

    private func prepareAndPublish(source: URL, name: String, expectedBytes: Int64?,
                                   collision: ComicImportCollision, cancellation: OperationCancellation,
                                   phase: @Sendable (ComicImportPhase) -> Void) throws -> Imported {
        try cancellation.check()
        guard !name.isEmpty, name != ".", name != "..", !name.hasPrefix("."),
              !name.contains("/"), !name.contains("\\"), !name.contains("\0") else {
            throw ComicImportError.invalidName
        }
        let fm = FileManager.default
        let root = directory.appendingPathComponent(".leaf-import-staging", isDirectory: true)
        try fm.createDirectory(at: root, withIntermediateDirectories: true)
        let staging = root.appendingPathComponent(UUID().uuidString, isDirectory: true)
        try fm.createDirectory(at: staging, withIntermediateDirectories: true)
        defer { try? fm.removeItem(at: staging) }

        phase(.preparing)
        let input = staging.appendingPathComponent("input")
        let scoped = source.startAccessingSecurityScopedResource()
        do {
            defer { if scoped { source.stopAccessingSecurityScopedResource() } }
            try Self.copy(source, to: input, cancellation: cancellation)
        }
        let size = try input.resourceValues(forKeys: [.fileSizeKey]).fileSize ?? 0
        if let expectedBytes, expectedBytes != Int64(size) { throw ComicImportError.sizeMismatch }
        let handle = try FileHandle(forReadingFrom: input)
        let head: Data
        do {
            defer { try? handle.close() }
            head = try handle.read(upToCount: 8) ?? Data()
        }
        let bytes = KotlinByteArray(size: Int32(head.count))
        for (index, byte) in head.enumerated() { bytes.set(index: Int32(index), value: Int8(bitPattern: byte)) }
        // Empty extension deliberately disables fallback: downloaded bytes must identify the format.
        let format = ComicFormatDetector.shared.detect(head: bytes, fileExtension: "")
        guard format != .unknown else { throw ComicImportError.invalidComic }
        let base = (name as NSString).deletingPathExtension
        guard !base.isEmpty else { throw ComicImportError.invalidName }
        let filename = base + (format == .pdf ? ".pdf" : ".cbz")
        let prepared = staging.appendingPathComponent(filename)
        if format == .cbr {
            try CbrConverter.convertSynchronously(source: input, destination: prepared, checkCancellation: cancellation.check)
        } else {
            try fm.moveItem(at: input, to: prepared)
        }
        try Self.validate(prepared, cancellation: cancellation)
        var destination = directory.appendingPathComponent(filename)
        if fm.fileExists(atPath: destination.path) {
            switch collision {
            case .cancel: throw ComicImportError.collision
            case .replace: break
            case .keepBoth:
                var suffix = 2
                repeat {
                    destination = directory.appendingPathComponent("\(base) (\(suffix)).\(prepared.pathExtension)")
                    suffix += 1
                } while fm.fileExists(atPath: destination.path)
            }
        }
        // Parse archive metadata off the main actor, using the eventual filename for fallback.
        let namedPrepared = staging.appendingPathComponent(destination.lastPathComponent)
        if namedPrepared != prepared { try fm.moveItem(at: prepared, to: namedPrepared) }
        let metadata = ComicMetadataStore.parseDefault(for: namedPrepared)
        phase(.publishing)
        try cancellation.check()
        // POSIX rename atomically replaces a file on the same filesystem. A failed rename
        // leaves the existing comic untouched. Never unlink the destination beforehand.
        guard rename(namedPrepared.path, destination.path) == 0 else {
            throw POSIXError(POSIXErrorCode(rawValue: errno) ?? .EIO)
        }
        // Publication is the commit point: callers must finish metadata even if cancelled now.
        return Imported(url: destination, metadata: metadata)
    }

    private static func copy(_ source: URL, to destination: URL, cancellation: OperationCancellation) throws {
        let input = try FileHandle(forReadingFrom: source)
        defer { try? input.close() }
        guard FileManager.default.createFile(atPath: destination.path, contents: nil) else {
            throw CocoaError(.fileWriteUnknown)
        }
        let output = try FileHandle(forWritingTo: destination)
        defer { try? output.close() }
        while true {
            try cancellation.check()
            guard let data = try input.read(upToCount: 1024 * 1024), !data.isEmpty else { break }
            try output.write(contentsOf: data)
        }
        try output.synchronize()
    }

    private static func validate(_ url: URL, cancellation: OperationCancellation) throws {
        if url.pathExtension == "pdf" {
            guard let document = PDFDocument(url: url), !document.isLocked, document.pageCount > 0,
                  let page = document.page(at: 0), !page.bounds(for: .mediaBox).isEmpty else {
                throw ComicImportError.invalidComic
            }
            return
        }
        let archive = try Archive(url: url, accessMode: .read)
        var usablePage = false
        for entry in archive where entry.type == .file {
            try cancellation.check()
            // Validate CRCs without accumulating the archive (or an entire image) in Data.
            let crc = try archive.extract(entry, skipCRC32: false) { _ in try cancellation.check() }
            guard crc == entry.checksum else { throw ComicImportError.invalidComic }
            if !usablePage, ComicArchiveKt.isImageEntry(name: entry.path) {
                let sample = url.deletingLastPathComponent().appendingPathComponent("page-validation")
                defer { try? FileManager.default.removeItem(at: sample) }
                _ = try archive.extract(entry, to: sample)
                if let image = CGImageSourceCreateWithURL(sample as CFURL, nil) {
                    let options: [CFString: Any] = [kCGImageSourceCreateThumbnailFromImageAlways: true,
                                                  kCGImageSourceThumbnailMaxPixelSize: 64]
                    usablePage = CGImageSourceCreateThumbnailAtIndex(image, 0, options as CFDictionary) != nil
                }
            }
        }
        guard usablePage else { throw ComicImportError.invalidComic }
    }
}
