import XCTest
import Testing
import SwiftUI
import UIKit
@testable import Leaf

/// Unit and on-device UI integration tests for Library Stacking, Metadata Backfill,
/// Series Detail View, Reading Lists, Custom Reordering, and Stack Cards on physical device.
@MainActor
final class LibraryStoreTests: XCTestCase {

    private var tempDir: URL!

    override func setUpWithError() throws {
        try super.setUpWithError()
        tempDir = FileManager.default.temporaryDirectory
            .appendingPathComponent("LeafLibraryTests_\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: tempDir, withIntermediateDirectories: true)
    }

    override func tearDownWithError() throws {
        if let tempDir {
            try? FileManager.default.removeItem(at: tempDir)
        }
        try super.tearDownWithError()
    }

    // MARK: - Helpers

    /// Creates a minimal valid CBZ (ZIP) archive with `pageCount` JPEG images and optional `ComicInfo.xml`.
    private func makeTestCbz(
        named filename: String,
        pageCount: Int = 4,
        comicInfoXml: String? = nil
    ) throws -> URL {
        let dest = tempDir.appendingPathComponent(filename)
        var entries: [(name: String, data: Data)] = []

        let renderer = UIGraphicsImageRenderer(size: CGSize(width: 60, height: 84))
        for i in 1...max(1, pageCount) {
            let img = renderer.image { ctx in
                UIColor(hue: CGFloat(i) / 10.0, saturation: 0.7, brightness: 0.8, alpha: 1.0).setFill()
                ctx.fill(CGRect(x: 0, y: 0, width: 60, height: 84))
            }
            let jpg = img.jpegData(compressionQuality: 0.7) ?? Data()
            entries.append((String(format: "page_%03d.jpg", i), jpg))
        }

        if let xml = comicInfoXml, let xmlData = xml.data(using: .utf8) {
            entries.append(("ComicInfo.xml", xmlData))
        }

        let archiveData = Self.buildStoreZip(entries: entries)
        try archiveData.write(to: dest)
        ReadingProgress.clear(dest)
        ComicMetadataStore.clear(dest)
        return dest
    }

    /// Builds an uncompressed (STORE) ZIP archive in memory.
    private static func buildStoreZip(entries: [(name: String, data: Data)]) -> Data {
        var out = Data()
        var centralDirectory = Data()
        var localOffsets: [UInt32] = []

        for entry in entries {
            localOffsets.append(UInt32(out.count))
            let nameBytes = Array(entry.name.utf8)
            let crc = crc32(entry.data)
            let size = UInt32(entry.data.count)

            // Local file header
            out.appendUInt32LE(0x04034b50)
            out.appendUInt16LE(20) // version needed
            out.appendUInt16LE(0)  // flags
            out.appendUInt16LE(0)  // method 0 = STORE
            out.appendUInt16LE(0)  // mod time
            out.appendUInt16LE(0)  // mod date
            out.appendUInt32LE(crc)
            out.appendUInt32LE(size)
            out.appendUInt32LE(size)
            out.appendUInt16LE(UInt16(nameBytes.count))
            out.appendUInt16LE(0)  // extra len
            out.append(contentsOf: nameBytes)
            out.append(entry.data)

            // Central directory header
            centralDirectory.appendUInt32LE(0x02014b50)
            centralDirectory.appendUInt16LE(20)
            centralDirectory.appendUInt16LE(20)
            centralDirectory.appendUInt16LE(0)
            centralDirectory.appendUInt16LE(0)
            centralDirectory.appendUInt16LE(0)
            centralDirectory.appendUInt16LE(0)
            centralDirectory.appendUInt32LE(crc)
            centralDirectory.appendUInt32LE(size)
            centralDirectory.appendUInt32LE(size)
            centralDirectory.appendUInt16LE(UInt16(nameBytes.count))
            centralDirectory.appendUInt16LE(0)
            centralDirectory.appendUInt16LE(0)
            centralDirectory.appendUInt16LE(0)
            centralDirectory.appendUInt16LE(0)
            centralDirectory.appendUInt32LE(0)
            centralDirectory.appendUInt32LE(localOffsets.last!)
            centralDirectory.append(contentsOf: nameBytes)
        }

        let cdOffset = UInt32(out.count)
        out.append(centralDirectory)
        let cdSize = UInt32(centralDirectory.count)

        // End of central directory
        out.appendUInt32LE(0x06054b50)
        out.appendUInt16LE(0)
        out.appendUInt16LE(0)
        out.appendUInt16LE(UInt16(entries.count))
        out.appendUInt16LE(UInt16(entries.count))
        out.appendUInt32LE(cdSize)
        out.appendUInt32LE(cdOffset)
        out.appendUInt16LE(0)
        return out
    }

    private static func crc32(_ data: Data) -> UInt32 {
        var crc: UInt32 = 0xFFFFFFFF
        for byte in data {
            var c = (crc ^ UInt32(byte)) & 0xFF
            for _ in 0..<8 {
                c = (c & 1) != 0 ? (0xEDB88320 ^ (c >> 1)) : (c >> 1)
            }
            crc = (crc >> 8) ^ c
        }
        return crc ^ 0xFFFFFFFF
    }

    // MARK: - 1. Progress & Completion Logic Tests

    func testUnopenedComicIsNotCompleted() throws {
        let url = try makeTestCbz(named: "Unopened #01.cbz", pageCount: 5)
        XCTAssertNil(ReadingProgress.get(url))
        XCTAssertFalse(ReadingProgress.isCompleted(url), "Unopened comic with nil progress must NOT be marked completed")
    }

    func testStartedAndCompletedProgress() throws {
        let url = try makeTestCbz(named: "Saga #01.cbz", pageCount: 10)
        ReadingProgress.set(url, page: 3, step: -1, total: 10, rtl: false)
        XCTAssertFalse(ReadingProgress.isCompleted(url))

        ReadingProgress.set(url, page: 9, step: -1, total: 10, rtl: false)
        XCTAssertTrue(ReadingProgress.isCompleted(url))
        XCTAssertEqual(ReadingProgress.get(url)?.percent, 100)
        ReadingProgress.clear(url)
    }

    // MARK: - 2. Metadata Parsing & Backfill Tests

    func testFilenameAndComicInfoMetadataExtraction() throws {
        let xml = """
        <ComicInfo>
            <Series>Saga</Series>
            <Number>12</Number>
            <Volume>2</Volume>
            <Year>2013</Year>
            <Title>Chapter Twelve</Title>
        </ComicInfo>
        """
        let urlXml = try makeTestCbz(named: "Saga_12.cbz", pageCount: 4, comicInfoXml: xml)
        let metaXml = ComicMetadataStore.parseDefault(for: urlXml)
        XCTAssertEqual(metaXml.series, "Saga")
        XCTAssertEqual(metaXml.issueNumber, 12)
        XCTAssertEqual(metaXml.volume, "Vol. 2")
        XCTAssertEqual(metaXml.year, 2013)
        XCTAssertEqual(metaXml.title, "Chapter Twelve")

        let urlFilename = try makeTestCbz(named: "Batman (2016) #005.cbz", pageCount: 4)
        let metaFilename = ComicMetadataStore.parseDefault(for: urlFilename)
        XCTAssertEqual(metaFilename.series, "Batman")
        XCTAssertEqual(metaFilename.issueNumber, 5)
        XCTAssertEqual(metaFilename.year, 2016)
    }

    func testBackfillPreservesUserModifiedSeriesOverride() throws {
        let url = try makeTestCbz(named: "Deadpool #01.cbz", pageCount: 4)
        ComicMetadataStore.set(url, metadata: ComicMetadataRecord(series: nil, volume: nil, issueNumber: nil))
        ComicMetadataStore.backfillMetadataIfNeeded(for: [url])
        XCTAssertEqual(ComicMetadataStore.get(url)?.series, "Deadpool")
        XCTAssertEqual(ComicMetadataStore.get(url)?.issueNumber, 1)

        // Now user explicitly detaches from series
        ComicMetadataStore.updateSeries(for: [url], series: nil, volume: nil)
        XCTAssertNil(ComicMetadataStore.get(url)?.series)
        XCTAssertEqual(ComicMetadataStore.get(url)?.userModified, true)

        // Backfill must NOT re-attach a comic that the user explicitly detached
        ComicMetadataStore.backfillMetadataIfNeeded(for: [url])
        XCTAssertNil(ComicMetadataStore.get(url)?.series)
        ComicMetadataStore.clear(url)
    }

    // MARK: - 3. Series Stack Grouping & Top Cover Resolution Tests

    func testAllFilterCollapsesMultiIssueSeriesAndLeavesSingleIssueStandalone() throws {
        let dp1 = try makeTestCbz(named: "Deadpool #01.cbz", pageCount: 6)
        let dp2 = try makeTestCbz(named: "Deadpool #02.cbz", pageCount: 6)
        let dp3 = try makeTestCbz(named: "Deadpool #03.cbz", pageCount: 6)
        let solo = try makeTestCbz(named: "Watchmen #01.cbz", pageCount: 6)

        let store = LibraryStore(directory: tempDir)
        let allItems = store.libraryItems(filter: .all, searchQuery: "")

        XCTAssertEqual(allItems.count, 2, "Expected 1 SeriesStack (Deadpool 1-3) + 1 SingleComic (Watchmen #01)")

        let stackItem = allItems.first {
            if case .seriesStack = $0 { return true }
            return false
        }
        guard case .seriesStack(let name, _, let issues, let topCover, let total, let unread, let fraction) = stackItem else {
            XCTFail("Expected Deadpool series stack in .all filter")
            return
        }
        XCTAssertEqual(name, "Deadpool")
        XCTAssertEqual(total, 3)
        XCTAssertEqual(unread, 3)
        XCTAssertEqual(fraction, 0.0, accuracy: 0.001)
        XCTAssertEqual(issues, [dp1, dp2, dp3])
        XCTAssertEqual(topCover, dp1, "When all issues are unread, topCoverComic must be Issue #1")

        // Complete Issue #1 -> topCoverComic must advance to Issue #2
        store.markComicsRead(urls: [dp1], read: true)
        let updatedItems = store.libraryItems(filter: .all, searchQuery: "")
        guard case .seriesStack(_, _, _, let nextTop, _, let nextUnread, let nextFraction) = updatedItems.first(where: {
            if case .seriesStack = $0 { return true }; return false
        }) else {
            XCTFail("Expected updated series stack")
            return
        }
        XCTAssertEqual(nextTop, dp2, "After completing Issue #1, topCoverComic must advance to Issue #2")
        XCTAssertEqual(nextUnread, 2)
        XCTAssertEqual(nextFraction, 1.0 / 3.0, accuracy: 0.01)

        // Complete all issues -> topCoverComic falls back to Issue #1
        store.markSeriesRead(seriesName: "Deadpool", volume: nil, read: true)
        let completedItems = store.libraryItems(filter: .all, searchQuery: "")
        guard case .seriesStack(_, _, _, let finalTop, _, let finalUnread, let finalFraction) = completedItems.first(where: {
            if case .seriesStack = $0 { return true }; return false
        }) else {
            XCTFail("Expected completed series stack")
            return
        }
        XCTAssertEqual(finalTop, dp1, "When all issues are completed, topCoverComic falls back to Issue #1")
        XCTAssertEqual(finalUnread, 0)
        XCTAssertEqual(finalFraction, 1.0, accuracy: 0.001)

        for u in [dp1, dp2, dp3, solo] {
            ReadingProgress.clear(u)
            ComicMetadataStore.clear(u)
        }
    }

    func testSeriesAndUnreadFiltersAndSearchQuery() throws {
        let dp1 = try makeTestCbz(named: "Deadpool #01.cbz", pageCount: 5)
        let dp2 = try makeTestCbz(named: "Deadpool #02.cbz", pageCount: 5)
        let solo = try makeTestCbz(named: "Batman #01.cbz", pageCount: 5)

        let store = LibraryStore(directory: tempDir)

        // SERIES tab includes even 1-issue series if it has a series name
        let seriesItems = store.libraryItems(filter: .series, searchQuery: "")
        XCTAssertEqual(seriesItems.count, 2)

        // Search query filters by series name
        let searchDeadpool = store.libraryItems(filter: .all, searchQuery: "deadpool")
        XCTAssertEqual(searchDeadpool.count, 1)

        // UNREAD filter returns individual unread comics
        store.markComicsRead(urls: [dp1], read: true)
        let unreadItems = store.libraryItems(filter: .unread, searchQuery: "")
        XCTAssertEqual(unreadItems.count, 2, "dp2 and solo should remain unread")

        for u in [dp1, dp2, solo] {
            ReadingProgress.clear(u)
            ComicMetadataStore.clear(u)
        }
    }

    // MARK: - 4. SeriesDetailView Hero Button State Transitions

    func testSeriesDetailHeroButtonTransitions() throws {
        let issue1 = try makeTestCbz(named: "Invincible #01.cbz", pageCount: 8)
        let issue2 = try makeTestCbz(named: "Invincible #02.cbz", pageCount: 8)
        let store = LibraryStore(directory: tempDir)

        let viewFresh = SeriesDetailView(
            seriesName: "Invincible",
            volume: nil,
            library: store,
            onOpenComic: { _ in },
            onDismiss: {}
        )
        XCTAssertTrue(viewFresh.heroButtonText.hasPrefix("START:"), "Expected START: prefix when fresh, got \(viewFresh.heroButtonText)")
        XCTAssertEqual(viewFresh.shortActionLabel, "START")

        // Partial progress on issue 1
        ReadingProgress.set(issue1, page: 3, step: -1, total: 8, rtl: false)
        store.refresh()
        let viewContinue = SeriesDetailView(
            seriesName: "Invincible",
            volume: nil,
            library: store,
            onOpenComic: { _ in },
            onDismiss: {}
        )
        XCTAssertTrue(viewContinue.heroButtonText.hasPrefix("CONTINUE:"), "Expected CONTINUE: prefix when in progress, got \(viewContinue.heroButtonText)")
        XCTAssertEqual(viewContinue.shortActionLabel, "CONTINUE")

        // Mark all read
        store.markSeriesRead(seriesName: "Invincible", volume: nil, read: true)
        let viewAllRead = SeriesDetailView(
            seriesName: "Invincible",
            volume: nil,
            library: store,
            onOpenComic: { _ in },
            onDismiss: {}
        )
        XCTAssertTrue(viewAllRead.heroButtonText.hasPrefix("READ FROM START:"), "Expected READ FROM START: when all completed, got \(viewAllRead.heroButtonText)")
        XCTAssertEqual(viewAllRead.shortActionLabel, "RESTART")

        for u in [issue1, issue2] {
            ReadingProgress.clear(u)
            ComicMetadataStore.clear(u)
        }
    }

    // MARK: - 5. Reading Lists, Sequential Reordering, Batch Mark Read & Hero CTA Tests

    func testReadingListCreationReorderingAndHeroCTA() throws {
        let c1 = try makeTestCbz(named: "Event Part 1.cbz", pageCount: 6)
        let c2 = try makeTestCbz(named: "Event Part 2.cbz", pageCount: 6)
        let c3 = try makeTestCbz(named: "Event Part 3.cbz", pageCount: 6)

        let store = LibraryStore(directory: tempDir)
        let created = store.createReadingList(name: "Summer Crossover", urls: [c1, c2, c3])
        defer { store.deleteReadingList(id: created.id) }

        // Verify list appears in .readingLists filter
        let listItems = store.libraryItems(filter: .readingLists, searchQuery: "crossover")
        XCTAssertEqual(listItems.count, 1)
        if case .readingListItem(let listRecord, let previews, let count) = listItems[0] {
            XCTAssertEqual(listRecord.name, "Summer Crossover")
            XCTAssertEqual(count, 3)
            XCTAssertEqual(previews, [c1, c2, c3])
        } else {
            XCTFail("Expected .readingListItem")
        }

        // Verify hero button starts as START: EVENT PART 1
        let detailFresh = ReadingListDetailView(
            listId: created.id,
            library: store,
            onOpenComic: { _ in },
            onDismiss: {}
        )
        XCTAssertEqual(detailFresh.heroButtonText, "START: EVENT PART 1")

        // Move Event Part 2 UP (-1) so order becomes [c2, c1, c3]
        store.moveComicInReadingList(listId: created.id, url: c2, direction: -1)
        let reordered = store.readingLists.first { $0.id == created.id }
        XCTAssertEqual(reordered?.itemFilenames, [c2.lastPathComponent, c1.lastPathComponent, c3.lastPathComponent])

        // Hero CTA should now target Event Part 2 (#1 in the list)
        let detailAfterReorder = ReadingListDetailView(
            listId: created.id,
            library: store,
            onOpenComic: { _ in },
            onDismiss: {}
        )
        XCTAssertEqual(detailAfterReorder.heroButtonText, "START: EVENT PART 2")
        XCTAssertEqual(detailAfterReorder.shortActionLabel, "START")

        // Start reading Event Part 2 (page 2 of 6) -> CONTINUE: EVENT PART 2
        ReadingProgress.set(c2, page: 2, step: -1, total: 6, rtl: false)
        store.refresh()
        XCTAssertEqual(detailAfterReorder.heroButtonText, "CONTINUE: EVENT PART 2")
        XCTAssertEqual(detailAfterReorder.shortActionLabel, "CONTINUE")

        // Mark entire reading list read -> READ FROM START: EVENT PART 2
        store.markReadingListRead(listId: created.id, read: true)
        XCTAssertTrue(ReadingProgress.isCompleted(c1))
        XCTAssertTrue(ReadingProgress.isCompleted(c2))
        XCTAssertTrue(ReadingProgress.isCompleted(c3))
        XCTAssertEqual(detailAfterReorder.heroButtonText, "READ FROM START: EVENT PART 2")
        XCTAssertEqual(detailAfterReorder.shortActionLabel, "RESTART")

        // Mark entire reading list unread -> back to START: EVENT PART 2
        store.markReadingListRead(listId: created.id, read: false)
        XCTAssertFalse(ReadingProgress.isCompleted(c1))
        XCTAssertEqual(detailAfterReorder.heroButtonText, "START: EVENT PART 2")
        XCTAssertEqual(detailAfterReorder.shortActionLabel, "START")

        for u in [c1, c2, c3] {
            ReadingProgress.clear(u)
            ComicMetadataStore.clear(u)
        }
    }

    // MARK: - 6. Live Device UI Rendering Verification

    func testComicStackCardReadingListCardAndDetailViewsRenderOnDevice() throws {
        let dp1 = try makeTestCbz(named: "Deadpool #01.cbz", pageCount: 6)
        let dp2 = try makeTestCbz(named: "Deadpool #02.cbz", pageCount: 6)
        let dp3 = try makeTestCbz(named: "Deadpool #03.cbz", pageCount: 6)

        let store = LibraryStore(directory: tempDir)
        let list = store.createReadingList(name: "Marvel Event", urls: [dp1, dp2, dp3])
        defer { store.deleteReadingList(id: list.id) }

        let settings = LeafSettings()

        let stackCard = ComicStackCard(
            seriesName: "Deadpool",
            volume: "1",
            issues: [dp1, dp2, dp3],
            topCoverComic: dp1,
            totalIssues: 3,
            unreadIssues: 2,
            progressFraction: 1.0 / 3.0,
            columns: 2,
            onOpenSeries: {}
        )

        let listCard = ReadingListCard(
            list: list,
            previewUrls: [dp1, dp2, dp3],
            itemCount: 3,
            columns: 2,
            onOpenList: {}
        )

        let seriesDetail = SeriesDetailView(
            seriesName: "Deadpool",
            volume: "1",
            library: store,
            onOpenComic: { _ in },
            onDismiss: {}
        ).environmentObject(settings)

        let listDetail = ReadingListDetailView(
            listId: list.id,
            library: store,
            onOpenComic: { _ in },
            onDismiss: {}
        ).environmentObject(settings)

        let window = UIWindow(frame: CGRect(x: 0, y: 0, width: 390, height: 844))
        let host = UIHostingController(rootView: VStack {
            HStack {
                stackCard
                listCard
            }
            seriesDetail
            listDetail
        })
        window.rootViewController = host
        window.makeKeyAndVisible()

        let exp = expectation(description: "Render stack card, reading list card, and detail views on device")
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.35) {
            exp.fulfill()
        }
        wait(for: [exp], timeout: 3.0)

        XCTAssertNotNil(host.view.window)
        window.isHidden = true

        for u in [dp1, dp2, dp3] {
            ReadingProgress.clear(u)
            ComicMetadataStore.clear(u)
        }
    }
}

private extension Data {
    mutating func appendUInt16LE(_ value: UInt16) {
        var le = value.littleEndian
        Swift.withUnsafeBytes(of: &le) { append(contentsOf: $0) }
    }

    mutating func appendUInt32LE(_ value: UInt32) {
        var le = value.littleEndian
        Swift.withUnsafeBytes(of: &le) { append(contentsOf: $0) }
    }
}

@Suite(.serialized)
@MainActor
struct SeriesNameGroupingTests {
    @Test func groupsAcrossVolumesAndKeepsVolumeMetadata() throws {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
        var urls: [URL] = []
        defer {
            for url in urls { ComicMetadataStore.clear(url); ReadingProgress.clear(url) }
            try? FileManager.default.removeItem(at: root)
        }
        for (volume, issue) in [("10", Float(1)), ("2", Float(2)), ("2", Float(1))] {
            let url = root.appendingPathComponent(UUID().uuidString + ".cbz")
            try Data().write(to: url)
            urls.append(url)
            ComicMetadataStore.set(url, metadata: ComicMetadataRecord(
                series: "Example", volume: volume, issueNumber: issue, userModified: true))
        }
        let store = LibraryStore(directory: root)
        for filter in [LibraryFilter.all, .series] {
            let items = store.libraryItems(filter: filter, searchQuery: "")
            #expect(items.count == 1)
            guard case .seriesStack(_, let volume, let issues, _, let total, _, _) = items.first else {
                Issue.record("Expected a single series stack across volumes")
                return
            }
            #expect(volume == nil)
            #expect(total == 3)
            #expect(issues == [urls[2], urls[1], urls[0]])
        }
        #expect(store.comics(inSeries: "EXAMPLE") == [urls[2], urls[1], urls[0]])
        store.addToSeries(urls: [urls[0]], name: "Other")
        #expect(ComicMetadataStore.get(urls[0])?.volume == "10")
        #expect(store.comics(inSeries: "Example").count == 2)
    }
}
