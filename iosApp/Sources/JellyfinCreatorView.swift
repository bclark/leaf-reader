import SwiftUI
import LeafShared

/// Independent from title search: a creator request always covers the selected library.
@MainActor
final class JellyfinCreatorModel: ObservableObject {
    @Published private(set) var books: [JellyfinBook] = []
    @Published private(set) var loading = false
    @Published private(set) var fromCache = false
    @Published private(set) var complete = false
    @Published private(set) var message: String?
    private let defaults: UserDefaults
    private var generation = UUID()

    private let snapshots: MetadataSnapshotStore

    init(defaults: UserDefaults = .standard, snapshotDirectory: URL? = nil) {
        self.defaults = defaults
        snapshots = .scoped(defaults: defaults, directory: snapshotDirectory)
    }

    private struct Snapshot: Codable, Sendable {
        let books: [JellyfinBook]
        let complete: Bool
    }

    func load(person: JellyfinPerson, libraryID: String?, cachedBooks: [JellyfinBook],
              browser: JellyfinBrowserModel) async {
        let run = UUID()
        generation = run
        books = []
        complete = false
        message = nil
        loading = false
        fromCache = true
        guard let connection = browser.connection, let libraryID else {
            message = String(localized: "Choose a Jellyfin library to find this creator’s comics.")
            return
        }
        let identity = person.id ?? person.name.split(whereSeparator: \.isWhitespace).joined(separator: " ").lowercased()
        let key = "leaf.jellyfin.creator." + [connection.id, connection.serverID, connection.userID,
            libraryID, person.id == nil ? "name" : "id", identity].map { "\($0.count):\($0)" }.joined()
        books = cachedBooks.filter { book in
            (book.people ?? []).compactMap(\.shared).contains { person.matches(other: $0) }
        }
        let snapshot = await snapshots.restore(Snapshot.self, key: key, defaults: defaults)
        guard generation == run, browser.connection == connection, !Task.isCancelled else { return }
        if let snapshot {
            books = snapshot.books
            complete = snapshot.complete
        }
        guard let client = browser.client, !browser.needsSignIn else {
            message = JellyfinError.authentication.localizedDescription
            return
        }
        loading = true
        defer { if generation == run { loading = false } }
        var collected: [JellyfinBook] = []
        var seen: Set<String> = []
        var offset = 0
        do {
            while true {
                let page = try await client.catalogue(userID: connection.userID, libraryID: libraryID,
                    view: .all, start: offset, creator: person)
                try Task.checkCancellation()
                guard generation == run, browser.connection == connection, browser.client === client,
                      !browser.needsSignIn else { throw CancellationError() }
                guard page.total.map({ $0 >= 0 && $0 >= offset + page.items.count }) ?? true,
                      page.items.allSatisfy({ seen.insert($0.id).inserted }),
                      !page.items.isEmpty || (page.total ?? offset) == offset else { throw JellyfinError.response }
                let visible = Set(JellyfinCatalogueLogic.shared.visible(entries: page.items.map(\.catalogueEntry), view: .all).map { $0.item.id })
                collected += page.items.filter { visible.contains($0.id) }.map(\.withoutServerPath)
                offset += page.items.count
                let finished = page.total.map { offset == $0 } ?? (page.items.count < 100)
                // A partial refresh must not discard previously saved relationships.
                books = collected + (finished ? [] : books.filter { !seen.contains($0.id) })
                complete = finished
                fromCache = !finished
                let snapshot = Snapshot(books: books, complete: finished)
                do {
                    try await snapshots.write(keys: [key]) { try JSONEncoder().encode(snapshot) }
                    defaults.removeObject(forKey: key)
                } catch { /* Keep live pagination usable when optional cache persistence fails. */ }
                try Task.checkCancellation()
                guard generation == run, browser.connection == connection, browser.client === client else {
                    throw CancellationError()
                }
                if finished { break }
            }
        } catch is CancellationError {
            if generation == run, browser.connection != connection { books = []; complete = false }
        } catch {
            guard generation == run else { return }
            guard browser.connection == connection else { books = []; complete = false; return }
            fromCache = true
            message = (error as? JellyfinError)?.localizedDescription ?? JellyfinError.network.localizedDescription
        }
    }
}

struct JellyfinCreatorView: View {
    let person: JellyfinPerson
    let libraryID: String?
    let cachedBooks: [JellyfinBook]
    let sourceConnection: JellyfinConnection?
    @ObservedObject var browser: JellyfinBrowserModel
    @StateObject private var results = JellyfinCreatorModel()
    @State private var retry = 0
    @EnvironmentObject private var settings: LeafSettings
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    var body: some View {
        if browser.connection != sourceConnection {
            Text("This comic belongs to another Jellyfin session. Return to your library.")
        } else {
            resultsContent
        }
    }

    private var resultsContent: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                if results.fromCache {
                    Text("Saved creator matches. Results may be incomplete or out of date.")
                        .font(.footnote).foregroundStyle(Leaf.secondaryText)
                }
                if let message = results.message {
                    Text(message)
                    Button("Retry") { retry += 1 }.frame(minHeight: 44)
                }
                if results.books.isEmpty && !results.loading && results.message == nil {
                    Text("No comics by this creator in this library.")
                }
                LazyVGrid(columns: [GridItem(.adaptive(minimum: dynamicTypeSize.isAccessibilitySize ? 280 : 140))],
                          alignment: .leading, spacing: 18) {
                    ForEach(results.books) { book in
                        NavigationLink {
                            JellyfinDetailView(item: book, model: browser, creatorLibraryID: libraryID,
                                creatorCachedBooks: results.books)
                        } label: {
                            JellyfinComicCard(book: book, browser: browser)
                        }
                        .buttonStyle(.plain)
                    }
                }
                if results.loading { ProgressView("Loading Jellyfin…") }
            }
            .padding(20)
        }
        .background(settings.ground)
        .foregroundStyle(Leaf.text)
        .tint(Leaf.goldText)
        .navigationTitle(String(localized: "Comics by \(person.name)"))
        .navigationBarTitleDisplayMode(.inline)
        .task(id: [browser.connection?.id ?? "", libraryID ?? "", String(retry)]) {
            await results.load(person: person,
                libraryID: browser.connection == sourceConnection ? libraryID : nil,
                cachedBooks: browser.connection == sourceConnection ? cachedBooks : [], browser: browser)
        }
    }
}
