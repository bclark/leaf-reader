import Foundation
import Testing
@testable import Leaf

@Suite(.serialized) @MainActor
struct LibraryContinueTests {
    private func book(_ id: String, played: Bool? = false, ticks: Int64? = 10_000,
                      percentage: Double? = 25, date: String? = nil) -> JellyfinBook {
        JellyfinBook(id: id, name: id, isFolder: false, container: "cbz", path: nil,
            imageTags: nil, dateModified: nil, userData: .init(Played: played,
                PlaybackPositionTicks: ticks, PlayedPercentage: percentage, LastPlayedDate: date))
    }
    private func rank(_ books: [JellyfinBook], available: Bool = true,
                      downloaded: Set<String> = []) -> [LibraryContinueItem] {
        LibraryContinueItem.ranked(local: [], remote: books, downloadedIDs: downloaded,
            remoteAvailable: available)
    }

    @Test(arguments: [true, false, nil] as [Bool?])
    func remoteCompletionMustBeExplicitlyUnfinished(_ played: Bool?) {
        #expect(rank([book("item", played: played)]).count == (played == false ? 1 : 0))
    }

    @Test(arguments: [nil, -1, 0, 1, 10_000, Int64.max] as [Int64?])
    func ticksMustExistAndBePositiveWithoutOverflow(_ ticks: Int64?) {
        let items = rank([book("item", ticks: ticks)])
        #expect(items.count == ((ticks ?? 0) > 0 ? 1 : 0))
        if ticks == Int64.max { #expect(items.first?.page == Int(Int32.max)) }
    }

    @Test(arguments: [nil, -1, 0, 50, 100, Double.infinity, Double.nan] as [Double?])
    func explicitUnreadIntentDoesNotDependOnOptionalPercentage(_ percent: Double?) {
        #expect(rank([book("item", played: false, percentage: percent)]).count == 1)
        #expect(rank([book("item", played: true, percentage: percent)]).isEmpty)
        #expect(rank([book("item", played: nil, percentage: percent)]).isEmpty)
    }

    @Test func localExplicitUnreadAtLastPageRemainsEligible() {
        let local = LibraryContinueItem.Local(url: URL(fileURLWithPath: "/fixture/unread.cbz"),
            progress: Progress(page: 4, step: -1, total: 5, completionOverride: false), openedAt: 10)
        #expect(LibraryContinueItem.ranked(local: [local], remote: [], downloadedIDs: [],
            remoteAvailable: false).map(\.id) == ["local:unread.cbz"])
    }

    @Test func offlineUnavailableDownloadedAndUnsupportedStayOut() {
        var unsupported = book("epub"); unsupported.container = "epub"
        var folder = book("folder"); folder = JellyfinBook(id: "folder", name: "folder", isFolder: true,
            container: "cbz", path: nil, imageTags: nil, dateModified: nil, userData: folder.userData)
        #expect(rank([book("live")], available: false).isEmpty)
        #expect(rank([book("downloaded"), unsupported, folder, book("../bad")],
            downloaded: ["downloaded"]).isEmpty)
        #expect(rank([book("live"), book("live")]).count == 1)
    }

    @Test func recencyUsesServerDateAndLocalOpeningWithStableTiesAndNoInventedTotal() {
        let url = URL(fileURLWithPath: "/fixture/local.cbz")
        let local = LibraryContinueItem.Local(url: url, progress: .init(page: 0, step: -1, total: 5), openedAt: 1_000)
        let result = LibraryContinueItem.ranked(local: [local], remote: [
            book("older", date: "1970-01-01T00:10:00Z"), book("newest", date: "1970-01-01T00:20:00.500Z"),
            book("no-date"), book("bad-date", date: "not a date")], downloadedIDs: [], remoteAvailable: true)
        #expect(result.map(\.id) == ["remote:newest", "local:local.cbz", "remote:older", "remote:bad-date", "remote:no-date"])
        #expect(result.first?.page == 1)
        #expect(result.first?.total == nil && result.first?.fraction == nil)
        #expect(result[1].page == 0 && result[1].total == 5)
        #expect(result[1].fraction == 0.2)
        #expect(Array(result.prefix(1)).count == 1 && Array(result.prefix(5)).count == 5)
        #expect(LibraryContinueItem.ranked(local: [local], remote: [book("server")],
            downloadedIDs: [], remoteAvailable: false).map(\.id) == ["local:local.cbz"])
    }

    @Test func localCompletionAndCorruptPositionsAreExcludedWithoutRemoteReplacement() {
        let url = URL(fileURLWithPath: "/fixture/local.cbz")
        let local = [Progress(page: 4, step: -1, total: 5), Progress(page: -1, step: -1, total: 5),
            Progress(page: 0, step: -1, total: 0), Progress(page: 9, step: -1, total: 5)]
            .map { LibraryContinueItem.Local(url: url, progress: $0, openedAt: 10) }
        #expect(LibraryContinueItem.ranked(local: local, remote: [book("same")],
            downloadedIDs: ["same"], remoteAvailable: true).isEmpty)
    }

    @Test func oldCatalogueSnapshotsDecodeAndNewDateSurvivesSanitization() throws {
        let legacy = Data(#"{"Id":"legacy","Name":"Legacy","IsFolder":false,"Container":"cbz","UserData":{"Played":false,"PlaybackPositionTicks":10000}}"#.utf8)
        let old = try JSONDecoder().decode(JellyfinBook.self, from: legacy)
        #expect(old.userData?.LastPlayedDate == nil)
        let date = "2026-11-14T12:01:02Z"
        var current = book("new", date: date); current.path = "/private/server/comic.cbz"
        let saved = try JSONEncoder().encode(current.withoutServerPath)
        #expect(!String(decoding: saved, as: UTF8.self).contains("/private/"))
        #expect(try JSONDecoder().decode(JellyfinBook.self, from: saved).userData?.LastPlayedDate == date)
    }
}
