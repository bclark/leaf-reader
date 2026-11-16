import SwiftUI
import LeafShared
import os

/// Metadata browsing never imports archives or sends reading-position updates.
@MainActor
final class JellyfinCatalogueModel: ObservableObject {
    struct Group: Identifiable {
        let id: String
        let name: String
        let books: [JellyfinBook]
        var isCollection = false
    }

    @Published private(set) var books: [JellyfinBook] = []
    @Published private(set) var removedBooks: [JellyfinBook] = []
    var knownBooks: [JellyfinBook] { books + removedBooks }
    @Published private(set) var collections: [JellyfinBook] = []
    @Published private(set) var libraries: [JellyfinBook] = []
    @Published private(set) var libraryID: String?
    @Published private(set) var loading = false
    @Published private(set) var hasMore = false
    @Published private(set) var message: String?
    @Published private(set) var unavailable = false
    @Published private(set) var unreachable = false
    @Published private(set) var fromCache = false
    @Published private(set) var requiresSignIn = false
    @Published private(set) var searchBooks: [JellyfinBook] = []
    @Published private(set) var searchQuery = ""
    @Published private(set) var searching = false
    @Published private(set) var searchHasMore = false
    @Published private(set) var searchMessage: String?
    @Published private(set) var indexComplete = false
    private var searchGeneration = UUID()
    private var searchOffset = 0
    private var folders: [String: String] = [:]
    private var collectionMembers: [String: [JellyfinBook]] = [:]
    private var lastRefresh: Date?
    private var generation = UUID()
    private var scope: JellyfinConnection?
    /// A visible Continue item must belong to the same authenticated session as its client.
    func isCurrent(for connection: JellyfinConnection?) -> Bool {
        guard let connection else { return false }
        return scope == connection
    }

    private var offset = 0
    private let defaults: UserDefaults

    let snapshots: MetadataSnapshotStore

    init(defaults: UserDefaults = .standard, snapshotDirectory: URL? = nil) {
        self.defaults = defaults
        snapshots = .scoped(defaults: defaults, directory: snapshotDirectory)
    }

    private func key(_ connection: JellyfinConnection) -> String {
        // Length-prefixed identity avoids collisions and never includes credentials or URL.
        "leaf.jellyfin.catalogue." + [connection.serverID, connection.userID].map { "\($0.count):\($0)" }.joined()
    }

    private struct Snapshot: Codable, Sendable {
        let libraryID: String
        let books: [JellyfinBook]
        let offset: Int
        let hasMore: Bool
        var removedBooks: [JellyfinBook]? = nil
        var folders: [String: String]? = nil
        var complete: Bool? = nil
        var collections: [JellyfinBook]? = nil
        var collectionMembers: [String: [JellyfinBook]]? = nil

        func sanitized() -> Snapshot {
            Snapshot(libraryID: libraryID, books: books.map(\.withoutServerPath), offset: offset,
                hasMore: hasMore, removedBooks: removedBooks?.map(\.withoutServerPath), folders: folders,
                complete: complete, collections: collections?.map(\.withoutServerPath),
                collectionMembers: collectionMembers?.mapValues { $0.map(\.withoutServerPath) })
        }
    }

    private func restoreSnapshot(key: String) async -> Snapshot? {
        await snapshots.restore(Snapshot.self, key: key, defaults: defaults)
    }

    func reset() {
        generation = UUID()
        searchGeneration = UUID()
        searchBooks = []; searchQuery = ""; searchMessage = nil; searching = false; searchHasMore = false
        collectionMembers = [:]
        folders = [:]; indexComplete = false; lastRefresh = nil
        scope = nil
        books = []; removedBooks = []; collections = []; libraries = []; libraryID = nil
        offset = 0; loading = false; hasMore = false; message = nil
        unavailable = false; unreachable = false; fromCache = false; requiresSignIn = false
    }

    func start(browser: JellyfinBrowserModel, force: Bool = false) async {
        guard let connection = browser.connection else { reset(); return }
        guard force || scope != connection else { return }
        reset()
        scope = connection
        let token = generation
        let saved = await restoreSnapshot(key: key(connection))
        guard generation == token, browser.connection == connection, !Task.isCancelled else { return }
        if let saved {
            libraryID = saved.libraryID; books = saved.books; removedBooks = saved.removedBooks ?? []
            offset = saved.offset; hasMore = saved.hasMore; fromCache = true
            folders = saved.folders ?? [:]; indexComplete = saved.complete ?? !saved.hasMore
            collections = saved.collections ?? []; collectionMembers = saved.collectionMembers ?? [:]
        }
        guard let client = browser.client, !browser.needsSignIn else {
            unavailable = true; requiresSignIn = true; message = JellyfinError.authentication.localizedDescription; return
        }
        loading = true
        defer { if generation == token { loading = false } }
        do {
            let page = try await client.page(userID: connection.userID, parentID: nil, start: 0)
            try validate(token, connection, browser)
            libraries = page.items.filter { $0.collectionType == "books" || $0.collectionType == nil }
            if let selected = libraryID, !libraries.contains(where: { $0.id == selected }) {
                libraryID = nil; books = []; removedBooks = []; collections = []; collectionMembers = [:]; folders = [:]; offset = 0; hasMore = false; fromCache = false
            }
            if libraryID == nil, libraries.count == 1 { libraryID = libraries.first?.id }
            if libraryID != nil { try await index(browser: browser, token: token) }
        } catch is CancellationError {
        } catch { if scope == browser.connection { failure(error, token: token) } }
    }

    func selectLibrary(_ id: String, browser: JellyfinBrowserModel) async {
        guard libraries.contains(where: { $0.id == id }) else { return }
        generation = UUID()
        loading = false
        searchGeneration = UUID(); searchBooks = []; searchQuery = ""; searching = false
        libraryID = id; books = []; removedBooks = []; collections = []; collectionMembers = [:]; folders = [:]; offset = 0; hasMore = false; indexComplete = false
        let token = generation
        let connection = scope
        let saved = if let connection { await restoreSnapshot(key: key(connection) + ".library." + id) } else { nil as Snapshot? }
        guard generation == token, connection == browser.connection, !Task.isCancelled else { return }
        if let saved {
            books = saved.books; removedBooks = saved.removedBooks ?? []; folders = saved.folders ?? [:]; indexComplete = saved.complete ?? false
            collections = saved.collections ?? []; collectionMembers = saved.collectionMembers ?? [:]
            fromCache = true
        }
        await refresh(browser: browser)
    }

    func refresh(browser: JellyfinBrowserModel, more: Bool = false) async {
        guard !loading, scope == browser.connection, libraryID != nil else { return }
        let token = generation
        loading = true
        defer { if generation == token { loading = false } }
        do { try await index(browser: browser, token: token) }
        catch is CancellationError { }
        catch { if scope == browser.connection { failure(error, token: token) } }
    }

    func refreshIfNeeded(browser: JellyfinBrowserModel) async {
        guard !indexComplete || (lastRefresh.map({ Date().timeIntervalSince($0) > 60 }) ?? true) else { return }
        await refresh(browser: browser)
    }

    private func saveSnapshot(connection: JellyfinConnection, libraryID: String) async throws {
        let snapshot = Snapshot(libraryID: libraryID, books: books, offset: offset, hasMore: hasMore,
            removedBooks: removedBooks, folders: folders, complete: indexComplete,
            collections: collections, collectionMembers: collectionMembers)
        let keys = [key(connection), key(connection) + ".library." + libraryID]
        try await snapshots.write(keys: keys) { try JSONEncoder().encode(snapshot.sanitized()) }
        for key in keys { defaults.removeObject(forKey: key) }
    }

    /// Publish pages quickly, but remove old entries only after an entire scoped traversal succeeds.
    private func index(browser: JellyfinBrowserModel, token: UUID) async throws {
        let interval = LeafPerformance.signposter.beginInterval("Catalogue index", id: LeafPerformance.signposter.makeSignpostID())
        defer { LeafPerformance.signposter.endInterval("Catalogue index", interval) }
        guard let connection = scope, let client = browser.client, let libraryID else { throw JellyfinError.authentication }
        lastRefresh = Date()
        var fresh: [JellyfinBook] = []
        var seen = Set<String>()
        var checkedFolders = Set<String>()
        var expectedTotal: Int?
        var freshFolders: [String: String] = [:]
        var start = 0
        hasMore = true
        indexComplete = false
        message = nil
        repeat {
            let page = try await client.catalogue(userID: connection.userID, libraryID: libraryID, view: .all, start: start)
            try validate(token, connection, browser)
            // A changing inventory or repeated page is not a coherent deletion snapshot.
            guard let total = page.total, total >= 0,
                  expectedTotal == nil || expectedTotal == total,
                  start + page.items.count <= total,
                  Set(page.items.map(\.id)).count == page.items.count,
                  page.items.allSatisfy({ !seen.contains($0.id) }) else { throw JellyfinError.response }
            expectedTotal = total
            let ids = Set(JellyfinCatalogueLogic.shared.visible(entries: page.items.map(\.catalogueEntry), view: .all).map { $0.item.id })
            let unique = page.items.filter { ids.contains($0.id) && seen.insert($0.id).inserted }
            guard page.items.isEmpty || !unique.isEmpty else { throw JellyfinError.response }
            for book in unique {
                if let parent = book.parentId, parent != libraryID, checkedFolders.insert(parent).inserted,
                   let folder = try await client.verifiedFolder(for: book, libraryID: libraryID, userID: connection.userID) {
                    try validate(token, connection, browser)
                    freshFolders[parent] = folder.name
                }
            }
            try validate(token, connection, browser)
            fresh += unique
            start += page.items.count
            offset = start
            hasMore = !page.items.isEmpty && (page.total.map { start < $0 } ?? (page.items.count == 100))
            // Never interpret an early empty response with an advertised remainder as deletion evidence.
            if page.items.isEmpty, let total = page.total, start < total { throw JellyfinError.response }
            folders.merge(freshFolders) { _, new in new }
            books = fresh + books.filter { !seen.contains($0.id) }
            removedBooks.removeAll { seen.contains($0.id) }
            unavailable = false; unreachable = false; requiresSignIn = false
            fromCache = hasMore
            if !hasMore {
                var archivedIDs = Set<String>()
                removedBooks = (books + removedBooks).filter { !seen.contains($0.id) && archivedIDs.insert($0.id).inserted }
                books = fresh
                indexComplete = true
            }
            try await saveSnapshot(connection: connection, libraryID: libraryID)
            try validate(token, connection, browser)
            await Task.yield()
        } while hasMore
    }

    func search(_ query: String, browser: JellyfinBrowserModel, more: Bool = false) async {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        if more {
            guard trimmed == searchQuery, !searching, searchHasMore, searchMessage == nil else { return }
        } else {
            searchGeneration = UUID()
            searchQuery = trimmed; searchOffset = 0; searchHasMore = false; searchMessage = nil
            searchBooks = books.filter { $0.name.localizedCaseInsensitiveContains(trimmed) ||
                ($0.seriesName?.localizedCaseInsensitiveContains(trimmed) ?? false) ||
                ($0.parentId.flatMap { folders[$0] }?.localizedCaseInsensitiveContains(trimmed) ?? false) }
        }
        guard !trimmed.isEmpty, let connection = scope, let libraryID, let client = browser.client else {
            searching = false; return
        }
        let token = generation
        let queryToken = searchGeneration
        searching = true
        defer { if searchGeneration == queryToken { searching = false } }
        do {
            if !more { try await Task.sleep(nanoseconds: 250_000_000) }
            let page = try await client.catalogue(userID: connection.userID, libraryID: libraryID,
                view: .all, search: trimmed, start: searchOffset)
            try validate(token, connection, browser)
            guard searchGeneration == queryToken else { return }
            let validIDs = Set(JellyfinCatalogueLogic.shared.visible(entries: page.items.map(\.catalogueEntry), view: .all).map { $0.item.id })
            var seen = Set(more ? searchBooks.map(\.id) : [])
            let next = page.items.filter { validIDs.contains($0.id) && seen.insert($0.id).inserted }
            guard !more || page.items.isEmpty || !next.isEmpty else { throw JellyfinError.response }
            searchBooks = (more ? searchBooks : []) + next
            searchOffset += page.items.count
            searchHasMore = !page.items.isEmpty && (page.total.map { searchOffset < $0 } ?? (page.items.count == 100))
        } catch is CancellationError { }
        catch {
            guard generation == token, searchGeneration == queryToken, scope == browser.connection else { return }
            searchMessage = (error as? LocalizedError)?.errorDescription ?? JellyfinError.response.localizedDescription
        }
    }

    func loadCollections(browser: JellyfinBrowserModel) async {
        guard let connection = scope, let libraryID, let client = browser.client else { return }
        let token = generation
        do {
            var result: [JellyfinBook] = []
            var start = 0
            var expectedTotal: Int?
            var seen = Set<String>()
            while true {
                let page = try await client.catalogue(userID: connection.userID, libraryID: libraryID, view: .lists, start: start)
                try validate(token, connection, browser)
                let ids = page.items.map(\.id)
                guard (start == 0 || page.total == expectedTotal),
                      page.total.map({ $0 >= 0 && start + ids.count <= $0 }) ?? true,
                      !ids.isEmpty || (page.total.map({ start == $0 }) ?? true),
                      Set(ids).count == ids.count, ids.allSatisfy({ !seen.contains($0) }) else { throw JellyfinError.response }
                expectedTotal = page.total
                seen.formUnion(ids)
                let next = page.items.filter { $0.type == "BoxSet" }
                result += next; start += page.items.count
                if page.items.isEmpty || (page.total.map { start >= $0 } ?? (page.items.count < 100)) { break }
            }
            collections = result
            collectionMembers = collectionMembers.filter { entry in result.contains { $0.id == entry.key } }
            try await saveSnapshot(connection: connection, libraryID: libraryID)
            try validate(token, connection, browser)
            unavailable = false; unreachable = false; requiresSignIn = false; message = nil
        } catch is CancellationError { }
        catch { if scope == browser.connection { failure(error, token: token) } }
    }

    func collectionBooks(_ collection: JellyfinBook, browser: JellyfinBrowserModel) async throws -> [JellyfinBook] {
        guard let connection = scope, let libraryID, let client = browser.client else { throw JellyfinError.authentication }
        if unavailable, let cached = collectionMembers[collection.id] { return cached }
        let token = generation
        var result: [JellyfinBook] = []
        var seen = Set<String>()
        var start = 0
        var expectedTotal: Int?
        while true {
            let page = try await client.catalogue(userID: connection.userID, libraryID: libraryID,
                view: .all, collectionID: collection.id, start: start)
            try validate(token, connection, browser)
            let ids = page.items.map(\.id)
            guard (start == 0 || page.total == expectedTotal),
                  page.total.map({ $0 >= 0 && start + ids.count <= $0 }) ?? true,
                  !ids.isEmpty || (page.total.map({ start == $0 }) ?? true),
                  Set(ids).count == ids.count, ids.allSatisfy({ !seen.contains($0) }) else { throw JellyfinError.response }
            expectedTotal = page.total
            seen.formUnion(ids)
            for book in page.items where book.type == "Book" && book.supported {
                if try await client.belongsToLibrary(book.id, libraryID: libraryID, userID: connection.userID) {
                    try validate(token, connection, browser)
                    result.append(book)
                }
            }
            start += page.items.count
            if page.items.isEmpty || (page.total.map { start >= $0 } ?? (page.items.count < 100)) { break }
        }
        collectionMembers[collection.id] = result
        try await saveSnapshot(connection: connection, libraryID: libraryID)
        try validate(token, connection, browser)
        return result
    }

    func groups(_ entries: [JellyfinBook]) -> [Group] {
        guard let scope, let libraryID else { return [] }
        let lookup = Dictionary(entries.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
        return JellyfinSeries.shared.groupsWithFolders(server: scope.serverID, user: scope.userID, library: libraryID,
            entries: entries.map(\.catalogueEntry), folders: folders).map {
                Group(id: $0.key, name: $0.name, books: $0.entries.compactMap { lookup[$0.item.id] })
            }
    }

    private func validate(_ token: UUID, _ connection: JellyfinConnection, _ browser: JellyfinBrowserModel) throws {
        try Task.checkCancellation()
        guard token == generation, connection == browser.connection else { throw CancellationError() }
    }

    private func failure(_ error: Error, token: UUID) {
        guard token == generation else { return }
        unavailable = true
        unreachable = false
        fromCache = !books.isEmpty
        if case JellyfinError.authentication = error { requiresSignIn = true }
        if case JellyfinError.network = error {
            unreachable = true
            message = String(localized: "Jellyfin server is unreachable. Local copies are available in Offline.")
        } else {
            message = (error as? LocalizedError)?.errorDescription ?? JellyfinError.response.localizedDescription
        }
    }
}
