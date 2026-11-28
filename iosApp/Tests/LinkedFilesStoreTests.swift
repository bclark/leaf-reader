import CryptoKit
import PDFKit
import UIKit
import XCTest
@testable import Leaf

/// Exercises the linked-record transaction, not the document picker's security-scoped URL.
@MainActor
final class LinkedFilesStoreTests: XCTestCase {
    private let fm = FileManager.default

    private func scratch() throws -> URL {
        let url = fm.temporaryDirectory.appendingPathComponent("LeafLinkedFilesTests-" + UUID().uuidString, isDirectory: true)
        try fm.createDirectory(at: url, withIntermediateDirectories: true)
        return url
    }

    private func fixture(in folder: URL, name: String, pages: Int, color: UIColor) throws -> URL {
        try fm.createDirectory(at: folder, withIntermediateDirectories: true)
        let bounds = CGRect(x: 0, y: 0, width: 72, height: 96)
        let data = UIGraphicsPDFRenderer(bounds: bounds).pdfData { context in
            for _ in 0..<pages {
                context.beginPage()
                context.cgContext.setFillColor(color.cgColor)
                context.cgContext.fill(bounds.insetBy(dx: 3, dy: 4))
            }
        }
        let url = folder.appendingPathComponent(name)
        try data.write(to: url)
        return url
    }

    private func makeStore(directory: URL, cache: URL? = nil, coverOverride: Data? = nil) throws -> LinkedFilesStore {
        let generatedCover = UIGraphicsImageRenderer(size: CGSize(width: 16, height: 24)).image { context in
            UIColor.systemPurple.setFill()
            context.fill(CGRect(x: 0, y: 0, width: 16, height: 24))
        }.jpegData(compressionQuality: 0.8)
        let cover = try XCTUnwrap(coverOverride ?? generatedCover)
        return LinkedFilesStore(directory: directory, cacheDirectory: cache, prepare: { source in
            guard let pdf = PDFDocument(url: source), pdf.pageCount > 0 else {
                throw CocoaError(.fileReadCorruptFile)
            }
            // Real security scope is covered by the picker smoke test. A tiny opaque stand-in makes
            // this test exercise persistence and identity without sandbox-dependent picker access.
            return LinkedFilesStore.Prepared(
                bookmark: Data(source.absoluteString.utf8),
                title: source.deletingPathExtension().lastPathComponent,
                pageCount: pdf.pageCount,
                cover: cover,
                fingerprint: SHA256.hash(data: try Data(contentsOf: source)).map { String(format: "%02x", $0) }.joined()
            )
        })
    }

    private func regularFiles(in directory: URL) throws -> [URL] {
        let records = try XCTUnwrap(fm.enumerator(at: directory, includingPropertiesForKeys: [.isRegularFileKey]))
        return try records.compactMap { entry in
            guard let url = entry as? URL,
                  try url.resourceValues(forKeys: [.isRegularFileKey]).isRegularFile == true else { return nil }
            return url
        }
    }

    func testSameNamedDifferentPDFsKeepDistinctIdentityAndProgressAcrossStoreRestart() async throws {
        let root = try scratch()
        defer { try? fm.removeItem(at: root) }
        let name = "Same-" + UUID().uuidString + ".pdf"
        let a = try fixture(in: root.appendingPathComponent("provider-a"), name: name, pages: 4, color: .systemRed)
        let b = try fixture(in: root.appendingPathComponent("provider-b"), name: name, pages: 5, color: .systemBlue)
        let directory = root.appendingPathComponent("records")
        let cache = root.appendingPathComponent("covers")
        XCTAssertNotEqual(try Data(contentsOf: a), try Data(contentsOf: b))

        let store = try makeStore(directory: directory, cache: cache)
        let one = try await store.link(a)
        let two = try await store.link(b)
        defer {
            [a, one.progressURL, two.progressURL].forEach { ReadingProgress.clear($0) }
            ReadingProgress.flushSync()
        }
        XCTAssertEqual(one.title, two.title)
        XCTAssertEqual(one.pageCount, 4)
        XCTAssertEqual(two.pageCount, 5)
        XCTAssertNotEqual(one.id, two.id)
        XCTAssertNotEqual(one.bookmark, two.bookmark)
        XCTAssertNotEqual(one.fingerprint, two.fingerprint)
        XCTAssertNotEqual(one.progressURL.lastPathComponent, two.progressURL.lastPathComponent)
        XCTAssertNotEqual(one.progressURL.lastPathComponent, a.lastPathComponent)
        XCTAssertNotEqual(two.progressURL.lastPathComponent, b.lastPathComponent)

        // The same local filename represents a conventional imported copy in the legacy progress map.
        ReadingProgress.set(a, page: 0, step: -1, total: 4, rtl: false)
        ReadingProgress.set(one.progressURL, page: 1, step: -1, total: 4, rtl: true)
        ReadingProgress.set(two.progressURL, page: 2, step: -1, total: 5, rtl: false)
        ReadingProgress.flushSync()

        let reopened = try makeStore(directory: directory, cache: cache)
        XCTAssertEqual(Set(reopened.comics.map(\.id)), Set([one.id, two.id]))
        let first = try XCTUnwrap(reopened.comics.first { $0.id == one.id })
        let second = try XCTUnwrap(reopened.comics.first { $0.id == two.id })
        XCTAssertEqual(first, one)
        XCTAssertEqual(second, two)
        XCTAssertEqual(first.progressURL, one.progressURL)
        XCTAssertEqual(second.progressURL, two.progressURL)
        XCTAssertEqual(ReadingProgress.get(first.progressURL)?.page, 1)
        XCTAssertEqual(ReadingProgress.get(second.progressURL)?.page, 2)
        XCTAssertEqual(ReadingProgress.get(a)?.page, 0)
    }

    func testLinkWritesOnlyRegistryAndContainedCoverThenRemoveLeavesOriginal() async throws {
        let root = try scratch()
        defer { try? fm.removeItem(at: root) }
        let original = try fixture(in: root.appendingPathComponent("provider"), name: "Original.pdf", pages: 3, color: .systemGreen)
        let bytes = try Data(contentsOf: original)
        let directory = root.appendingPathComponent("records", isDirectory: true)
        let cache = root.appendingPathComponent("covers", isDirectory: true)
        let owned = root.appendingPathComponent("ownedLibrary", isDirectory: true)
        try fm.createDirectory(at: owned, withIntermediateDirectories: true)
        let store = try makeStore(directory: directory, cache: cache)

        let comic = try await store.link(original)
        XCTAssertEqual(try regularFiles(in: directory).map(\.lastPathComponent), ["linked-files.json"])
        let images = try regularFiles(in: cache)
        XCTAssertEqual(images.map { $0.lastPathComponent.lowercased() }, [comic.id.uuidString.lowercased() + ".jpg"])
        let cover = try XCTUnwrap(images.first)
        XCTAssertEqual(cover.deletingLastPathComponent().resolvingSymlinksInPath().path, cache.resolvingSymlinksInPath().path)
        XCTAssertNotEqual(try Data(contentsOf: cover), bytes)
        XCTAssertNotNil(store.cover(for: comic))
        XCTAssertTrue(try fm.contentsOfDirectory(atPath: owned.path).isEmpty)
        XCTAssertTrue(try fm.contentsOfDirectory(atPath: original.deletingLastPathComponent().path).elementsEqual(["Original.pdf"]))
        XCTAssertEqual(try Data(contentsOf: original), bytes)

        try store.remove(comic)
        XCTAssertTrue(store.comics.isEmpty)
        XCTAssertNil(store.cover(for: comic))
        XCTAssertFalse(fm.fileExists(atPath: cover.path))
        XCTAssertTrue(try makeStore(directory: directory, cache: cache).comics.isEmpty)
        XCTAssertEqual(try Data(contentsOf: original), bytes)
    }

    func testRejectedRelinkDoesNotReplaceRecordProgressCoverOrOriginal() async throws {
        let root = try scratch()
        defer { try? fm.removeItem(at: root) }
        let original = try fixture(in: root.appendingPathComponent("original"), name: "Same.pdf", pages: 4, color: .systemOrange)
        let bytes = try Data(contentsOf: original)
        let badFolder = root.appendingPathComponent("replacement")
        try fm.createDirectory(at: badFolder, withIntermediateDirectories: true)
        let invalid = badFolder.appendingPathComponent("Same.pdf")
        let validDifferent = try fixture(in: root.appendingPathComponent("different"), name: "Same.pdf", pages: 4, color: .systemTeal)
        let differentBytes = try Data(contentsOf: validDifferent)
        XCTAssertNotEqual(bytes, differentBytes)
        let invalidBytes = Data("not a PDF".utf8)
        try invalidBytes.write(to: invalid)
        let directory = root.appendingPathComponent("records", isDirectory: true)
        let cache = root.appendingPathComponent("covers", isDirectory: true)
        let store = try makeStore(directory: directory, cache: cache)
        let linked = try await store.link(original)
        defer {
            ReadingProgress.clear(linked.progressURL)
            ReadingProgress.flushSync()
        }
        ReadingProgress.set(linked.progressURL, page: 2, step: -1, total: 4, rtl: false)
        let cover = try XCTUnwrap(regularFiles(in: cache).first)
        let coverBytes = try Data(contentsOf: cover)
        let registry = directory.appendingPathComponent("linked-files.json")
        let recordBytes = try Data(contentsOf: registry)

        do {
            _ = try await store.link(invalid, replacing: linked.id)
            XCTFail("An unreadable replacement must not overwrite a link")
        } catch {
            XCTAssertEqual((error as NSError).domain, NSCocoaErrorDomain)
            XCTAssertEqual((error as NSError).code, CocoaError.Code.fileReadCorruptFile.rawValue)
        }

        do {
            _ = try await store.link(validDifferent, replacing: linked.id)
            XCTFail("A valid same-name, same-page-count comic with different content must not take over the link")
        } catch LinkedFilesStore.LinkError.differentFile {
            // The exact-content check rejects this even though it is a readable PDF with the same page count.
        }

        XCTAssertEqual(store.comics, [linked])
        XCTAssertEqual(try makeStore(directory: directory, cache: cache).comics, [linked])
        XCTAssertEqual(ReadingProgress.get(linked.progressURL)?.page, 2)
        XCTAssertEqual(try Data(contentsOf: cover), coverBytes)
        XCTAssertEqual(try Data(contentsOf: registry), recordBytes)
        XCTAssertEqual(try Data(contentsOf: original), bytes)
        XCTAssertEqual(try Data(contentsOf: invalid), invalidBytes)
        XCTAssertEqual(try Data(contentsOf: validDifferent), differentBytes)
    }

    func testByteIdenticalRelinkPreservesIdentityAndValidResumeWithNewBookmark() async throws {
        let root = try scratch()
        defer { try? fm.removeItem(at: root) }
        let original = try fixture(in: root.appendingPathComponent("before"), name: "Before.pdf", pages: 4, color: .systemRed)
        let directory = root.appendingPathComponent("records", isDirectory: true)
        let cache = root.appendingPathComponent("covers", isDirectory: true)
        let store = try makeStore(directory: directory, cache: cache)
        let initial = try await store.link(original)
        defer {
            ReadingProgress.clear(initial.progressURL)
            ReadingProgress.flushSync()
        }
        ReadingProgress.set(initial.progressURL, page: 1, step: -1, total: 4, rtl: true)
        let newFolder = root.appendingPathComponent("after", isDirectory: true)
        try fm.createDirectory(at: newFolder, withIntermediateDirectories: true)
        let relocated = newFolder.appendingPathComponent("Renamed.pdf")
        let originalBytes = try Data(contentsOf: original)
        try fm.moveItem(at: original, to: relocated)

        let updated = try await store.link(relocated, replacing: initial.id)
        XCTAssertEqual(updated.id, initial.id)
        XCTAssertEqual(updated.fingerprint, initial.fingerprint)
        XCTAssertEqual(updated.progressURL, initial.progressURL)
        XCTAssertNotEqual(updated.bookmark, initial.bookmark)
        XCTAssertEqual(updated.title, "Renamed")
        XCTAssertEqual(updated.pageCount, 4)
        let restarted = try makeStore(directory: directory, cache: cache)
        XCTAssertEqual(restarted.comics, [updated])
        XCTAssertEqual(ReadingProgress.get(updated.progressURL)?.page, 1)
        XCTAssertEqual(ReadingProgress.get(updated.progressURL)?.rtl, true)
        XCTAssertEqual(try Data(contentsOf: relocated), originalBytes)
    }
    func testOversizedThumbnailIsDiscardedWithoutLosingTheLink() async throws {
        let root = try scratch()
        defer { try? fm.removeItem(at: root) }
        let original = try fixture(in: root.appendingPathComponent("provider"), name: "Original.pdf", pages: 2, color: .systemBlue)
        let bytes = try Data(contentsOf: original)
        let directory = root.appendingPathComponent("records", isDirectory: true)
        let cache = root.appendingPathComponent("covers", isDirectory: true)
        try fm.createDirectory(at: cache, withIntermediateDirectories: true)
        let oversized = Data(repeating: 0x41, count: LinkedFilesStore.maxCoverBytes + 1)
        let store = try makeStore(directory: directory, cache: cache, coverOverride: oversized)

        let comic = try await store.link(original)
        XCTAssertTrue(try regularFiles(in: cache).isEmpty)
        XCTAssertNil(store.cover(for: comic))
        XCTAssertEqual(try makeStore(directory: directory, cache: cache).comics, [comic])
        XCTAssertEqual(try Data(contentsOf: original), bytes)
    }


    func testDefaultCoverSurvivesRestartAndSameFileRelinkWithoutNewThumbnail() async throws {
        let root = try scratch()
        defer { try? fm.removeItem(at: root) }
        let original = try fixture(in: root.appendingPathComponent("provider"), name: "Original.pdf", pages: 2, color: .systemBlue)
        let directory = root.appendingPathComponent("records")
        let store = try makeStore(directory: directory)
        let first = try await store.link(original)
        let cover = directory.appendingPathComponent("Covers").appendingPathComponent(first.id.uuidString + ".jpg")
        let coverBytes = try Data(contentsOf: cover)
        let withoutThumbnail = try makeStore(directory: directory,
            coverOverride: Data(repeating: 0x41, count: LinkedFilesStore.maxCoverBytes + 1))
        XCTAssertNotNil(withoutThumbnail.cover(for: first))
        let updated = try await withoutThumbnail.link(original, replacing: first.id)
        XCTAssertEqual(updated.id, first.id)
        XCTAssertEqual(try Data(contentsOf: cover), coverBytes)
        XCTAssertNotNil(try makeStore(directory: directory).cover(for: updated))
        try withoutThumbnail.remove(updated)
        XCTAssertFalse(fm.fileExists(atPath: cover.path))
        XCTAssertTrue(fm.fileExists(atPath: original.path))
    }

    func testLinkedProgressAndMixedSeriesAndListStayOutOfOwnedAndBackupRoutes() async throws {
        let root = try scratch()
        defer { try? fm.removeItem(at: root) }
        let source = try fixture(in: root.appendingPathComponent("provider"), name: "Issue 2.pdf",
                                 pages: 4, color: .systemBlue)
        let owned = root.appendingPathComponent("owned")
        let name = "Mixed-" + UUID().uuidString
        let copy = try fixture(in: owned, name: name + " #1.pdf", pages: 4, color: .systemRed)
        let store = try makeStore(directory: root.appendingPathComponent("registry"))
        let first = try await store.link(source)
        let library = LibraryStore(directory: owned)
        let list = library.createReadingList(name: name, urls: [copy, first.progressURL])
        defer {
            library.deleteReadingList(id: list.id)
            try? store.remove(first)
            ComicMetadataStore.clear(copy)
            ReadingProgress.clear(copy)
            ReadingProgress.flushSync()
        }
        library.setSeries(urls: [copy], series: name, volume: nil)
        try store.setSeries(urls: [first.progressURL], name: name, volume: nil)
        let linked = try XCTUnwrap(store.comics.first)
        XCTAssertEqual(library.comics.map(\.lastPathComponent), [copy.lastPathComponent])
        XCTAssertEqual(Set(library.comics(inSeries: name, linked: [linked])), Set([library.comics[0], linked.progressURL]))
        let mixed = library.libraryItems(filter: .series, searchQuery: name, linked: [linked])
        guard case .seriesStack(_, _, let issues, _, let count, _, _) = try XCTUnwrap(mixed.first) else {
            return XCTFail("Expected one shared Series tile")
        }
        XCTAssertEqual(count, 2)
        XCTAssertTrue(issues.contains(linked.progressURL))
        let offline = library.libraryItems(filter: .series, searchQuery: name, available: Set(library.comics), linked: [linked])
        guard case .seriesStack(_, _, let offlineIssues, _, _, _, _) = try XCTUnwrap(offline.first) else {
            return XCTFail("Expected the owned issue in Offline")
        }
        XCTAssertEqual(offlineIssues, library.comics)

        let members = ReadingListMember.resolve(list.itemFilenames, local: library.comics, metadata: library.metadata,
                                                books: [], connection: nil, linked: [linked])
        XCTAssertEqual(members.count, 2)
        XCTAssertTrue(members[0].url?.isFileURL == true)
        XCTAssertNil(members[1].url)
        XCTAssertEqual(members[1].linked?.id, linked.id)
        XCTAssertNil(ReadingListStore.localFilename(for: linked.listToken))
        let choices = ReadingListMember.choices(local: library.comics, metadata: library.metadata, books: [],
            connection: nil, excluding: [], query: name, linked: [linked])
        XCTAssertTrue(choices.contains { $0.id == linked.listToken })

        ReadingProgress.set(linked.progressURL, page: 1, step: -1, total: 4, rtl: false)
        ReadingProgress.setOpenedAt(linked.progressURL, timestamp: 100)
        let continued = LibraryContinueItem.ranked(local: [], remote: [], downloadedIDs: [],
                                                    remoteAvailable: false, linked: [linked])
        XCTAssertEqual(continued.first?.linked?.id, linked.id)
        XCTAssertEqual(continued.first?.page, 1)
        XCTAssertNil(continued.first?.url)
        let backup = BackupManager.exportToJson(comics: library.comics, metadata: library.metadata,
                                                 readingLists: library.readingLists, settings: LeafSettings())
        XCTAssertFalse(backup.contains(linked.listToken))
        XCTAssertFalse(backup.contains(linked.bookmark.base64EncodedString()))
        XCTAssertTrue(backup.contains(name))
        XCTAssertTrue(try Data(contentsOf: source).starts(with: Data("%PDF".utf8)))
    }
}
