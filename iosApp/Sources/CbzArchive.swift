import CryptoKit
import Foundation
import PDFKit
import ImageIO
import UIKit
import ZIPFoundation
import LeafShared

/// iOS comic container implementation (CBZ/ZIP and PDF). Entry filtering (isImageEntry) and page
/// ordering (NaturalOrderComparator) come from the shared Kotlin core so iOS and Android agree on
/// what counts as a page and in which order pages are read.
final class CbzArchive {
    private enum Backend {
        case zip(Archive, [Entry])
        case pdf(PDFDocument)
    }

    private let backend: Backend
    private let scopedFile: ScopedComicFile?
    let pageCount: Int

    init(url: URL, readInPlace: Bool = false, reopeningBookmark: Bool = false) throws {
        if readInPlace {
            guard ["cbz", "pdf"].contains(url.pathExtension.lowercased()) else {
                throw CocoaError(.fileReadUnsupportedScheme)
            }
            let file = try ScopedComicFile(url: url, reopeningBookmark: reopeningBookmark)
            (backend, pageCount) = try file.read(Self.open)
            scopedFile = file
        } else {
            (backend, pageCount) = try Self.open(url)
            scopedFile = nil
        }
    }

    private static func open(_ url: URL) throws -> (Backend, Int) {
        if url.pathExtension.lowercased() == "pdf" {
            guard let doc = PDFDocument(url: url) else {
                throw CocoaError(.fileReadCorruptFile)
            }
            return (.pdf(doc), doc.pageCount)
        } else {
            let archive = try Archive(url: url, accessMode: .read)
            let entries = archive
                .filter { $0.type == .file && ComicArchiveKt.isImageEntry(name: $0.path) }
                .sorted { NaturalOrderComparator.shared.compare(a: $0.path, b: $1.path) < 0 }
            return (.zip(archive, entries), entries.count)
        }
    }

    func pageName(_ index: Int) -> String {
        switch backend {
        case .zip(_, let entries): return entries[index].path
        case .pdf: return "page_\(index + 1).jpg"
        }
    }

    func readPage(_ index: Int) throws -> Data {
        guard let scopedFile else { return try readPageData(index) }
        return try scopedFile.read { _ in try self.readPageData(index) }
    }

    private func readPageData(_ index: Int) throws -> Data {
        switch backend {
        case .zip(let archive, let entries):
            var data = Data()
            _ = try archive.extract(entries[index], skipCRC32: true) { data.append($0) }
            return data
        case .pdf(let doc):
            guard let page = doc.page(at: index) else {
                throw CocoaError(.fileReadCorruptFile)
            }
            let maxDim = CGFloat(PageConstants.shared.maxPageDimension)
            let img = page.thumbnail(of: CGSize(width: maxDim, height: maxDim), for: .mediaBox)
            guard let data = img.jpegData(compressionQuality: 0.92) else {
                throw CocoaError(.fileReadCorruptFile)
            }
            return data
        }
    }

    /// Relinking requires exact file bytes, not just filename, page count, or cover.
    func linkedFingerprint() throws -> String {
        guard let scopedFile else { throw CocoaError(.fileReadNoPermission) }
        return try scopedFile.read { url in
            let handle = try FileHandle(forReadingFrom: url)
            defer { try? handle.close() }
            var hash = SHA256()
            while let chunk = try handle.read(upToCount: 1024 * 1024), !chunk.isEmpty {
                hash.update(data: chunk)
            }
            return hash.finalize().map { String(format: "%02x", $0) }.joined()
        }
    }

    /// The shelf stores a small first-page JPEG, not a rendered page or archive copy.
    func linkedCover(maxBytes: Int) throws -> Data? {
        guard pageCount > 0, let scopedFile else { return nil }
        return try scopedFile.read { _ in
            let image: UIImage?
            switch backend {
            case .pdf(let doc):
                guard let page = doc.page(at: 0) else { return nil }
                image = page.thumbnail(of: CGSize(width: 720, height: 720), for: .mediaBox)
            case .zip(_, let entries):
                // A corrupt or very large first image does not prevent linking the comic.
                guard entries[0].uncompressedSize <= 24 * 1024 * 1024 else { return nil }
                let data = try readPageData(0)
                guard let source = CGImageSourceCreateWithData(data as CFData,
                    [kCGImageSourceShouldCache: false] as CFDictionary),
                    let thumb = CGImageSourceCreateThumbnailAtIndex(source, 0, [
                        kCGImageSourceCreateThumbnailFromImageAlways: true,
                        kCGImageSourceCreateThumbnailWithTransform: true,
                        kCGImageSourceThumbnailMaxPixelSize: 720,
                    ] as CFDictionary) else { return nil }
                image = UIImage(cgImage: thumb)
            }
            guard let image else { return nil }
            // Keep detail first, while bounding the bytes even for noisy scanned art.
            for quality: CGFloat in [0.86, 0.74, 0.62, 0.5, 0.38] {
                if let jpeg = image.jpegData(compressionQuality: quality), jpeg.count <= maxBytes { return jpeg }
            }
            return nil
        }
    }

    func linkedComicInfo() throws -> String? {
        guard let scopedFile else { return nil }
        return try scopedFile.read { _ in readComicInfoXml(maxBytes: 256 * 1024) }
    }

    /// Reads optional ComicInfo.xml metadata file from the root/subfolders of the archive if present.
    func readComicInfoXml(maxBytes: Int = .max) -> String? {
        switch backend {
        case .zip(let archive, _):
            guard let entry = archive.first(where: {
                let clean = $0.path.split(separator: "/").last.map(String.init) ?? $0.path
                return clean.caseInsensitiveCompare("ComicInfo.xml") == .orderedSame
            }), entry.uncompressedSize <= maxBytes else { return nil }
            var data = Data()
            _ = try? archive.extract(entry, skipCRC32: true) { data.append($0) }
            return String(data: data, encoding: .utf8)
        case .pdf:
            return nil
        }
    }
}


/// The archive owns its permission until the final reader or queued page load releases it.
private final class ScopedComicFile {
    let url: URL
    private let scopeStarted: Bool

    init(url: URL, reopeningBookmark: Bool) throws {
        let started = url.startAccessingSecurityScopedResource()
        // On iOS the resolved bookmark can still be readable when this call returns
        // false. Only the OS-coordinated read determines whether access is available.
        guard started || reopeningBookmark else { throw CocoaError(.fileReadNoPermission) }
        self.url = url
        scopeStarted = started
    }

    deinit { if scopeStarted { url.stopAccessingSecurityScopedResource() } }

    func read<T>(_ operation: (URL) throws -> T) throws -> T {
        var coordinationError: NSError?
        var result: Result<T, Error>?
        NSFileCoordinator().coordinate(readingItemAt: url, options: [], error: &coordinationError) { coordinatedURL in
            result = Result { try operation(coordinatedURL) }
        }
        if let coordinationError { throw coordinationError }
        guard let result else { throw CocoaError(.fileReadUnknown) }
        return try result.get()
    }
}
