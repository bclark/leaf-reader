import Testing
import SwiftUI
import UIKit
import PDFKit
@testable import Leaf

@Suite(.serialized) @MainActor
struct LibraryBrowseTests {
    @Test func seriesIsTheColdDefaultAndChoicesKeepApprovedOrder() {
        #expect(LibraryBrowseState.ViewMode.allCases == [.series, .lists, .all, .offline])
        var session = LibraryBrowseState()
        #expect(session.view == .series)
        session.select(.all)
        session.finishLaunchCheck()
        #expect(session.view == .all)
        #expect(LibraryBrowseState().view == .series)
    }

    @Test func recentlyAddedUsesLocalAllWithoutAddingATabOrChangingUnread() {
        var session = LibraryBrowseState()
        session.unreadOnly = true
        session.toggleRecentlyAdded()
        #expect(session.recentlyAdded)
        #expect(session.view == .all)
        #expect(session.unreadOnly)
        session.handleUnreachableLaunch()
        #expect(session.recentlyAdded)
        session.select(.series)
        #expect(!session.recentlyAdded)
        session.toggleRecentlyAdded()
        session.toggleRecentlyAdded()
        #expect(!session.recentlyAdded)
        #expect(session.view == .series)
    }

    @Test func newestFirstSortsKnownDatesBeforeUnknownAndBreaksTiesByFilename() {
        let urls = ["D", "C", "B", "A", "E"].map { URL(fileURLWithPath: "/\($0).pdf") }
        let old = Date(timeIntervalSince1970: 1)
        let recent = Date(timeIntervalSince1970: 2)
        let dates = ["C.pdf": old, "B.pdf": recent, "A.pdf": recent]
        let sorted = LibraryStore.newestFirst(urls) { dates[$0.lastPathComponent] }
        #expect(sorted.map(\.lastPathComponent) == ["A.pdf", "B.pdf", "C.pdf", "D.pdf", "E.pdf"])
    }

    @Test func recentlyAddedUsesManagedCopiesButNotJellyfinDownloadDates() throws {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
        let original = root.appendingPathComponent(UUID().uuidString + ".pdf")
        let recent = root.appendingPathComponent(UUID().uuidString + ".pdf")
        let download = root.appendingPathComponent(UUID().uuidString + ".pdf")
        defer {
            ReadingListStore.unlinkLocalCopy(filename: download.lastPathComponent)
            for url in [original, recent, download] {
                ReadingProgress.clear(url)
                ComicMetadataStore.clear(url)
            }
            try? FileManager.default.removeItem(at: root)
        }
        for (i, url) in [original, recent, download].enumerated() {
            try makePDF(url)
            try FileManager.default.setAttributes(
                [.creationDate: Date(timeIntervalSince1970: 946_684_800 + Double(i))], ofItemAtPath: url.path)
        }
        ReadingProgress.set(original, page: 0, step: -1, total: 2, rtl: false, completed: true)
        let ref = try #require(JellyfinListReference(serverID: "server", userID: "user", itemID: UUID().uuidString))
        ReadingListStore.linkRemote(ref, filename: download.lastPathComponent)
        let library = LibraryStore(directory: root)
        let items = library.libraryItems(filter: .all, searchQuery: "", recentlyAdded: true)
        #expect(items.compactMap { item -> String? in
            if case .singleComic(let url, _) = item { return url.lastPathComponent }
            return nil
        } == [recent.lastPathComponent, original.lastPathComponent])
        let unread = library.libraryItems(filter: .all, searchQuery: "", unreadOnly: true, recentlyAdded: true)
        #expect(unread.count == 1)
    }

    @Test func unreadStateSurvivesEveryViewAndExplicitOfflineSurvivesFailure() {
        var state = LibraryBrowseState()
        state.unreadOnly = true
        for view in LibraryBrowseState.ViewMode.allCases {
            state.select(view)
            #expect(state.view == view)
            #expect(state.unreadOnly)
        }
        state.handleUnreachableLaunch()
        #expect(state.view == .offline)
        state.select(.series)
        state.handleUnreachableLaunch()
        #expect(state.view == .series)
        #expect(state.unreadOnly)
        state.unreadOnly = false
        #expect(state.view == .series)
    }

    @Test func coldNetworkFallbackDoesNotOverrideAnExplicitBrowseChoice() {
        var automatic = LibraryBrowseState()
        automatic.handleUnreachableLaunch()
        #expect(automatic.view == .offline)
        var chosen = LibraryBrowseState()
        chosen.select(.lists)
        chosen.handleUnreachableLaunch()
        #expect(chosen.view == .lists)
        var successfulLaunch = LibraryBrowseState()
        successfulLaunch.finishLaunchCheck()
        successfulLaunch.handleUnreachableLaunch()
        #expect(successfulLaunch.view == .series)
    }

    @Test func unknownProgressRemainsUnknownAndSearchCombinesWithUnread() {
        #expect(LibraryBrowseState.includes(completed: nil, unreadOnly: false))
        #expect(!LibraryBrowseState.includes(completed: nil, unreadOnly: true))
        #expect(!LibraryBrowseState.includes(completed: true, unreadOnly: true))
        #expect(LibraryBrowseState.includes(completed: false, unreadOnly: true))
        #expect(LibraryBrowseState.matches("  hellBOY ", values: ["Other title", "Hellboy"]))
        #expect(!LibraryBrowseState.matches("missing", values: ["Hellboy"]))
        // Continue still considers unknown earlier members; a display filter cannot skip them.
        let ordered = ["unknown", "read", "unread"]
        let completed: (String) -> Bool? = { $0 == "unknown" ? nil : $0 == "read" }
        let visible = ordered.filter { LibraryBrowseState.includes(completed: completed($0), unreadOnly: true) }
        #expect(visible == ["unread"])
        #expect(ReadingListStore.nextMember(in: ordered, completed: completed) == "unknown")
    }

    @Test func unreadSeriesAndListsKeepIdentityOrderAndMembership() throws {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
        let first = root.appendingPathComponent(UUID().uuidString + "-1.pdf")
        let second = root.appendingPathComponent(UUID().uuidString + "-2.pdf")
        try makePDF(first); try makePDF(second)
        let group = "Fixture-" + UUID().uuidString
        for (index, url) in [first, second].enumerated() {
            ComicMetadataStore.set(url, metadata: ComicMetadataRecord(series: group, issueNumber: Float(index + 1)))
        }
        let list = ReadingListStore.createList(name: group, itemFilenames: [first.lastPathComponent, second.lastPathComponent])
        let empty = ReadingListStore.createList(name: group + " empty")
        defer {
            ReadingListStore.deleteList(id: list.id); ReadingListStore.deleteList(id: empty.id)
            for url in [first, second] { ReadingProgress.clear(url); ComicMetadataStore.clear(url); CoverLoader.invalidate(url) }
            try? FileManager.default.removeItem(at: root)
        }
        ReadingProgress.set(first, page: 0, step: -1, total: 1, rtl: false, completed: true)
        let library = LibraryStore(directory: root)
        let original = library.libraryItems(filter: .series, searchQuery: group)
        let filtered = library.libraryItems(filter: .series, searchQuery: group, unreadOnly: true)
        #expect(original.count == 1)
        #expect(filtered.first?.id == original.first?.id)
        if case .seriesStack(_, _, let members, _, _, _, _) = filtered.first {
            #expect(members == [second])
        } else { Issue.record("Unread filtering lost the series identity") }
        let lists = library.libraryItems(filter: .readingLists, searchQuery: group, unreadOnly: true)
        #expect(lists.count == 2)
        if case .readingListItem(_, let previews, _) = lists.first(where: { $0.id == "list-" + list.id }) {
            #expect(previews == [second])
        } else { Issue.record("The unread list lost its accessible card") }
        #expect(ReadingListStore.get(id: list.id)?.itemFilenames == [first.lastPathComponent, second.lastPathComponent])
        #expect(ReadingListStore.get(id: empty.id)?.itemFilenames.isEmpty == true)
        #expect(ReadingProgress.isCompleted(first))
        // An Offline group must not reopen an earlier, ineligible file through Continue.
        ReadingProgress.clear(first)
        let offlineSeries = SeriesDetailView(seriesName: group, volume: nil, library: library,
            onDismiss: {}, unreadOnly: .constant(false), availableCopies: [second])
        #expect(offlineSeries.heroButtonText.contains(second.deletingPathExtension().lastPathComponent))
        #expect(!offlineSeries.heroButtonText.contains(first.deletingPathExtension().lastPathComponent))
    }

    @Test func offlineRequiresReadableBytesAndRechecksReplacement() async throws {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
        let good = root.appendingPathComponent(UUID().uuidString + ".pdf")
        let corrupt = root.appendingPathComponent(UUID().uuidString + ".cbz")
        let missing = root.appendingPathComponent(UUID().uuidString + ".pdf")
        defer {
            for url in [good, corrupt, missing] { CoverLoader.invalidate(url); ReadingProgress.clear(url) }
            try? FileManager.default.removeItem(at: root)
        }
        try makePDF(good)
        try Data("incomplete archive".utf8).write(to: corrupt)
        let copies = try await LibraryBrowseState.readableCopies([good, corrupt, missing])
        #expect(copies == [good])
        let library = LibraryStore(directory: root)
        let offline = library.libraryItems(filter: .all, searchQuery: "", available: copies)
        #expect(offline.count == 1)
        #expect(offline.first?.id == "single-" + good.lastPathComponent)
        try FileManager.default.removeItem(at: good)
        CoverLoader.invalidate(good)
        #expect(try await LibraryBrowseState.readableCopies([good]).isEmpty)
        try makePDF(good)
        CoverLoader.invalidate(good)
        #expect(try await LibraryBrowseState.readableCopies([good]) == [good])
    }

    @Test(arguments: [320.0, 768.0])
    func nativeHeaderRendersInThemesAndAccessibility(width: Double) async throws {
        let scene = try #require(UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }.first)
        for dark in [false, true] {
            for large in [false, true] {
                let root = VStack(spacing: 8) {
                    HStack {
                        Text("Leaf").font(.headline)
                        Spacer()
                        UnreadFilterButton(isOn: .constant(true), onBrand: true)
                    }.padding(.horizontal).background(Leaf.leafPrimary)
                    LibraryViewSelector(selection: .constant(.offline)).padding(.horizontal)
                    UnreadFilterChip(isOn: .constant(true)).padding(.horizontal)
                    Text("Readable local copies").font(.body)
                    Spacer()
                }
                .environment(\.dynamicTypeSize, large ? .accessibility3 : .large)
                .environment(\.colorScheme, dark ? .dark : .light)
                .background(dark ? Leaf.ink : Leaf.paperWhite)
                let host = UIHostingController(rootView: root)
                let window = UIWindow(windowScene: scene)
                window.frame = CGRect(x: 0, y: 0, width: width, height: 600)
                let container = UIViewController()
                window.rootViewController = container; window.makeKeyAndVisible()
                container.addChild(host); container.view.addSubview(host.view); host.didMove(toParent: container)
                host.view.frame = CGRect(x: 0, y: 0, width: width, height: 600)
                try await Task.sleep(for: .milliseconds(80))
                host.view.layoutIfNeeded()
                let image = UIGraphicsImageRenderer(bounds: host.view.bounds).image { _ in
                    host.view.drawHierarchy(in: host.view.bounds, afterScreenUpdates: true)
                }
                #expect(abs(image.size.width - CGFloat(width)) <= 1 / image.scale)
                if #available(iOS 26.0, *) {
                    Attachment.record(image, named: "library-header-\(Int(width))-\(dark ? "dark" : "light")-\(large ? "AX3" : "default").png", as: .png)
                }
                window.isHidden = true
            }
        }
    }

    private func makePDF(_ url: URL) throws {
        let renderer = UIGraphicsPDFRenderer(bounds: CGRect(x: 0, y: 0, width: 120, height: 180))
        try renderer.writePDF(to: url) { context in
            context.beginPage()
            UIColor.systemGreen.setFill()
            context.cgContext.fill(CGRect(x: 0, y: 0, width: 120, height: 180))
        }
    }
}
