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

struct JellyfinMixedListContent: View {
    let list: ReadingListRecord
    let browser: JellyfinBrowserModel?
    let catalogue: JellyfinCatalogueModel?
    @ObservedObject var library: LibraryStore
    @Binding var downloadedOnly: Bool
    @State private var readableCopies: Set<URL> = []
    @ObservedObject private var covers = CoverLoader.shared
    @Binding var unreadOnly: Bool
    var searchQuery = ""
    let openLocal: (URL) -> Void
    @State private var selected: JellyfinBook?

    private func local(_ token: String) -> URL? {
        guard let filename = ReadingListStore.localFilename(for: token) else { return nil }
        return library.comics.first { $0.lastPathComponent == filename }
    }

    private func remote(_ token: String) -> JellyfinBook? {
        guard let ref = JellyfinListReference(token: token), ref.belongs(to: browser?.connection) else { return nil }
        return catalogue?.knownBooks.first { $0.id == ref.itemID } ?? JellyfinBook(id: ref.itemID,
            name: String(localized: "Comic"), isFolder: false, container: nil, path: nil, imageTags: nil, dateModified: nil)
    }

    private var availableMembers: [String] {
        list.itemFilenames.filter { !downloadedOnly || local($0).map { readableCopies.contains($0) } == true }
    }

    private var visibleMembers: [String] {
        availableMembers.filter {
            LibraryBrowseState.includes(completed: completed($0), unreadOnly: unreadOnly) &&
            LibraryBrowseState.matches(searchQuery, values: [list.name,
                local($0).map { ComicMetadataStore.displayFilename(for: $0) } ?? remote($0)?.name ?? ""])
        }
    }

    private func completed(_ member: String) -> Bool? {
        if let url = local(member) { return ReadingProgress.isCompleted(url) }
        return remote(member)?.userData?.Played
    }

    var body: some View {
        VStack(spacing: 12) {
            Toggle("Downloaded only", isOn: $downloadedOnly)
            if let next = ReadingListStore.nextMember(in: availableMembers, completed: completed) {
                let states = availableMembers.map(completed)
                if states.allSatisfy({ $0 != nil }) {
                    Text("\(states.filter { $0 == true }.count) OF \(availableMembers.count) READ")
                        .font(.headline)
                }
                Button("CONTINUE") {
                    if let url = local(next) { openLocal(url) }
                    else { selected = remote(next) }
                }
                .disabled(local(next) == nil && remote(next) == nil)
                .frame(minHeight: 44)
                if local(next) == nil && remote(next) == nil {
                    Text("The list keeps this item. Reconnect its account or restore the local copy to read it.")
                        .font(.footnote)
                }
            }
            ForEach(visibleMembers, id: \.self) { member in
                VStack(alignment: .leading, spacing: 8) {
                    if let url = local(member) {
                        Button { openLocal(url) } label: {
                            Label(ComicMetadataStore.displayFilename(for: url), systemImage: "arrow.down.circle")
                        }
                    } else if let book = remote(member) {
                        Button(book.name) { selected = book }
                        Text("Jellyfin · Needs connection").font(.caption)
                    } else {
                        Text("Unavailable comic").font(.headline)
                        Text("The list keeps this item. Reconnect its account or restore the local copy to read it.")
                            .font(.caption).foregroundStyle(.secondary)
                    }
                    HStack {
                        Button("Move Up") { move(member, direction: -1) }
                            .disabled(list.itemFilenames.first == member)
                        Button("Move Down") { move(member, direction: 1) }
                            .disabled(list.itemFilenames.last == member)
                        Spacer()
                        Button("Remove", role: .destructive) {
                            ReadingListStore.removeComic(from: list.id, filename: member); library.refresh()
                        }
                    }
                    .buttonStyle(.borderless)
                    .frame(minHeight: 44)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding()
                .background(Leaf.raisedSurface, in: RoundedRectangle(cornerRadius: 8))
            }
        }
        .task(id: downloadedOnly ? covers.revisions(for: library.comics) : []) {
            guard downloadedOnly else { return }
            if let readable = try? await LibraryBrowseState.readableCopies(library.comics) { readableCopies = readable }
        }
        .sheet(item: $selected) { book in
            if let browser {
                NavigationStack {
                    JellyfinDetailView(item: book, model: browser,
                        creatorLibraryID: catalogue?.libraryID, creatorCachedBooks: catalogue?.books ?? []).environmentObject(library)
                        .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Close") { selected = nil } } }
                }
            }
        }
    }

    private func move(_ member: String, direction: Int) {
        ReadingListStore.moveComic(in: list.id, filename: member, direction: direction)
        library.refresh()
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
