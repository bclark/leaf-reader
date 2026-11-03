import Foundation
import PDFKit
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

    init(url: URL) throws {
        if url.pathExtension.lowercased() == "pdf" {
            guard let doc = PDFDocument(url: url) else {
                throw CocoaError(.fileReadCorruptFile)
            }
            backend = .pdf(doc)
        } else {
            let archive = try Archive(url: url, accessMode: .read)
            let entries = archive
                .filter { $0.type == .file && ComicArchiveKt.isImageEntry(name: $0.path) }
                .sorted { NaturalOrderComparator.shared.compare(a: $0.path, b: $1.path) < 0 }
            backend = .zip(archive, entries)
        }
    }

    var pageCount: Int {
        switch backend {
        case .zip(_, let entries): return entries.count
        case .pdf(let doc): return doc.pageCount
        }
    }

    func pageName(_ index: Int) -> String {
        switch backend {
        case .zip(_, let entries): return entries[index].path
        case .pdf: return "page_\(index + 1).jpg"
        }
    }

    func readPage(_ index: Int) throws -> Data {
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

    /// Reads optional ComicInfo.xml metadata file from the root/subfolders of the archive if present.
    func readComicInfoXml() -> String? {
        switch backend {
        case .zip(let archive, _):
            guard let entry = archive.first(where: {
                let clean = $0.path.split(separator: "/").last.map(String.init) ?? $0.path
                return clean.caseInsensitiveCompare("ComicInfo.xml") == .orderedSame
            }) else { return nil }
            var data = Data()
            _ = try? archive.extract(entry, skipCRC32: true) { data.append($0) }
            return String(data: data, encoding: .utf8)
        case .pdf:
            return nil
        }
    }
}
