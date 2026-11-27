import SwiftUI

struct JellyfinAddToListMenu: View {
    let book: JellyfinBook
    let connection: JellyfinConnection
    @ObservedObject var library: LibraryStore

    var body: some View {
        Menu("Add to Reading List") {
            ForEach(library.readingLists) { list in
                Button(list.name) {
                    ReadingListStore.addComics(to: list.id,
                        filenames: [JellyfinListReference(connection: connection, itemID: book.id).token])
                    library.refresh()
                }
            }
            Button("Create Reading List") {
                let list = ReadingListStore.createList(name: book.name,
                    itemFilenames: [JellyfinListReference(connection: connection, itemID: book.id).token])
                _ = list
                library.refresh()
            }
        }
    }
}

struct JellyfinCollectionsSection: View {
    @ObservedObject var catalogue: JellyfinCatalogueModel
    @ObservedObject var browser: JellyfinBrowserModel
    @EnvironmentObject private var library: LibraryStore
    @Binding var unreadOnly: Bool
    var searchQuery = ""
    @State private var selected: JellyfinBook?
    @State private var members: JellyfinCatalogueModel.Group?
    @State private var error: String?
    @State private var loading = false

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Jellyfin collections").font(.headline)
            Text("Read-only collections. Only supported comics in the selected library are shown.")
                .font(.footnote).foregroundStyle(.secondary)
            if let error { Text(error) }
            if loading { ProgressView() }
            ForEach(catalogue.collections.filter { LibraryBrowseState.matches(searchQuery, values: [$0.name]) }) { collection in
                Button(collection.name) { selected = collection }
                    .frame(minHeight: 44)
                    .libraryScrollAnchor("lists:collection-" + collection.id)
            }
            if catalogue.collections.isEmpty {
                Text("No compatible collections loaded.").foregroundStyle(.secondary)
            }
            Button("Refresh collections") { Task { await catalogue.loadCollections(browser: browser) } }
        }
        .padding()
        .task { await catalogue.loadCollections(browser: browser) }
        .task(id: selected?.id) {
            guard let selected else { return }
            loading = true; error = nil
            defer { loading = false }
            do {
                let books = try await catalogue.collectionBooks(selected, browser: browser)
                try Task.checkCancellation()
                members = .init(id: selected.id, name: selected.name, books: books, isCollection: true)
            } catch is CancellationError { }
            catch { self.error = (error as? LocalizedError)?.errorDescription ?? JellyfinError.network.localizedDescription }
            self.selected = nil
        }
        .sheet(item: $members) { group in
            NavigationStack {
                JellyfinGroupView(group: group, browser: browser, catalogue: catalogue, downloadedOnly: false,
                    unreadOnly: $unreadOnly, searchQuery: searchQuery)
                    .environmentObject(library)
            }
        }
    }
}
