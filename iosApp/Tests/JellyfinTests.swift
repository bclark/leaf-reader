import Foundation
import SwiftUI
import Network
import Testing
import UIKit
import PDFKit
import ZIPFoundation
import LeafShared
@testable import Leaf

@Suite(.serialized)
@MainActor
struct JellyfinTests {
    enum OnboardingSignInOutcome: CaseIterable, Equatable {
        case refusedHTTP, credentialFailure, cancelled, success, cancelledAfterCommit
    }

    @Test(arguments: OnboardingSignInOutcome.allCases)
    func onboardingCompletesOnlyAfterCommittedAuthentication(_ outcome: OnboardingSignInOutcome) async throws {
        let root = try directory()
        defer { try? FileManager.default.removeItem(at: root) }
        let server = try JellyfinHTTPFixture(payload: Data())
        let base = try await server.start()
        let suite = "leaf.signin-onboarding." + UUID().uuidString
        let defaults = try #require(UserDefaults(suiteName: suite))
        defer { defaults.removePersistentDomain(forName: suite); server.stop() }
        let onboarding = FirstLaunchOnboarding(defaults: defaults, directory: root.appendingPathComponent("Comics"))
        onboarding.showJellyfin()
        let credentials: any SmbCredentialStore = outcome == .credentialFailure
            ? OnboardingFailingCredentials() : JellyfinFixtureCredentials()
        let browser = JellyfinBrowserModel(defaults: defaults, credentials: credentials,
            imports: JellyfinImports(file: root.appendingPathComponent("imports.json")),
            progressStore: JellyfinProgressStore(file: root.appendingPathComponent("progress.json")),
            snapshotDirectory: root.appendingPathComponent("snapshots"))
        var successes = 0
        browser.signIn(url: base.absoluteString, username: "fixture", password: "fixture",
            allowHTTP: outcome != .refusedHTTP, onSuccess: {
                #expect(browser.connection != nil)
                #expect(defaults.data(forKey: "leaf.jellyfin.profile") != nil)
                successes += 1
                onboarding.finish()
                if outcome == .cancelledAfterCommit { browser.cancel() }
            })
        #expect(!onboarding.isComplete)
        if outcome == .cancelled { browser.cancel() }
        // A repeated tap while signing in must not create another connection/presentation outcome.
        browser.signIn(url: base.absoluteString, username: "fixture", password: "fixture", allowHTTP: true,
            onSuccess: { successes += 100 })
        await browser.waitForIdle()
        if outcome == .success || outcome == .cancelledAfterCommit {
            #expect(successes == 1)
            #expect(onboarding.isComplete)
            #expect(browser.connection != nil)
        } else {
            #expect(successes == 0)
            #expect(onboarding.step == .jellyfin)
            #expect(browser.connection == nil)
            #expect(defaults.data(forKey: "leaf.jellyfin.profile") == nil)
            if outcome != .cancelled { #expect(browser.message != nil) }
        }
    }

    @Test(.timeLimit(.minutes(1))) func catalogueColdStartCachesWithoutImportingAndFallsBackOffline() async throws {
        let root = try directory()
        defer { try? FileManager.default.removeItem(at: root) }
        let server = try JellyfinHTTPFixture(payload: Data())
        let base = try await server.start()
        let suite = "leaf.catalogue." + UUID().uuidString
        let defaults = try #require(UserDefaults(suiteName: suite))
        defer { defaults.removePersistentDomain(forName: suite); server.stop() }
        let credentials = JellyfinFixtureCredentials()
        let imports = JellyfinImports(file: root.appendingPathComponent("imports.json"))
        let browser = JellyfinBrowserModel(defaults: defaults, credentials: credentials,
            imports: imports, progressStore: JellyfinProgressStore(file: root.appendingPathComponent("progress.json")))
        browser.signIn(url: base.absoluteString, username: "fixture", password: "fixture", allowHTTP: true)
        await browser.waitForIdle()
        let catalogue = JellyfinCatalogueModel(defaults: defaults, snapshotDirectory: root.appendingPathComponent("snapshots"))
        #expect(!catalogue.isCurrent(for: browser.connection))
        #expect(!catalogue.isCurrent(for: nil))
        await catalogue.start(browser: browser)
        #expect(catalogue.isCurrent(for: browser.connection))
        let currentConnection = try #require(browser.connection)
        let anotherAccount = JellyfinConnection(id: "other-profile", baseURL: currentConnection.baseURL,
            userID: "other-user", serverID: currentConnection.serverID, username: "Other", allowsHTTP: true)
        #expect(!catalogue.isCurrent(for: anotherAccount))
        #expect(catalogue.libraryID == "folder")
        #expect(catalogue.books.count == 2)
        #expect(!catalogue.hasMore)
        #expect(catalogue.message == nil)
        #expect(catalogue.groups(catalogue.books).count == 2)
        #expect(catalogue.books.first?.indexNumber == 1.5)
        await catalogue.loadCollections(browser: browser)
        #expect(catalogue.collections.count == 1)
        let collection = try #require(catalogue.collections.first)
        let members = try await catalogue.collectionBooks(collection, browser: browser)
        #expect(members.map(\.id) == ["unknown"])
        let library = LibraryStore(directory: root.appendingPathComponent("Comics"))
        for large in [false, true] {
            let content = JellyfinMainGrid(catalogue: catalogue, browser: browser, filter: .series,
                query: "", columns: 2, downloadedOnly: false, select: { _ in }, selectGroup: { _ in })
                .environmentObject(library).environmentObject(LeafSettings(defaults: defaults))
                .environment(\.dynamicTypeSize, large ? .accessibility3 : .large)
                .environment(\.colorScheme, .light)
                .frame(width: 320).background(Color.white)
            let renderer = ImageRenderer(content: content)
            let image = try #require(renderer.uiImage)
            #expect(image.size.width == 320)
            if #available(iOS 26.0, *) {
                Attachment.record(image, named: large ? "catalogue-series-large.png" : "catalogue-series.png", as: .png)
            }
        }
        #expect(try await imports.reconcile(localFiles: []).isEmpty)
        #expect(browser.downloaded.isEmpty)
        let snapshot = try await catalogue.snapshots.data(for: "leaf.jellyfin.catalogue.6:server4:user")
        #expect(!String(decoding: snapshot, as: UTF8.self).contains("/private/"))
        #expect(defaults.data(forKey: "leaf.jellyfin.catalogue.6:server4:user") == nil)
        server.stop()
        // Simulate an upgrade from the old preference snapshot before reopening offline.
        defaults.set(snapshot, forKey: "leaf.jellyfin.catalogue.6:server4:user")
        try FileManager.default.removeItem(at: root.appendingPathComponent("snapshots"))
        try await Task.sleep(for: .milliseconds(100))
        let reopened = JellyfinCatalogueModel(defaults: defaults, snapshotDirectory: root.appendingPathComponent("snapshots"))
        await reopened.start(browser: browser)
        #expect(reopened.unavailable)
        #expect(reopened.unreachable)
        #expect(reopened.fromCache)
        #expect(reopened.books.count == 2)
        #expect(defaults.data(forKey: "leaf.jellyfin.catalogue.6:server4:user") == nil)
        #expect(reopened.collections.count == 1)
        #expect(try await reopened.collectionBooks(collection, browser: browser).map(\.id) == ["unknown"])
        #expect(reopened.message == String(localized: "Jellyfin server is unreachable. Local copies are available in Offline."))
        await browser.disconnect()
        #expect(!reopened.isCurrent(for: browser.connection))
        await reopened.start(browser: browser)
        #expect(!reopened.isCurrent(for: currentConnection))
        #expect(reopened.books.isEmpty)
        #expect(reopened.libraryID == nil)
    }

    @Test func independentUnreadProjectionUsesLocalProgressAndKeepsUnknownRemoteState() async throws {
        let root = try directory()
        defer { try? FileManager.default.removeItem(at: root) }
        let server = try JellyfinHTTPFixture(payload: Data())
        let base = try await server.start()
        let suite = UUID().uuidString
        let defaults = try #require(UserDefaults(suiteName: suite))
        defer { defaults.removePersistentDomain(forName: suite); server.stop() }
        let browser = JellyfinBrowserModel(defaults: defaults, credentials: JellyfinFixtureCredentials(),
            imports: JellyfinImports(file: root.appendingPathComponent("imports.json")),
            progressStore: JellyfinProgressStore(file: root.appendingPathComponent("progress.json")))
        browser.signIn(url: base.absoluteString, username: "fixture", password: "fixture", allowHTTP: true)
        await browser.waitForIdle()
        let catalogue = JellyfinCatalogueModel(defaults: defaults, snapshotDirectory: root.appendingPathComponent("snapshots"))
        await catalogue.start(browser: browser)
        let grid = JellyfinMainGrid(catalogue: catalogue, browser: browser, filter: .series,
            query: "", columns: 2, downloadedOnly: false, unreadOnly: true,
            select: { _ in }, selectGroup: { _ in })
        #expect(grid.visible.map(\.id) == ["unknown"])
        #expect(catalogue.books.first { $0.id == "unknown-state" }?.userData?.Played == nil)
        let url = try comic(in: root)
        defer { ReadingProgress.clear(url); CoverLoader.invalidate(url) }
        browser.downloaded["unknown"] = url
        ReadingProgress.set(url, page: 0, step: -1, total: 1, rtl: false, completed: true)
        #expect(grid.visible.isEmpty)
        // Filtering changes presentation only, preserving the complete scoped index.
        #expect(catalogue.books.count == 2)
        #expect(catalogue.groups(catalogue.books).count == 2)
        await browser.disconnect()
    }

    enum IncompleteCollectionPage: CaseIterable {
        case emptyFirst, emptyLater, changingTotal, duplicateFirst, duplicateLater, negativeTotal, overrun
    }

    @Test(arguments: [false, true], IncompleteCollectionPage.allCases)
    func incompleteCollectionPagesPreserveOfflineCache(members: Bool, scenario: IncompleteCollectionPage) async throws {
        let root = try directory()
        defer { try? FileManager.default.removeItem(at: root) }
        let server = try JellyfinHTTPFixture(payload: Data())
        let base = try await server.start()
        let suite = UUID().uuidString
        let defaults = try #require(UserDefaults(suiteName: suite))
        defer { defaults.removePersistentDomain(forName: suite); server.stop() }
        let browser = JellyfinBrowserModel(defaults: defaults, credentials: JellyfinFixtureCredentials(),
            imports: JellyfinImports(file: root.appendingPathComponent("imports.json")),
            progressStore: JellyfinProgressStore(file: root.appendingPathComponent("progress.json")))
        browser.signIn(url: base.absoluteString, username: "fixture", password: "fixture", allowHTTP: true)
        await browser.waitForIdle()
        let snapshots = root.appendingPathComponent("snapshots")
        let model = JellyfinCatalogueModel(defaults: defaults, snapshotDirectory: snapshots)
        await model.start(browser: browser)
        await model.loadCollections(browser: browser)
        let collection = try #require(model.collections.first)
        #expect(try await model.collectionBooks(collection, browser: browser).map(\.id) == ["unknown"])
        func page(_ ids: [String], total: Int) throws -> String {
            let items = ids.map { ["Id": $0, "Name": $0, "Type": members ? "Book" : "BoxSet", "Container": "cbz"] }
            return String(decoding: try JSONSerialization.data(withJSONObject: ["Items": items, "TotalRecordCount": total]), as: UTF8.self)
        }
        let id = members ? "unknown" : "collection"
        let pages: [Int: String]
        switch scenario {
        case .emptyFirst: pages = [0: try page([], total: 1)]
        case .emptyLater: pages = [0: try page([id], total: 2), 1: try page([], total: 2)]
        case .changingTotal: pages = [0: try page([id], total: 2), 1: try page(["second"], total: 3), 2: try page(["third"], total: 3)]
        case .duplicateFirst: pages = [0: try page([id, id], total: 2)]
        case .duplicateLater: pages = [0: try page([id], total: 2), 1: try page([id], total: 2)]
        case .negativeTotal: pages = [0: try page([], total: -1)]
        case .overrun: pages = [0: try page([id], total: 0)]
        }
        server.collectionFixture(pages: pages, members: members)
        if members {
            do {
                _ = try await model.collectionBooks(collection, browser: browser)
                Issue.record("An incomplete member traversal must fail before replacing its offline cache")
            } catch JellyfinError.response { }
        } else {
            await model.loadCollections(browser: browser)
            #expect(model.unavailable)
            #expect(model.message != nil)
            #expect(model.collections.map(\.id) == ["collection"])
        }
        // Reopening offline proves the on-disk snapshot survived, not only published UI state.
        server.dropCatalogueConnections(true)
        let reopened = JellyfinCatalogueModel(defaults: defaults, snapshotDirectory: snapshots)
        await reopened.start(browser: browser)
        #expect(reopened.unreachable)
        #expect(reopened.collections.map(\.id) == ["collection"])
        #expect(try await reopened.collectionBooks(collection, browser: browser).map(\.id) == ["unknown"])
        // A later successful collection retry must permit fresh member requests again.
        server.dropCatalogueConnections(false)
        server.collectionFixture(pages: [0: #"{"Items":[{"Id":"collection","Name":"Fixture collection","Type":"BoxSet"}],"TotalRecordCount":1}"#], members: false)
        server.collectionFixture(pages: [0: #"{"Items":[],"TotalRecordCount":0}"#], members: true)
        await model.loadCollections(browser: browser)
        #expect(!model.unavailable)
        #expect(model.message == nil)
        #expect(try await model.collectionBooks(collection, browser: browser).isEmpty)
        await browser.disconnect()
    }

    @Test(arguments: [false, true])
    func completeCollectionPagesStillReconcileAndAllowUnknownTotals(members: Bool) async throws {
        let root = try directory()
        defer { try? FileManager.default.removeItem(at: root) }
        let server = try JellyfinHTTPFixture(payload: Data())
        let base = try await server.start()
        let suite = UUID().uuidString
        let defaults = try #require(UserDefaults(suiteName: suite))
        defer { defaults.removePersistentDomain(forName: suite); server.stop() }
        let browser = JellyfinBrowserModel(defaults: defaults, credentials: JellyfinFixtureCredentials(),
            imports: JellyfinImports(file: root.appendingPathComponent("imports.json")),
            progressStore: JellyfinProgressStore(file: root.appendingPathComponent("progress.json")))
        browser.signIn(url: base.absoluteString, username: "fixture", password: "fixture", allowHTTP: true)
        await browser.waitForIdle()
        let snapshots = root.appendingPathComponent("snapshots")
        let model = JellyfinCatalogueModel(defaults: defaults, snapshotDirectory: snapshots)
        await model.start(browser: browser)
        await model.loadCollections(browser: browser)
        let collection = try #require(model.collections.first)
        #expect(try await model.collectionBooks(collection, browser: browser).map(\.id) == ["unknown"])
        // A short response without TotalRecordCount remains supported.
        let one = members ? #"{"Items":[{"Id":"unknown","Name":"Inside","Type":"Book","Container":"cbz"}]}"#
            : #"{"Items":[{"Id":"collection","Name":"Fixture collection","Type":"BoxSet"}]}"#
        server.collectionFixture(pages: [0: one], members: members)
        if members { #expect(try await model.collectionBooks(collection, browser: browser).map(\.id) == ["unknown"]) }
        else { await model.loadCollections(browser: browser); #expect(model.collections.map(\.id) == ["collection"]) }
        // Cancellation after a request starts must preserve the previous complete snapshot.
        server.delayCollectionResponses(1)
        server.collectionFixture(pages: [0: #"{"Items":[],"TotalRecordCount":0}"#], members: members)
        let priorRequests = server.collectionRequestCount
        let canceled = Task {
            if members { _ = try? await model.collectionBooks(collection, browser: browser) }
            else { await model.loadCollections(browser: browser) }
        }
        for _ in 0..<100 {
            if server.collectionRequestCount > priorRequests { break }
            try await Task.sleep(for: .milliseconds(10))
        }
        try #require(server.collectionRequestCount > priorRequests)
        canceled.cancel()
        await canceled.value
        server.delayCollectionResponses(0)
        server.dropCatalogueConnections(true)
        let afterCancel = JellyfinCatalogueModel(defaults: defaults, snapshotDirectory: snapshots)
        await afterCancel.start(browser: browser)
        #expect(afterCancel.collections.map(\.id) == ["collection"])
        #expect(try await afterCancel.collectionBooks(collection, browser: browser).map(\.id) == ["unknown"])
        server.dropCatalogueConnections(false)
        server.collectionFixture(pages: [0: #"{"Items":[],"TotalRecordCount":0}"#], members: members)
        if members { #expect(try await model.collectionBooks(collection, browser: browser).isEmpty) }
        else { await model.loadCollections(browser: browser); #expect(model.collections.isEmpty); #expect(!model.unavailable) }
        server.dropCatalogueConnections(true)
        let reopened = JellyfinCatalogueModel(defaults: defaults, snapshotDirectory: snapshots)
        await reopened.start(browser: browser)
        if members { #expect(try await reopened.collectionBooks(collection, browser: browser).isEmpty) }
        else { #expect(reopened.collections.isEmpty) }
        // An old session response cannot repopulate the reset catalogue after disconnect.
        server.delayCollectionResponses(1)
        server.collectionFixture(pages: [0: one], members: members)
        let beforeStale = server.collectionRequestCount
        let stale = Task { () -> Bool in
            do {
                if members { _ = try await model.collectionBooks(collection, browser: browser) }
                else { await model.loadCollections(browser: browser) }
                return false
            } catch is CancellationError { return true }
            catch { Issue.record("Expected stale-session cancellation, got \(error)"); return false }
        }
        for _ in 0..<100 {
            if server.collectionRequestCount > beforeStale { break }
            try await Task.sleep(for: .milliseconds(10))
        }
        try #require(server.collectionRequestCount > beforeStale)
        await browser.disconnect()
        await model.start(browser: browser)
        let memberCanceled = await stale.value
        if members { #expect(memberCanceled) }
        #expect(model.collections.isEmpty)
        #expect(model.books.isEmpty)
    }

    @Test func cataloguePagesPreserveCachedMembersUntilCompleteAndSearchBeyondLoadedItems() async throws {
        let root = try directory()
        defer { try? FileManager.default.removeItem(at: root) }
        let server = try JellyfinHTTPFixture(payload: Data())
        let base = try await server.start()
        let suite = UUID().uuidString
        let defaults = try #require(UserDefaults(suiteName: suite))
        defer { defaults.removePersistentDomain(forName: suite); server.stop() }
        let browser = JellyfinBrowserModel(defaults: defaults, credentials: JellyfinFixtureCredentials(),
            imports: JellyfinImports(file: root.appendingPathComponent("imports.json")),
            progressStore: JellyfinProgressStore(file: root.appendingPathComponent("progress.json")))
        browser.signIn(url: base.absoluteString, username: "fixture", password: "fixture", allowHTTP: true)
        await browser.waitForIdle()
        let ancestry = ["a": #"[{"Id":"run-a","Name":"Hellboy","IsFolder":true},{"Id":"folder","Name":"Comics","IsFolder":true}]"#,
                        "b": #"[{"Id":"run-b","Name":"Hellboy","IsFolder":true},{"Id":"folder","Name":"Comics","IsFolder":true}]"#]
        let first = #"{"Items":[{"Id":"a","Name":"Volume 2","Type":"Book","Container":"cbz","ParentId":"run-a"}],"TotalRecordCount":2}"#
        let second = #"{"Items":[{"Id":"b","Name":"Volume 10","Type":"Book","Container":"cbz","ParentId":"run-b"}],"TotalRecordCount":2}"#
        let search = #"{"Items":[{"Id":"unloaded","Name":"Unloaded result","Type":"Book","Container":"cbz"}],"TotalRecordCount":1}"#
        server.catalogueFixture(pages: [0: first, 1: second], search: [0: search], ancestry: ancestry)
        let model = JellyfinCatalogueModel(defaults: defaults, snapshotDirectory: root.appendingPathComponent("snapshots"))
        await model.start(browser: browser)
        #expect(model.books.map(\.id) == ["a", "b"])
        #expect(model.indexComplete)
        let originalGroups = model.groups(model.books)
        #expect(originalGroups.count == 2)
        #expect(originalGroups.allSatisfy { $0.name == "Hellboy" })
        #expect(model.groups([model.books[0]]).first?.id == originalGroups.first?.id)
        await model.search("Unloaded", browser: browser)
        #expect(model.searchBooks.map(\.id) == ["unloaded"])
        #expect(model.books.map(\.id) == ["a", "b"])
        server.setCatalogueStatus(401)
        await model.refresh(browser: browser)
        #expect(!model.unreachable)
        #expect(model.requiresSignIn)
        #expect(model.books.map(\.id) == ["a", "b"])
        server.setCatalogueStatus(200)
        // A failed later page cannot reconcile the old second member away.
        server.catalogueFixture(pages: [0: first], ancestry: ancestry)
        await model.refresh(browser: browser)
        #expect(!model.indexComplete)
        #expect(model.unavailable)
        #expect(model.books.map(\.id) == ["a", "b"])
        let cached = JellyfinCatalogueModel(defaults: defaults, snapshotDirectory: root.appendingPathComponent("snapshots"))
        await cached.start(browser: browser)
        #expect(cached.books.map(\.id) == ["a", "b"])
        #expect(cached.groups(cached.books).map(\.id) == originalGroups.map(\.id))
        let complete = first.replacingOccurrences(of: "TotalRecordCount\":2", with: "TotalRecordCount\":1")
        server.catalogueFixture(pages: [0: complete], ancestry: ancestry)
        await cached.refresh(browser: browser)
        #expect(cached.indexComplete)
        #expect(cached.books.map(\.id) == ["a"])
        #expect(cached.removedBooks.map(\.id) == ["b"])
        #expect(cached.groups(cached.knownBooks).map(\.id) == originalGroups.map(\.id))
        server.catalogueFixture(pages: [0: complete], search: [0: search], ancestry: ancestry)
        server.delayCatalogueResponses(0.5)
        let priorRequests = server.searchRequestCount
        let pendingRefresh = Task { await cached.refresh(browser: browser) }
        let pendingSearch = Task { await cached.search("Unloaded", browser: browser) }
        for _ in 0..<100 {
            if server.searchRequestCount > priorRequests { break }
            try await Task.sleep(nanoseconds: 10_000_000)
        }
        #expect(server.searchRequestCount > priorRequests)
        await browser.disconnect()
        await cached.start(browser: browser)
        await pendingSearch.value
        await pendingRefresh.value
        #expect(cached.books.isEmpty)
        #expect(cached.searchBooks.isEmpty)
    }


    @Test(.timeLimit(.minutes(2))) func largeCatalogueRetainsAllPagesAndCanReopenOffline() async throws {
        let root = try directory()
        defer { try? FileManager.default.removeItem(at: root) }
        let server = try JellyfinHTTPFixture(payload: Data())
        let base = try await server.start()
        let suite = "leaf.large-catalogue." + UUID().uuidString
        let defaults = try #require(UserDefaults(suiteName: suite))
        defer { defaults.removePersistentDomain(forName: suite); server.stop() }
        let browser = JellyfinBrowserModel(defaults: defaults, credentials: JellyfinFixtureCredentials(),
            imports: JellyfinImports(file: root.appendingPathComponent("imports.json")),
            progressStore: JellyfinProgressStore(file: root.appendingPathComponent("progress.json")))
        browser.signIn(url: base.absoluteString, username: "fixture", password: "fixture", allowHTTP: true)
        await browser.waitForIdle()
        let total = 10001
        var pages: [Int: String] = [:]
        for start in stride(from: 0, to: total, by: 100) {
            let items: [[String: Any]] = (start..<min(start + 100, total)).map { index in
                ["Id": "item-\(index)", "Name": "Issue \(index)", "Container": index % 2 == 0 ? "cbz" : "pdf",
                 "Type": "Book", "SeriesId": "series-\(index / 20)", "SeriesName": "Series \(index / 20)",
                 "IndexNumber": index % 20, "People": [["Id": "creator-\(index / 100)", "Name": "Creator \(index / 100)", "Type": "Writer"]]]
            }
            pages[start] = String(decoding: try JSONSerialization.data(withJSONObject:
                ["Items": items, "TotalRecordCount": total]), as: UTF8.self)
        }
        server.catalogueFixture(pages: pages)
        let catalogue = JellyfinCatalogueModel(defaults: defaults, snapshotDirectory: root.appendingPathComponent("snapshots"))
        let start = Date()
        await catalogue.start(browser: browser)
        let elapsed = Date().timeIntervalSince(start)
        #expect(catalogue.books.count == total)
        #expect(catalogue.indexComplete && !catalogue.hasMore)
        #expect(catalogue.groups(catalogue.books).count == 501)
        let snapshot = try await catalogue.snapshots.data(for: "leaf.jellyfin.catalogue.6:server4:user")
        print("LARGE_CATALOGUE records=\(total) pages=101 elapsedSeconds=\(elapsed) persistedBytes=\(snapshot.count)")
        server.setCatalogueStatus(503)
        let reopened = JellyfinCatalogueModel(defaults: defaults, snapshotDirectory: root.appendingPathComponent("snapshots"))
        await reopened.start(browser: browser)
        #expect(reopened.books.count == total)
        #expect(reopened.unavailable && reopened.fromCache)
        #expect(reopened.groups(reopened.books).count == 501)
        await reopened.search("Issue 10000", browser: browser)
        #expect(reopened.searchBooks.map(\.id) == ["item-10000"])
        #expect(browser.downloaded.isEmpty)
    }

    @Test func remoteListIdentitySurvivesDownloadRemovalAndBackupEncoding() throws {
        let connection = JellyfinConnection(id: UUID().uuidString, baseURL: try #require(URL(string: "https://example.test")),
            userID: UUID().uuidString, serverID: "fixture", username: "fixture", allowsHTTP: false)
        let ref = JellyfinListReference(connection: connection, itemID: "comic")
        let filename = UUID().uuidString + ".cbz"
        let list = ReadingListStore.createList(name: "Fixture list", itemFilenames: [filename, ref.token])
        defer { ReadingListStore.deleteList(id: list.id) }
        ReadingListStore.linkRemote(ref, filename: filename)
        #expect(ReadingListStore.get(id: list.id)?.itemFilenames == [ref.token])
        #expect(ReadingListStore.localFilename(for: ref.token) == filename)
        ReadingListStore.unlinkLocalCopy(filename: filename)
        #expect(ReadingListStore.localFilename(for: ref.token) == nil)
        #expect(ReadingListStore.get(id: list.id)?.itemFilenames == [ref.token])
        // A later unrelated import with the same filename must remain a local member.
        ReadingListStore.addComics(to: list.id, filenames: [filename])
        #expect(ReadingListStore.get(id: list.id)?.itemFilenames == [ref.token, filename])
        ReadingListStore.removeComic(from: list.id, filename: filename)
        ReadingListStore.linkRemote(ref, filename: filename)
        #expect(ReadingListStore.get(id: list.id)?.itemFilenames == [ref.token])
        defer { ReadingListStore.unlinkLocalCopy(filename: filename) }
        #expect(ReadingListStore.nextMember(in: [ref.token, filename], completed: { _ in nil }) == ref.token)
        #expect(ReadingListStore.nextMember(in: [ref.token, filename], completed: { $0 == ref.token }) == filename)
        #expect(JellyfinListReference(token: ref.token) == ref)
        #expect(!ref.belongs(to: nil))
        #expect(JellyfinListReference(token: "jellyfin/server/user/../comic") == nil)
        var item = BackupReadingListItem(comicTitle: "", sortOrder: 2)
        item.serverId = ref.serverID; item.userId = ref.userID; item.itemId = ref.itemID
        let decoded = try JSONDecoder().decode(BackupReadingListItem.self, from: JSONEncoder().encode(item))
        #expect(decoded == item)
        let legacy = try JSONDecoder().decode(BackupReadingListItem.self,
            from: Data(#"{"comicTitle":"Local","sortOrder":0}"#.utf8))
        #expect(legacy.serverId == nil)
    }

    @Test func readableDownloadNamesPreserveDetailsAndStayInsideLibrary() throws {
        func book(_ name: String, path: String? = nil, container: String = "cbz") -> JellyfinBook {
            JellyfinBook(id: "remote-id", name: name, isFolder: false, container: container,
                         path: path, imageTags: nil, dateModified: nil)
        }
        #expect(book("Batman 001 (2024).cbz").importFilename == "Batman 001 (2024).cbz")
        #expect(book("  Saga   #12  ").importFilename == "Saga #12.cbz")
        #expect(book("../Folder\\\\Comic: 01\n.cbz").importFilename == "Folder Comic 01.cbz")
        #expect(book("...", path: "C:\\\\Comics\\\\Original 007.zip", container: "zip").importFilename == "Original 007.cbz")
        #expect(book("", container: "pdf").importFilename == "Comic.pdf")
        let long = book(String(repeating: "漫画", count: 200)).importFilename
        #expect(long.utf8.count <= 204)
        #expect(!long.contains("/"))
    }

    @Test func legacyDownloadDisplayKeepsProgressFilename() {
        let url = URL(fileURLWithPath: "/Comics/Jellyfin-" + UUID().uuidString + ".cbz")
        ComicMetadataStore.set(url, metadata: ComicMetadataRecord(title: "Saga 012"))
        defer { ComicMetadataStore.clear(url) }
        #expect(ComicMetadataStore.displayFilename(for: url) == "Saga 012")
        #expect(url.lastPathComponent.hasPrefix("Jellyfin-"))
    }

    @Test func collisionResolvedPublicationRecoversWithoutRenamingExistingComic() async throws {
        let root = try directory()
        defer { try? FileManager.default.removeItem(at: root) }
        let source = try comic(in: root)
        let checksum = try JellyfinImportLimits.digest(source)
        let pipeline = ComicImportPipeline(directory: root.appendingPathComponent("Comics"))
        let journal = JellyfinImports(file: root.appendingPathComponent("mapping.json"))
        let first = try await pipeline.importFile(from: source, originalName: "Saga 012.cbz",
            expectedBytes: nil, collision: .keepBoth, phase: { _ in })
        let pending = await journal.publicationJournal()
        let second = try await pipeline.importFile(from: source, originalName: "Saga 012.cbz",
            expectedBytes: nil, collision: .keepBoth, beforePublish: { destination in
                try JellyfinImports.stage(.init(profileID: "profile", serverID: "server",
                    userID: "user", itemID: "item", filename: destination.lastPathComponent,
                    checksum: checksum, title: "Saga 012", versionHint: nil, pending: true), at: pending)
            }, phase: { _ in })
        #expect(first.url.lastPathComponent == "Saga 012.cbz")
        #expect(second.url.lastPathComponent == "Saga 012 (2).cbz")
        // Simulate termination immediately after publication, before finishing the journal.
        let recovered = try await journal.reconcile(localFiles: [first.url, second.url])
        #expect(recovered.map(\.filename) == ["Saga 012 (2).cbz"])
        #expect(recovered.first?.pending == false)
        #expect(try JellyfinImportLimits.digest(first.url) == checksum)
        #expect(try await journal.reconcile(localFiles: [first.url, second.url]) == recovered)
        // A journal failure must prevent publication altogether.
        await #expect(throws: JellyfinError.self) {
            try await pipeline.importFile(from: source, originalName: "Blocked.cbz",
                expectedBytes: nil, collision: .keepBoth,
                beforePublish: { _ in throw JellyfinError.storage }, phase: { _ in })
        }
        #expect(!FileManager.default.fileExists(atPath: first.url.deletingLastPathComponent().appendingPathComponent("Blocked.cbz").path))
    }

    @Test func connectionSecurityAndProxyPaths() throws {
        let base = try JellyfinConnection.baseURL(" https://EXAMPLE.com/jellyfin/ ", allowHTTP: false)
        #expect(base.absoluteString == "https://example.com/jellyfin")
        for invalid in ["http://example.com", "https://name:secret@example.com", "https://example.com/?api_key=secret",
                        "https://example.com/#token", "file:///tmp/server", "https://example.com/a/../b"] {
            #expect(throws: JellyfinError.self) { try JellyfinConnection.baseURL(invalid, allowHTTP: true) }
        }
        #expect(throws: JellyfinError.self) { try JellyfinConnection.baseURL("http://192.168.1.2:8096/jellyfin", allowHTTP: false) }
        #expect(try JellyfinConnection.baseURL("http://192.168.1.2:8096/jellyfin", allowHTTP: true).path == "/jellyfin")
        #expect(try JellyfinConnection.baseURL("https://[::1]:8096/base", allowHTTP: false).host != nil)
        let client = JellyfinClient(baseURL: base, token: "fixture-token", deviceID: "fixture-device")
        let request = try client.bookRequest("abc-123")
        #expect(request.url?.path == "/jellyfin/Items/abc-123/Download")
        #expect(request.url?.query == nil)
        #expect(request.value(forHTTPHeaderField: "Authorization")?.contains("Token=\"fixture-token\"") == true)
        #expect(throws: JellyfinError.self) { try client.request("https://other.example.com/jellyfin/Items/abc/Download") }
        #expect(throws: JellyfinError.self) { try client.bookRequest("../escape?token") }
        let malicious = JellyfinClient(baseURL: base, token: "secret\r\nOther: injected", deviceID: "device")
        #expect(throws: JellyfinError.self) { try malicious.bookRequest("abc") }
    }

    @Test(.timeLimit(.minutes(1))) func authenticatedTransportParsesPagesAndRejectsHTTPFailures() async throws {
        let root = try directory()
        defer { try? FileManager.default.removeItem(at: root) }
        let comic = try comic(in: root)
        let server = try JellyfinHTTPFixture(payload: Data(contentsOf: comic))
        let base = try await server.start()
        defer { server.stop() }
        let client = JellyfinClient(baseURL: base, token: "fixture-token", deviceID: "fixture-device")
        let page = try await client.page(userID: "user", parentID: "folder", start: 100)
        #expect(page.items.count == 1)
        #expect(page.total == 101)
        let request = try client.bookRequest("denied")
        do {
            _ = try await client.download(request)
            Issue.record("Download permission denial must be reported")
        } catch JellyfinError.denied { }
        let unknownLength = try await client.download(client.bookRequest("unknown"))
        defer { try? FileManager.default.removeItem(at: unknownLength) }
        #expect(try JellyfinImportLimits.validate(unknownLength, fileExtension: "cbz").count == 64)
        do {
            _ = try await client.download(client.bookRequest("unknown"), limit: 1)
            Issue.record("Observed download bytes must enforce the limit")
        } catch JellyfinError.limit { }
        do {
            _ = try await client.download(client.bookRequest("redirect"))
            Issue.record("Redirect must not be followed")
        } catch JellyfinError.response { }
    }

    @Test func optionalFieldsAndIOSCapabilityGate() throws {
        let json = Data(#"{"Items":[{"Id":"1","Name":"Book","Container":"CBZ"},{"Id":"2","Name":"PDF","Path":"/server/book.pdf"},{"Id":"3","Name":"EPUB","Container":"epub"},{"Id":"4","Name":"RAR","Container":"cbr"},{"Id":"5","Name":"Folder","IsFolder":true}],"TotalRecordCount":5}"#.utf8)
        let page = try JSONDecoder().decode(JellyfinPage.self, from: json)
        #expect(page.items.map(\.supported) == [true, true, false, false, false])
        #expect(page.total == 5)
        #expect(page.items[0].dateModified == nil)
    }

    @Test func statusErrorsAreSanitized() throws {
        let url = try #require(URL(string: "https://example.com"))
        for code in [301, 401, 403, 404, 500] {
            let response = try #require(HTTPURLResponse(url: url, statusCode: code, httpVersion: nil, headerFields: nil))
            #expect(throws: JellyfinError.self) { try JellyfinError.check(response) }
        }
    }

    @Test func zipPDFValidationAndInvalidBodies() throws {
        let root = try directory()
        defer { try? FileManager.default.removeItem(at: root) }
        let cbz = try comic(in: root)
        #expect(try JellyfinImportLimits.validate(cbz, fileExtension: "cbz").count == 64)
        #expect(throws: ComicImportError.self) { try JellyfinImportLimits.validate(cbz, fileExtension: "epub") }
        let html = root.appendingPathComponent("login.cbz")
        try Data("<html>Please sign in</html>".utf8).write(to: html)
        #expect(throws: ComicImportError.self) { try JellyfinImportLimits.validate(html, fileExtension: "cbz") }
        let pdf = root.appendingPathComponent("book.pdf")
        let data = UIGraphicsPDFRenderer(bounds: CGRect(x: 0, y: 0, width: 100, height: 150)).pdfData { context in
            context.beginPage()
            UIColor.red.setFill(); context.cgContext.fill(CGRect(x: 0, y: 0, width: 100, height: 150))
        }
        try data.write(to: pdf)
        #expect(try JellyfinImportLimits.validate(pdf, fileExtension: "pdf").count == 64)
        let oversized = root.appendingPathComponent("huge.pdf")
        let large = UIGraphicsPDFRenderer(bounds: CGRect(x: 0, y: 0, width: 20_000, height: 20_000)).pdfData { $0.beginPage() }
        try large.write(to: oversized)
        #expect(throws: JellyfinError.self) { try JellyfinImportLimits.validate(oversized, fileExtension: "pdf") }
    }

    @Test func forgedExpandedSizeIsRejectedBeforeExtraction() throws {
        let root = try directory()
        defer { try? FileManager.default.removeItem(at: root) }
        let file = try comic(in: root)
        var bytes = try Data(contentsOf: file)
        let directory = try #require(bytes.range(of: Data([0x50, 0x4b, 0x01, 0x02])))
        var size = UInt32(JellyfinImportLimits.entry + 1).littleEndian
        withUnsafeBytes(of: &size) { bytes.replaceSubrange((directory.lowerBound + 24)..<(directory.lowerBound + 28), with: $0) }
        try bytes.write(to: file)
        #expect(throws: JellyfinError.self) { try JellyfinImportLimits.validate(file, fileExtension: "cbz") }
    }

    @Test func journalRecoversOnlyMatchingPublishedBytesAndPreservesOtherFiles() async throws {
        let root = try directory()
        defer { try? FileManager.default.removeItem(at: root) }
        let source = try comic(in: root)
        let checksum = try JellyfinImportLimits.digest(source)
        let file = root.appendingPathComponent("mapping.json")
        let journal = JellyfinImports(file: file)
        let record = JellyfinImports.Record(profileID: "profile", serverID: "server", userID: "user", itemID: "item",
            filename: source.lastPathComponent, checksum: checksum, title: "Same title", versionHint: nil, pending: true)
        try await journal.begin(record)
        let reopened = JellyfinImports(file: file)
        let recovered = try await reopened.reconcile(localFiles: [source])
        #expect(recovered.count == 1)
        #expect(recovered.first?.pending == false)
        // Another account/title must never inherit the first account's mapping.
        let other = JellyfinImports.Record(profileID: "other", serverID: "server", userID: "other", itemID: "item",
            filename: "not-published.cbz", checksum: checksum, title: "Same title", versionHint: nil, pending: true)
        try await reopened.begin(other)
        #expect(try await reopened.reconcile(localFiles: [source]).count == 1)
        #expect(try await reopened.reconcile(localFiles: []).isEmpty)
        #expect(FileManager.default.fileExists(atPath: source.path))
        try await reopened.begin(record)
        try Data("unrelated replacement".utf8).write(to: source)
        #expect(try await reopened.reconcile(localFiles: [source]).isEmpty)
        #expect(try String(contentsOf: source, encoding: .utf8) == "unrelated replacement")
    }

    @Test func validatedDownloadImportsAndReopensOffline() async throws {
        let root = try directory()
        defer { try? FileManager.default.removeItem(at: root) }
        let source = try comic(in: root)
        _ = try JellyfinImportLimits.validate(source, fileExtension: "cbz")
        let directory = root.appendingPathComponent("Comics")
        let store = LibraryStore(directory: directory)
        let first = try await store.importComicAwaitingCompletion(from: source, originalName: UUID().uuidString + ".cbz", collision: .cancel)
        let second = try await store.importComicAwaitingCompletion(from: source, originalName: UUID().uuidString + ".cbz", collision: .cancel)
        defer { store.delete(first); store.delete(second) }
        #expect(first != second)
        let reopened = LibraryStore(directory: directory)
        #expect(reopened.comics.count == 2)
        #expect(try CbzArchive(url: first).pageCount == 1)
    }

    @Test func cancelledValidationDoesNotComplete() async throws {
        let root = try directory()
        defer { try? FileManager.default.removeItem(at: root) }
        let source = try comic(in: root)
        let task = Task {
            try Task.checkCancellation()
            return try JellyfinImportLimits.validate(source, fileExtension: "cbz")
        }
        task.cancel()
        await #expect(throws: CancellationError.self) { try await task.value }
    }


    @Test(.timeLimit(.minutes(1))) func creatorQueriesPageWholeLibraryCacheCreditsAndRejectLateAccounts() async throws {
        let root = try directory()
        defer { try? FileManager.default.removeItem(at: root) }
        let server = try JellyfinHTTPFixture(payload: Data())
        let base = try await server.start()
        let suite = "leaf.creator." + UUID().uuidString
        let defaults = try #require(UserDefaults(suiteName: suite))
        defer { defaults.removePersistentDomain(forName: suite); server.stop() }
        let browser = JellyfinBrowserModel(defaults: defaults, credentials: JellyfinFixtureCredentials(),
            imports: JellyfinImports(file: root.appendingPathComponent("imports.json")),
            progressStore: JellyfinProgressStore(file: root.appendingPathComponent("progress.json")))
        browser.signIn(url: base.absoluteString, username: "fixture", password: "fixture", allowHTTP: true)
        await browser.waitForIdle()
        let book = JellyfinBook(id: "book", name: "Fixture", isFolder: false, container: "cbz",
            path: nil, imageTags: nil, dateModified: nil)
        let (_, detail) = try await browser.details(book)
        #expect(detail.people.first?.id == "person-one")
        #expect(await browser.cachedDetails(book)?.1.fromCache == true)
        #expect(await browser.cachedDetails(book)?.1.people.first?.id == "person-one")
        func page(_ range: Range<Int>, total: Int) throws -> String {
            let items: [[String: Any]] = range.map { index in
                ["Id": "creator-\(index)", "Name": "Issue \(index)", "Type": "Book",
                 "Container": "cbz", "Path": "/private/server/comic.cbz",
                 "People": [["Name": "Same Name", "Id": "person-one", "Type": "Writer"]]]
            }
            return String(decoding: try JSONSerialization.data(withJSONObject:
                ["Items": items, "TotalRecordCount": total]), as: UTF8.self)
        }
        server.catalogueFixture(pages: [0: try page(0..<100, total: 201),
            100: try page(100..<200, total: 201), 200: try page(200..<201, total: 201)])
        let person = JellyfinPerson(name: "Same Name", type: "Writer", role: nil, id: "person-one")
        let results = JellyfinCreatorModel(defaults: defaults, snapshotDirectory: root.appendingPathComponent("snapshots"))
        await results.load(person: person, libraryID: "folder", cachedBooks: [], browser: browser)
        #expect(results.books.count == 201)
        #expect(results.complete)
        #expect(!results.fromCache)
        #expect(results.message == nil)
        #expect(server.creatorQueries.count == 3)
        #expect(server.creatorQueries.allSatisfy { $0["PersonIds"] == "person-one" &&
            $0["ParentId"] == "folder" && $0["UserId"] == "user" && $0["SearchTerm"] == nil })
        #expect(results.books.allSatisfy { $0.path == nil })
        // Same-name people with different IDs must never share an offline relationship.
        server.setCatalogueStatus(503)
        let reopened = JellyfinCreatorModel(defaults: defaults, snapshotDirectory: root.appendingPathComponent("snapshots"))
        await reopened.load(person: person, libraryID: "folder", cachedBooks: [], browser: browser)
        #expect(reopened.books.count == 201 && reopened.fromCache)
        #expect(reopened.message != nil)
        let other = JellyfinPerson(name: "Same Name", type: "Artist", role: nil, id: "person-two")
        await reopened.load(person: other, libraryID: "folder", cachedBooks: results.books, browser: browser)
        #expect(reopened.books.isEmpty)
        let byName = JellyfinPerson(name: "  Same   Name  ", type: nil, role: nil)
        await reopened.load(person: byName, libraryID: "folder", cachedBooks: results.books, browser: browser)
        #expect(reopened.books.count == 201)
        #expect(server.creatorQueries.last?["Person"] == "Same   Name")
        #expect(server.creatorQueries.last?["SearchTerm"] == nil)
        server.setCatalogueStatus(200)
        server.delayCatalogueResponses(0.4)
        let before = server.creatorQueries.count
        let pending = Task { await reopened.load(person: person, libraryID: "folder", cachedBooks: [], browser: browser) }
        for _ in 0..<100 {
            if server.creatorQueries.count > before { break }
            try await Task.sleep(for: .milliseconds(10))
        }
        await browser.disconnect()
        await pending.value
        #expect(reopened.books.isEmpty)
        #expect(await browser.cachedDetails(book) == nil)
    }


    @Test func cinematicHeroAdaptsToPhoneTabletAndAccessibility() throws {
        let suite = "leaf.cinematic." + UUID().uuidString
        let defaults = try #require(UserDefaults(suiteName: suite))
        defer { defaults.removePersistentDomain(forName: suite) }
        let settings = LeafSettings(defaults: defaults)
        let data = Data(#"{"Id":"book","Name":"The Library at the End of the World — Volume One","SeriesName":"The Library at the End of the World","Container":"cbz","IndexNumber":1.5,"ProductionYear":2026}"#.utf8)
        let wire = try JSONDecoder().decode(JellyfinDetails.self, from: data)
        let details = try wire.shared(connection: connection())
        // Synthetic cover, never bundled example artwork or owner metadata.
        let cover = UIGraphicsImageRenderer(size: CGSize(width: 200, height: 300)).image { context in
            UIColor(red: 0.12, green: 0.3, blue: 0.23, alpha: 1).setFill()
            context.fill(CGRect(x: 0, y: 0, width: 200, height: 300))
            UIColor(red: 0.83, green: 0.69, blue: 0.22, alpha: 1).setFill()
            context.fill(CGRect(x: 30, y: 50, width: 140, height: 200))
            "LEAF\nTEST\nCOVER".draw(at: CGPoint(x: 45, y: 90),
                withAttributes: [.font: UIFont.boldSystemFont(ofSize: 24), .foregroundColor: UIColor.black])
        }
        for index in 0..<5 {
            let width: CGFloat = index == 1 ? 768 : 320
            let accessibility = index == 2
            settings.amoled = index == 3
            let content = JellyfinCinematicHero(item: wire.book, details: details, cover: index == 4 ? nil : cover) {
                Text("Writer · Story").leafControlFont(12)
                Text("Alexandra María de la Cruz").underline().leafControlFont(17, weight: 700)
                    .fixedSize(horizontal: false, vertical: true)
                Button {} label: { Label("Read", systemImage: "book").frame(maxWidth: .infinity, minHeight: 44) }
                    .buttonStyle(JellyfinDetailReadStyle())
                Text("Read offline").leafControlFont(13)
                Text("Page \(12) of \(188)").leafControlFont(14)
            }
            .environmentObject(settings)
            .environment(\.dynamicTypeSize, accessibility ? .accessibility3 : .large)
            .environment(\.colorScheme, index == 1 ? .light : .dark)
            .environment(\.locale, Locale(identifier: index == 1 ? "es-MX" : "en"))
            .frame(width: width)
            let renderer = ImageRenderer(content: content)
            let image = try #require(renderer.uiImage)
            #expect(image.size.width == width)
            #expect(image.size.height > 300)
            if #available(iOS 26.0, *) {
                Attachment.record(image, named: "cinematic-\(index).png", as: .png)
            }
        }
    }

    @Test func cachedDetailRoundTripPreservesStructuredCreditsWithoutServerPaths() throws {
        let data = Data(#"{"Id":"book","Name":"Fixture","Path":"/private/a.cbz","Container":"cbz","People":[{"Name":"A","Id":"person-one","Type":"Writer","Role":"Story"},{"Name":"A","Id":"person-two","Type":"Artist"},{"Name":" ","Type":"Writer"}],"IndexNumber":1.5,"Genres":["Mystery"],"MediaSources":[{"Path":"/private/a.cbz","Size":123}],"UserData":{"Played":false}}"#.utf8)
        let wire = try JSONDecoder().decode(JellyfinDetails.self, from: data)
        let encoded = try JSONEncoder().encode(wire)
        #expect(!String(decoding: encoded, as: UTF8.self).contains("/private/"))
        let saved = try JSONDecoder().decode(JellyfinDetails.self, from: encoded)
        let shared = try saved.shared(connection: connection(), fromCache: true)
        #expect(saved.book.indexNumber == 1.5)
        #expect(shared.item.sizeBytes == 123)
        #expect(shared.people.map(\.id) == ["person-one", "person-two"])
        #expect(shared.people.first?.role == "Story")
        #expect(shared.fromCache)
        #expect(saved.book.catalogueEntry.people.count == 2)
    }

    @Test func detailMetadataPreservesAbsenceAndSharedIdentity() throws {
        let json = Data(#"{"Id":"book","Name":"Fixture","Overview":null,"People":[{"Name":"Writer","Type":"Writer"},{"Name":null}],"Genres":null,"SeriesName":"Series","IndexNumber":2,"UserData":{"Played":false,"PlaybackPositionTicks":20000},"MediaSources":[{"Container":"pdf","Size":123}]}"#.utf8)
        let details = try JSONDecoder().decode(JellyfinDetails.self, from: json)
        let connection = try connection()
        let shared = try details.shared(connection: connection)
        #expect(shared.identity.serverId == "server")
        #expect(shared.identity.userId == "user")
        #expect(shared.identity.itemId == "book")
        #expect(shared.people.map(\.name) == ["Writer"])
        #expect(shared.overview == nil)
        #expect(shared.genres.isEmpty)
        #expect(shared.playedPercentage == nil)
        #expect(shared.indexNumber?.intValue == 2)
        #expect(details.book.supported)
        #expect(details.book.fileExtension == "pdf")
        #expect(shared.item.sizeBytes == 123)
        let missing = try JSONDecoder().decode(JellyfinDetails.self, from: Data(#"{"Id":"book","Name":"Fixture"}"#.utf8))
        #expect(missing.userData == nil)
        #expect(missing.people.isEmpty)
        #expect(missing.book.supported == false)
    }

    @Test(.timeLimit(.minutes(1))) func detailTransportUsesAuthenticatedMetadataRouteAndRejectsWrongItem() async throws {
        let server = try JellyfinHTTPFixture(payload: Data())
        let base = try await server.start()
        defer { server.stop() }
        let connection = try connection(baseURL: base)
        let client = JellyfinClient(baseURL: base, token: "fixture-token", deviceID: "fixture-device")
        let result = try await client.details(connection: connection, itemID: "book")
        #expect(result.book.name == "Detail fixture")
        #expect(result.overview == "Synopsis")
        await #expect(throws: JellyfinError.self) {
            try await client.details(connection: connection, itemID: "wrong-item")
        }
        let foreign = try self.connection(baseURL: URL(string: "https://other.example.com/proxy"))
        await #expect(throws: JellyfinError.self) {
            try await client.details(connection: foreign, itemID: "book")
        }
        await #expect(throws: JellyfinError.self) {
            try await client.details(connection: connection, itemID: "../escape")
        }
        let unauthorized = JellyfinClient(baseURL: base, token: "wrong-token", deviceID: "fixture-device")
        do {
            _ = try await unauthorized.details(connection: connection, itemID: "book")
            Issue.record("Detail request must report expired authentication")
        } catch JellyfinError.authentication { }
    }

    @Test func downloadsReconnectByServerAndUserWithoutCrossingAccounts() throws {
        let record = JellyfinImports.Record(profileID: "old-profile", serverID: "server", userID: "user",
            itemID: "book", filename: "Fixture.cbz", checksum: "fixture", title: "Fixture", versionHint: nil, pending: false)
        #expect(record.belongs(to: try connection()))
        let anotherUser = JellyfinConnection(id: "new-profile", baseURL: try connection().baseURL,
            userID: "other", serverID: "server", username: "Other", allowsHTTP: false)
        #expect(!record.belongs(to: anotherUser))
        let anotherServer = JellyfinConnection(id: "new-profile", baseURL: try connection().baseURL,
            userID: "user", serverID: "different", username: "Fixture", allowsHTTP: false)
        #expect(!record.belongs(to: anotherServer))
    }

    @Test(.timeLimit(.minutes(1))) func browserDownloadReconnectAndOfflineReopenPreserveOneCopy() async throws {
        let root = try directory()
        defer { try? FileManager.default.removeItem(at: root) }
        let source = try comic(in: root)
        let server = try JellyfinHTTPFixture(payload: Data(contentsOf: source))
        let base = try await server.start()
        defer { server.stop() }
        let suite = "leaf.jellyfin.fixture." + UUID().uuidString
        let defaults = try #require(UserDefaults(suiteName: suite))
        defer { defaults.removePersistentDomain(forName: suite) }
        let credentials = JellyfinFixtureCredentials()
        let imports = JellyfinImports(file: root.appendingPathComponent("imports.json"))
        let progressFile = root.appendingPathComponent("progress.json")
        let progressStore = JellyfinProgressStore(file: progressFile)
        let model = JellyfinBrowserModel(defaults: defaults, credentials: credentials,
            imports: imports, progressStore: progressStore)
        let library = LibraryStore(directory: root.appendingPathComponent("Comics"))
        defer { for comic in library.comics { library.delete(comic) } }
        model.signIn(url: base.absoluteString, username: "fixture", password: "fixture", allowHTTP: true)
        await model.waitForIdle()
        #expect(model.message == nil)
        #expect(model.items.first?.id == "folder")
        let firstProfile = try #require(model.connection?.id)
        let item = JellyfinBook(id: "unknown", name: UUID().uuidString, isFolder: false,
            container: "cbz", path: nil, imageTags: nil, dateModified: nil)
        var opened: [URL] = []
        model.importBook(item, library: library) { opened.append($0) }
        await model.waitForIdle()
        #expect(model.message == nil)
        let published = try #require(model.downloaded[item.id])
        #expect(opened == [published])
        #expect(library.comics == [published])
        #expect(try CbzArchive(url: published).pageCount == 1)
        ReadingProgress.set(published, page: 1, step: -1, total: 1, rtl: true)
        let connection = try #require(model.connection)
        let ref = JellyfinListReference(connection: connection, itemID: item.id)
        let list = ReadingListStore.createList(name: "Retained fixture", itemFilenames: [ref.token])
        defer { ReadingListStore.deleteList(id: list.id) }
        await library.deletePreservingJellyfinProgress([published], imports: imports, progressStore: progressStore)
        #expect(!FileManager.default.fileExists(atPath: published.path))
        #expect(ReadingListStore.get(id: list.id)?.itemFilenames == [ref.token])
        let retainedRevision = try #require(try await progressStore.pending(for: connection).first?.pendingRevision)
        await model.restoreDownloads(library: library)
        model.importBook(item, library: library)
        await model.waitForIdle()
        #expect(model.downloaded[item.id] == published)
        #expect(ReadingProgress.isCompleted(published))
        #expect(try await progressStore.pending(for: connection).first?.pendingRevision == retainedRevision)
        await model.disconnect()
        #expect(model.connection == nil)
        #expect(try credentials.password(profileID: firstProfile) == nil)
        #expect(FileManager.default.fileExists(atPath: published.path))
        model.signIn(url: base.absoluteString, username: "fixture", password: "fixture", allowHTTP: true)
        await model.waitForIdle()
        #expect(model.connection?.id != firstProfile)
        await model.restoreDownloads(library: library)
        #expect(model.downloaded[item.id] == published)
        model.importBook(item, library: library)
        await model.waitForIdle()
        #expect(library.comics == [published])
        // Recreate all model/store instances to prove durable mapping rather than in-memory reuse.
        let reopened = JellyfinBrowserModel(defaults: defaults, credentials: credentials,
            imports: JellyfinImports(file: root.appendingPathComponent("imports.json")),
            progressStore: JellyfinProgressStore(file: progressFile))
        // Persist an offline edit through the same entry point as reader/mark-read/backup restore.
        ReadingProgress.set(published, page: 1, step: -1, total: 1, rtl: true)
        ReadingProgress.flushSync()
        server.stop()
        await reopened.restoreDownloads(library: library)
        #expect(reopened.message == nil)
        let account = try #require(reopened.connection)
        let pending = try #require(try await progressStore.pending(for: account).first)
        #expect(pending.identity.itemID == item.id)
        #expect(pending.position == .init(pageIndex: 0, pageCount: 1, completed: true))
        #expect(pending.sourceRevision == ReadingProgress.get(published)?.localRevision)
        await reopened.restoreDownloads(library: library)
        #expect(try await progressStore.pending(for: account).first?.pendingRevision == pending.pendingRevision)
        let savedOutbox = try Data(contentsOf: progressFile)
        try Data("corrupt fixture".utf8).write(to: progressFile)
        await reopened.restoreDownloads(library: library)
        #expect(reopened.message != nil)
        #expect(ReadingProgress.get(published)?.localRevision == pending.sourceRevision)
        #expect(try String(contentsOf: progressFile, encoding: .utf8) == "corrupt fixture")
        try savedOutbox.write(to: progressFile, options: .atomic)
        #expect(reopened.downloaded[item.id] == published)
        #expect(try CbzArchive(url: published).pageCount == 1)
        await reopened.disconnect()
    }

    @Test(.timeLimit(.minutes(1))) func browserCancellationAfterReceivingBytesPublishesNothingAndCanRetry() async throws {
        let root = try directory()
        defer { try? FileManager.default.removeItem(at: root) }
        let source = try comic(in: root)
        let server = try JellyfinHTTPFixture(payload: Data(contentsOf: source))
        let base = try await server.start()
        defer { server.stop() }
        let suite = "leaf.jellyfin.fixture." + UUID().uuidString
        let defaults = try #require(UserDefaults(suiteName: suite))
        defer { defaults.removePersistentDomain(forName: suite) }
        let imports = JellyfinImports(file: root.appendingPathComponent("imports.json"))
        let model = JellyfinBrowserModel(defaults: defaults, credentials: JellyfinFixtureCredentials(),
            imports: imports, progressStore: JellyfinProgressStore(file: root.appendingPathComponent("progress.json")))
        let library = LibraryStore(directory: root.appendingPathComponent("Comics"))
        defer { for comic in library.comics { library.delete(comic) } }
        model.signIn(url: base.absoluteString, username: "fixture", password: "fixture", allowHTTP: true)
        await model.waitForIdle()
        #expect(model.message == nil)
        let slow = JellyfinBook(id: "slow", name: UUID().uuidString, isFolder: false,
            container: "cbz", path: nil, imageTags: nil, dateModified: nil)
        model.importBook(slow, library: library)
        // Wait for the actual delegate progress event, bounded independently of network speed.
        for _ in 0..<250 {
            if model.transferred > 0 || !model.busy { break }
            try await Task.sleep(for: .milliseconds(20))
        }
        #expect(model.message == nil)
        #expect(model.busy)
        #expect(model.transferred > 0)
        model.cancel()
        await model.waitForIdle()
        #expect(model.busy == false)
        #expect(model.downloaded.isEmpty)
        #expect(library.comics.isEmpty)
        #expect(try await imports.reconcile(localFiles: library.comics).isEmpty)
        let valid = JellyfinBook(id: "unknown", name: UUID().uuidString, isFolder: false,
            container: "cbz", path: nil, imageTags: nil, dateModified: nil)
        model.importBook(valid, library: library)
        await model.waitForIdle()
        #expect(model.message == nil)
        #expect(model.downloaded[valid.id] != nil)
        #expect(library.comics.count == 1)
        await model.disconnect()
    }

    @Test func authenticatedUserDataUpdatesRoundTripWithoutPlaybackSideEffects() async throws {
        let root = try directory()
        defer { try? FileManager.default.removeItem(at: root) }
        let source = try comic(in: root)
        let server = try JellyfinHTTPFixture(payload: Data(contentsOf: source))
        let base = try await server.start()
        defer { server.stop() }
        let client = JellyfinClient(baseURL: base, token: "fixture-token", deviceID: "test-device")
        let account = try connection(baseURL: base)
        #expect(try await client.fetchProgress(connection: account, itemID: "unknown") == .init(positionTicks: 0, played: false))
        let desired = JellyfinProgressStore.Remote(positionTicks: 20_000, played: true)
        #expect(try await client.updateProgress(connection: account, itemID: "unknown", remote: desired) == desired)
        #expect(try await client.fetchProgress(connection: account, itemID: "unknown") == desired)
        await #expect(throws: (any Error).self) {
            try await client.fetchProgress(connection: account, itemID: "wrong")
        }
        let unauthenticated = JellyfinClient(baseURL: base, deviceID: "test-device")
        await #expect(throws: (any Error).self) {
            try await unauthenticated.fetchProgress(connection: account, itemID: "unknown")
        }
    }

    @Test(.timeLimit(.minutes(1))) func browserSyncPushesReadPullsUnreadAndKeepsOfflineChanges() async throws {
        let root = try directory()
        defer { try? FileManager.default.removeItem(at: root) }
        let source = try comic(in: root)
        let server = try JellyfinHTTPFixture(payload: Data(contentsOf: source))
        let base = try await server.start()
        defer { server.stop() }
        let suite = "leaf.jellyfin.sync." + UUID().uuidString
        let defaults = try #require(UserDefaults(suiteName: suite))
        defer { defaults.removePersistentDomain(forName: suite) }
        let store = JellyfinProgressStore(file: root.appendingPathComponent("progress.json"))
        let model = JellyfinBrowserModel(defaults: defaults, credentials: JellyfinFixtureCredentials(),
            imports: JellyfinImports(file: root.appendingPathComponent("imports.json")), progressStore: store)
        let library = LibraryStore(directory: root.appendingPathComponent("Comics"))
        defer { for comic in library.comics { library.delete(comic) } }
        model.signIn(url: base.absoluteString, username: "fixture", password: "fixture", allowHTTP: true)
        await model.waitForIdle()
        let account = try #require(model.connection)
        let item = JellyfinBook(id: "unknown", name: UUID().uuidString, isFolder: false,
            container: "cbz", path: nil, imageTags: nil, dateModified: nil)
        model.importBook(item, library: library)
        await model.waitForIdle()
        let url = try #require(model.downloaded[item.id])
        model.syncProgress(item, library: library)
        await model.waitForIdle()
        #expect(model.syncedItemID == item.id)
        library.markComicsRead(urls: [url], read: true)
        model.syncProgress(item, library: library)
        await model.waitForIdle()
        #expect(model.syncedItemID == item.id)
        #expect(try await store.pending(for: account).isEmpty)
        let client = try #require(model.client)
        #expect(try await client.fetchProgress(connection: account, itemID: item.id).played == true)
        _ = try await client.updateProgress(connection: account, itemID: item.id,
            remote: .init(positionTicks: 0, played: false))
        model.syncProgress(item, library: library)
        await model.waitForIdle()
        #expect(model.syncedItemID == item.id)
        #expect(!ReadingProgress.isCompleted(url))
        server.stop()
        library.markComicsRead(urls: [url], read: true)
        model.syncProgress(item, library: library)
        await model.waitForIdle()
        #expect(model.syncedItemID == nil)
        #expect(model.message != nil)
        #expect(ReadingProgress.isCompleted(url))
        #expect(try await store.pending(for: account).count == 1)
        #expect(try CbzArchive(url: url).pageCount == 1)
        await model.disconnect()
    }

    private func connection(baseURL: URL? = nil) throws -> JellyfinConnection {
        JellyfinConnection(id: "new-profile",
            baseURL: try baseURL ?? #require(URL(string: "https://example.com/proxy")),
            userID: "user", serverID: "server", username: "Fixture", allowsHTTP: false)
    }

    private func directory() throws -> URL {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: url, withIntermediateDirectories: true)
        return url
    }
    private func comic(in root: URL) throws -> URL {
        let data = UIGraphicsImageRenderer(size: CGSize(width: 40, height: 60)).pngData { context in
            UIColor.blue.setFill(); context.fill(CGRect(x: 0, y: 0, width: 40, height: 60))
        }
        let url = root.appendingPathComponent("fixture.cbz")
        let archive = try Archive(url: url, accessMode: .create)
        try archive.addEntry(with: "page1.png", type: .file, uncompressedSize: Int64(data.count)) { position, count in
            data.subdata(in: Int(position)..<min(data.count, Int(position) + count))
        }
        return url
    }
}


private struct OnboardingFailingCredentials: SmbCredentialStore {
    func password(profileID: String) throws -> String? { nil }
    func save(password: String, profileID: String) throws {
        throw NSError(domain: "LeafOnboardingCredentialFixture", code: 1)
    }
    func delete(profileID: String) throws { }
}

private final class JellyfinFixtureCredentials: SmbCredentialStore, @unchecked Sendable {
    private let lock = NSLock()
    private var values: [String: String] = [:]

    func password(profileID: String) throws -> String? {
        lock.lock(); defer { lock.unlock() }
        return values[profileID]
    }

    func save(password: String, profileID: String) throws {
        lock.lock(); defer { lock.unlock() }
        values[profileID] = password
    }

    func delete(profileID: String) throws {
        lock.lock(); defer { lock.unlock() }
        values.removeValue(forKey: profileID)
    }
}

private final class JellyfinHTTPFixture: @unchecked Sendable {
    private let listener: NWListener
    private let queue = DispatchQueue(label: "leaf.jellyfin-test-server")
    private let payload: Data
    // Accessed only by listener callbacks on the serial queue.
    private var resumedStart = false
    private var connections: [NWConnection] = []
    private final class RemoteState {
        var ticks: Int64 = 0; var played = false; var catalogueStatus = 200
        var pages: [Int: String]?; var searchPages: [Int: String]?
        var collectionPages: [Int: String]?; var memberPages: [Int: String]?
        var collectionDelay: TimeInterval = 0
        var collectionRequests = 0
        var dropCatalogue = false
        var ancestry: [String: String] = [:]
        var delay: TimeInterval = 0
        var searchRequests = 0
        var creatorQueries: [[String: String]] = []
    }
    private let remoteState = RemoteState()
    init(payload: Data, catalogueStatus: Int = 200) throws {
        self.payload = payload
        remoteState.catalogueStatus = catalogueStatus
        let parameters = NWParameters.tcp
        parameters.requiredLocalEndpoint = .hostPort(host: "127.0.0.1", port: .any)
        listener = try NWListener(using: parameters)
    }
    var searchRequestCount: Int { queue.sync { remoteState.searchRequests } }
    var creatorQueries: [[String: String]] { queue.sync { remoteState.creatorQueries } }
    func delayCatalogueResponses(_ seconds: TimeInterval) { queue.sync { remoteState.delay = seconds } }
    func setCatalogueStatus(_ status: Int) { queue.sync { remoteState.catalogueStatus = status } }

    func dropCatalogueConnections(_ drop: Bool) { queue.sync { remoteState.dropCatalogue = drop } }
    var collectionRequestCount: Int { queue.sync { remoteState.collectionRequests } }
    func delayCollectionResponses(_ seconds: TimeInterval) { queue.sync { remoteState.collectionDelay = seconds } }
    func collectionFixture(pages: [Int: String], members: Bool) {
        queue.sync {
            if members { remoteState.memberPages = pages }
            else { remoteState.collectionPages = pages }
        }
    }

    func catalogueFixture(pages: [Int: String], search: [Int: String] = [:], ancestry: [String: String] = [:]) {
        queue.sync { remoteState.pages = pages; remoteState.searchPages = search; remoteState.ancestry = ancestry }
    }

    func start() async throws -> URL {
        try await withCheckedThrowingContinuation { continuation in
            listener.stateUpdateHandler = { [weak self, listener] state in
                guard let self, !self.resumedStart else { return }
                switch state {
                case .ready:
                    self.resumedStart = true
                    if let port = listener.port,
                       let url = URL(string: "http://127.0.0.1:\(port.rawValue)/proxy") {
                        continuation.resume(returning: url)
                    } else { continuation.resume(throwing: JellyfinError.configuration) }
                case .failed(let error): self.resumedStart = true; continuation.resume(throwing: error)
                default: break
                }
            }
            listener.newConnectionHandler = { [weak self] connection in
                guard let self else { connection.cancel(); return }
                self.connections.append(connection)
                connection.start(queue: self.queue)
                Self.receive(connection, accumulated: Data(), payload: self.payload, state: self.remoteState)
            }
            listener.start(queue: queue)
        }
    }
    func stop() {
        queue.async { [self] in
            listener.cancel()
            connections.forEach { $0.cancel() }
            connections.removeAll()
        }
    }
    private static func sendSlowChunk(_ connection: NWConnection, body: Data, remaining: Int) {
        guard remaining > 0 else { return }
        DispatchQueue.global().asyncAfter(deadline: .now() + 0.1) {
            connection.send(content: body, completion: .contentProcessed { error in
                if error == nil { sendSlowChunk(connection, body: body, remaining: remaining - 1) }
            })
        }
    }
    private static func receive(_ connection: NWConnection, accumulated: Data, payload: Data, state: RemoteState) {
        connection.receive(minimumIncompleteLength: 1, maximumLength: 16_384) { data, _, complete, error in
            var request = accumulated
            request.append(data ?? Data())
            guard request.count <= 16_384, error == nil else { connection.cancel(); return }
            let text = String(decoding: request, as: UTF8.self)
            guard text.contains("\r\n\r\n") else {
                if complete { connection.cancel() } else { receive(connection, accumulated: request, payload: payload, state: state) }
                return
            }
            guard let boundary = request.range(of: Data("\r\n\r\n".utf8)) else { connection.cancel(); return }
            let headerText = String(decoding: request[..<boundary.lowerBound], as: UTF8.self)
            let length = headerText.components(separatedBy: "\r\n")
                .first { $0.lowercased().hasPrefix("content-length:") }
                .flatMap { Int($0.split(separator: ":").last?.trimmingCharacters(in: .whitespaces) ?? "") } ?? 0
            guard request.count >= boundary.upperBound + length else {
                if complete { connection.cancel() }
                else { receive(connection, accumulated: request, payload: payload, state: state) }
                return
            }
            let userData = text.hasPrefix("GET /proxy/UserItems/unknown/UserData?userId=user ") ||
                text.hasPrefix("POST /proxy/UserItems/unknown/UserData?userId=user ")
            let wrongUserData = text.hasPrefix("GET /proxy/UserItems/wrong/UserData?userId=user ")
            var validUpdate = true
            if userData && text.hasPrefix("POST ") {
                let body = request.subdata(in: boundary.upperBound..<(boundary.upperBound + length))
                if let fields = try? JSONSerialization.jsonObject(with: body) as? [String: Any],
                   Set(fields.keys) == ["PlaybackPositionTicks", "Played"],
                   let ticks = fields["PlaybackPositionTicks"] as? Int64, let played = fields["Played"] as? Bool {
                    state.ticks = ticks
                    state.played = played
                } else { validUpdate = false }
            }
            let denied = text.contains("/denied/Download")
            let authorized = text.contains("Token=\"fixture-token\"")
            let validQuery = text.contains("StartIndex=100") && text.contains("ParentId=folder")
            let unknown = text.contains("/unknown/Download")
            let redirect = text.contains("/redirect/Download")
            let detail = text.hasPrefix("GET /proxy/Items/book?UserId=user ") ||
                text.hasPrefix("GET /proxy/Items/wrong-item?UserId=user ")
            let login = text.hasPrefix("POST /proxy/Users/AuthenticateByName ")
            let views = text.hasPrefix("GET /proxy/Users/user/Views ")
            let catalogue = text.hasPrefix("GET /proxy/Items?") && text.contains("Recursive=true") && text.contains("IncludeItemTypes=Book") && text.contains("ParentId=folder")
            if catalogue && state.dropCatalogue { connection.cancel(); return }
            let slow = text.hasPrefix("GET /proxy/Items/slow/Download ")
            let collections = text.hasPrefix("GET /proxy/Items?") && text.contains("IncludeItemTypes=BoxSet")
            let members = text.hasPrefix("GET /proxy/Items?") && text.contains("ParentId=collection")
            let requestPath = text.split(separator: " ").dropFirst().first.map(String.init) ?? ""
            let components = URLComponents(string: "http://fixture" + requestPath)
            let start = components?.queryItems?.first { $0.name == "StartIndex" }?.value.flatMap(Int.init) ?? 0
            let search = components?.queryItems?.contains { $0.name == "SearchTerm" } ?? false
            if catalogue && search { state.searchRequests += 1 }
            if collections || members { state.collectionRequests += 1 }
            if catalogue, let query = components?.queryItems,
               query.contains(where: { $0.name == "PersonIds" || $0.name == "Person" }) {
                state.creatorQueries.append(Dictionary(query.map { ($0.name, $0.value ?? "") }, uniquingKeysWith: { _, last in last }))
            }
            let ancestorID = requestPath.components(separatedBy: "/").dropFirst(3).first ?? ""
            let ancestors = requestPath.contains("/Ancestors?")
            let code = catalogue && state.catalogueStatus != 200 ? state.catalogueStatus : !authorized && !login ? 401 :
                (denied ? 403 : (redirect ? 302 : ((validQuery || unknown || detail || login || views || catalogue || collections || members || ancestors || slow || userData || wrongUserData) && validUpdate ? 200 : 400)))
            let body: Data
            if userData || wrongUserData {
                body = (try? JSONSerialization.data(withJSONObject: ["ItemId": wrongUserData ? "other" : "unknown",
                    "PlaybackPositionTicks": state.ticks, "Played": state.played])) ?? Data()
            } else if login {
                body = Data(#"{"AccessToken":"fixture-token","ServerId":"server","User":{"Id":"user","Name":"Fixture"}}"#.utf8)
            } else if views {
                body = Data(#"{"Items":[{"Id":"folder","Name":"Comics","IsFolder":true}],"TotalRecordCount":1}"#.utf8)
            } else if collections, let pages = state.collectionPages {
                body = Data((pages[start] ?? "invalid response").utf8)
            } else if members, let pages = state.memberPages {
                body = Data((pages[start] ?? "invalid response").utf8)
            } else if collections {
                body = Data(#"{"Items":[{"Id":"collection","Name":"Fixture collection","Type":"BoxSet"}],"TotalRecordCount":1}"#.utf8)
            } else if members {
                body = Data(#"{"Items":[{"Id":"unknown","Name":"Inside","Type":"Book","Container":"cbz"},{"Id":"outside","Name":"Other library","Type":"Book","Container":"cbz"},{"Id":"epub","Name":"Unsupported","Type":"Book","Container":"epub"}],"TotalRecordCount":3}"#.utf8)
            } else if ancestors, let fixture = state.ancestry[ancestorID] {
                body = Data(fixture.utf8)
            } else if ancestors {
                body = text.contains("/unknown/Ancestors") ? Data(#"[{"Id":"folder","Name":"Comics","IsFolder":true}]"#.utf8) : Data(#"[{"Id":"other","Name":"Other library","IsFolder":true}]"#.utf8)
            } else if catalogue, let pages = search ? state.searchPages : state.pages {
                body = Data((pages[start] ?? "invalid response").utf8)
            } else if catalogue {
                body = Data(#"{"Items":[{"Id":"unknown","Name":"Catalogue issue","Type":"Book","Container":"cbz","Path":"/private/comic.cbz","SeriesName":"Fixture series","SeriesId":"run","IndexNumber":1.5,"UserData":{"Played":false,"PlaybackPositionTicks":0}},{"Id":"unknown-state","Name":"Unknown progress","Type":"Book","Container":"pdf"}],"TotalRecordCount":2}"#.utf8)
            } else if slow {
                body = Data(repeating: 0x41, count: 64 * 1024)
            } else if detail {
                body = Data(#"{"Id":"book","Name":"Detail fixture","Container":"cbz","Overview":"Synopsis","People":[{"Name":"Same Name","Id":"person-one","Type":"Writer"}]}"#.utf8)
            } else {
                body = unknown ? payload : Data(#"{"Items":[{"Id":"book","Name":"Fixture","Container":"cbz"}],"TotalRecordCount":101}"#.utf8)
            }
            var header = "HTTP/1.1 \(code) Fixture\r\nConnection: close\r\n"
            if slow { header += "Content-Type: application/octet-stream\r\n" }
            if !unknown { header += "Content-Length: \(slow ? body.count * 100 : body.count)\r\n" }
            if redirect { header += "Location: http://127.0.0.1:1/never-forward-credentials\r\n" }
            var response = Data((header + "\r\n").utf8)
            response.append(body)
            let outgoing = response
            DispatchQueue.global().asyncAfter(deadline: .now() + (catalogue ? state.delay : (collections || members ? state.collectionDelay : 0))) {
                connection.send(content: outgoing, completion: .contentProcessed { error in
                    // A deliberately incomplete response stays open until cancellation/fixture cleanup.
                    if !slow || error != nil { connection.cancel() }
                    else { sendSlowChunk(connection, body: body, remaining: 50) }
                })
            }
        }
    }
}
