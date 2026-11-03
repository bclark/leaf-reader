import XCTest
@testable import Leaf

final class BackupManagerTests: XCTestCase {

    func testExportAndParseRoundTrip() throws {
        let comicUrl = URL(fileURLWithPath: "/tmp/Spidey_01.cbz")
        let meta = ComicMetadataRecord(
            series: "Spider-Man",
            volume: "1",
            issueNumber: 1.0,
            year: 2024,
            title: "Spider-Man #1"
        )
        let metadata = ["Spidey_01.cbz": meta]
        let readingList = ReadingListRecord(name: "Favorites", itemFilenames: ["Spidey_01.cbz"])
        let settings = LeafSettings()
        settings.amoled = true
        settings.pageSlideEnabled = true
        settings.libraryColumns = 3

        let jsonString = BackupManager.exportToJson(
            comics: [comicUrl],
            metadata: metadata,
            readingLists: [readingList],
            settings: settings,
            appVersion: "0.3.4"
        )

        let payload = try BackupManager.parseAndValidate(jsonString: jsonString)

        XCTAssertEqual(payload.version, 1)
        XCTAssertEqual(payload.appVersion, "0.3.4")
        XCTAssertEqual(payload.settings?.amoledTheme, true)
        XCTAssertEqual(payload.settings?.pageSlideEnabled, true)
        XCTAssertEqual(payload.settings?.libraryColumns, 3)

        XCTAssertEqual(payload.comics.count, 1)
        let exportedComic = payload.comics.first!
        XCTAssertEqual(exportedComic.title, "Spider-Man #1")
        XCTAssertEqual(exportedComic.series, "Spider-Man")
        XCTAssertEqual(exportedComic.volume, "1")
        XCTAssertEqual(exportedComic.issueNumber, 1.0)
        XCTAssertEqual(exportedComic.format, "cbz")

        XCTAssertEqual(payload.readingLists.count, 1)
        let exportedList = payload.readingLists.first!
        XCTAssertEqual(exportedList.name, "Favorites")
        XCTAssertEqual(exportedList.items.count, 1)
        XCTAssertEqual(exportedList.items.first?.comicTitle, "Spider-Man #1")
    }

    func testRejectMalformedJson() {
        let malformed = "{ not valid json }"
        XCTAssertThrowsError(try BackupManager.parseAndValidate(jsonString: malformed))
    }

    func testRejectUnsupportedVersion() {
        let jsonVersion2 = """
        {
            "version": 2,
            "exportedAt": 1000,
            "appVersion": "0.4.0",
            "comics": []
        }
        """
        XCTAssertThrowsError(try BackupManager.parseAndValidate(jsonString: jsonVersion2)) { error in
            if case BackupError.unsupportedVersion(let v, let expected) = error {
                XCTAssertEqual(v, 2)
                XCTAssertEqual(expected, 1)
            } else {
                XCTFail("Expected unsupportedVersion error")
            }
        }
    }

    func testRejectMissingVersion() {
        let jsonNoVersion = """
        {
            "exportedAt": 1000,
            "comics": []
        }
        """
        XCTAssertThrowsError(try BackupManager.parseAndValidate(jsonString: jsonNoVersion)) { error in
            if case BackupError.missingVersion = error {
                // Success
            } else {
                XCTFail("Expected missingVersion error")
            }
        }
    }

    func testComicMatchingTieredHeuristics() {
        let localComics = [
            URL(fileURLWithPath: "/comics/Spider-Man #01.cbz"),
            URL(fileURLWithPath: "/comics/Batman Vol 1 #05.cbz")
        ]
        let metadata: [String: ComicMetadataRecord] = [
            "Spider-Man #01.cbz": ComicMetadataRecord(series: "Spider-Man", volume: "1", issueNumber: 1.0, title: "Spider-Man #1"),
            "Batman Vol 1 #05.cbz": ComicMetadataRecord(series: "Batman", volume: "1", issueNumber: 5.0, title: "Batman #5")
        ]

        // 1. Exact match on title
        let backup1 = BackupComic(title: "Spider-Man #1")
        let match1 = BackupManager.findMatchingComic(backup: backup1, localComics: localComics, metadata: metadata)
        XCTAssertEqual(match1?.lastPathComponent, "Spider-Man #01.cbz")

        // 2. Match on series + volume + issueNumber
        let backup2 = BackupComic(title: "The Dark Knight", series: "Batman", volume: "1", issueNumber: 5.0)
        let match2 = BackupManager.findMatchingComic(backup: backup2, localComics: localComics, metadata: metadata)
        XCTAssertEqual(match2?.lastPathComponent, "Batman Vol 1 #05.cbz")

        // 3. Match on filename
        let backup3 = BackupComic(title: "Batman Vol 1 #05")
        let match3 = BackupManager.findMatchingComic(backup: backup3, localComics: localComics, metadata: metadata)
        XCTAssertEqual(match3?.lastPathComponent, "Batman Vol 1 #05.cbz")
    }

    func testBackupPreviewCalculation() {
        let localComics = [
            URL(fileURLWithPath: "/comics/Spider-Man #01.cbz")
        ]
        let metadata: [String: ComicMetadataRecord] = [
            "Spider-Man #01.cbz": ComicMetadataRecord(series: "Spider-Man", volume: "1", issueNumber: 1.0, title: "Spider-Man #1")
        ]

        let payload = BackupPayload(
            version: 1,
            exportedAt: 1000,
            appVersion: "0.3.4",
            settings: BackupSettings(),
            comics: [
                BackupComic(title: "Spider-Man #1"),
                BackupComic(title: "Unknown Comic #99")
            ],
            readingLists: [
                BackupReadingList(name: "Favorites", items: [])
            ]
        )

        let preview = BackupManager.createPreview(payload: payload, localComics: localComics, metadata: metadata)

        XCTAssertEqual(preview.version, 1)
        XCTAssertEqual(preview.totalComicsInBackup, 2)
        XCTAssertEqual(preview.matchingComicsCount, 1)
        XCTAssertEqual(preview.readingListsCount, 1)
        XCTAssertTrue(preview.hasSettings)
    }
}
