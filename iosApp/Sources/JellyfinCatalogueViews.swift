import SwiftUI

struct JellyfinCatalogueStatus: View {
    @ObservedObject var catalogue: JellyfinCatalogueModel
    @ObservedObject var browser: JellyfinBrowserModel
    let connect: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            if let message = catalogue.message {
                Text(message).font(.footnote)
            }
            if let selected = catalogue.libraries.first(where: { $0.id == catalogue.libraryID }) {
                Text(selected.name).font(.headline)
            }
            if catalogue.fromCache { Text("Saved catalogue").font(.footnote) }
            if catalogue.loading { ProgressView("Loading Jellyfin…") }
            HStack {
                if browser.needsSignIn || catalogue.requiresSignIn {
                    Button("Sign in") { browser.needsSignIn = true; connect() }
                } else {
                    Button("Retry") { Task { await catalogue.start(browser: browser, force: true) } }
                        .disabled(catalogue.loading)
                }
                Spacer()
                Menu("Jellyfin libraries") {
                    ForEach(catalogue.libraries) { library in
                        Button(library.name) {
                            Task { await catalogue.selectLibrary(library.id, browser: browser) }
                        }
                    }
                    Button("Connection settings", action: connect)
                }
            }
            .frame(minHeight: 44)
        }
        .padding(.horizontal, 18)
    }
}

struct JellyfinMainGrid: View {
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @ObservedObject var catalogue: JellyfinCatalogueModel
    @ObservedObject var browser: JellyfinBrowserModel
    let filter: LibraryFilter
    let query: String
    let columns: Int
    let downloadedOnly: Bool
    var unreadOnly = false
    var scrollScope = "all"
    let select: (JellyfinBook) -> Void
    let selectGroup: (JellyfinCatalogueModel.Group) -> Void
    @EnvironmentObject private var library: LibraryStore

    var visible: [JellyfinBook] {
        let entries = downloadedOnly ? catalogue.knownBooks :
            (query.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? catalogue.books : catalogue.searchBooks)
        return entries.filter {
            LibraryBrowseState.includes(completed: browser.downloaded[$0.id].map { ReadingProgress.isCompleted($0) } ?? $0.userData?.Played,
                unreadOnly: unreadOnly || filter == .unread) &&
            (!downloadedOnly || (browser.downloaded[$0.id] != nil &&
                (query.isEmpty || $0.name.localizedCaseInsensitiveContains(query) ||
                 ($0.seriesName?.localizedCaseInsensitiveContains(query) ?? false))))
        }
    }

    var body: some View {
        let entries = visible
        VStack(alignment: .leading, spacing: 16) {
            if catalogue.libraryID == nil && !catalogue.loading {
                Text("Choose a Jellyfin library above.").foregroundStyle(.secondary)
            } else if entries.isEmpty && !catalogue.loading {
                Text("No matching comics in the loaded catalogue.").foregroundStyle(.secondary)
            }
            LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: columns >= 4 ? 12 : 16, alignment: .top), count: dynamicTypeSize.isAccessibilitySize ? 1 : columns),
                      spacing: columns >= 4 ? 14 : 18) {
                if filter == .series {
                    ForEach(catalogue.groups(entries)) { group in
                        JellyfinSeriesCard(group: group, browser: browser, columns: columns) { selectGroup(group) }
                            .fixedSize(horizontal: false, vertical: true)
                            .libraryScrollAnchor(scrollScope + ":remote-series-" + group.id)
                    }
                } else {
                    ForEach(entries) { book in
                        Button { select(book) } label: {
                            JellyfinComicCard(book: book, browser: browser, columns: columns)
                        }
                        .buttonStyle(.plain)
                        .fixedSize(horizontal: false, vertical: true)
                        .libraryScrollAnchor(scrollScope + ":remote-" + book.id)
                        .contextMenu {
                            if let connection = browser.connection {
                                JellyfinAddToListMenu(book: book, connection: connection, library: library)
                            }
                        }
                    }
                }
            }
            if catalogue.hasMore {
                Text("Showing loaded comics. More are available.").font(.footnote)
                if !catalogue.loading, catalogue.message != nil {
                    Button("Retry") { Task { await catalogue.refresh(browser: browser) } }
                        .frame(minHeight: 44)
                }
            }
            if let message = catalogue.searchMessage, !query.isEmpty {
                Text(message).font(.footnote)
                Button("Retry") { Task { await catalogue.search(query, browser: browser) } }
            }
            if catalogue.searching { ProgressView("Loading Jellyfin…") }
            if catalogue.searchHasMore, catalogue.searchMessage == nil {
                ProgressView().task(id: catalogue.searchBooks.count) {
                    await catalogue.search(query, browser: browser, more: true)
                }
            }
        }
        .padding(18)
        .task(id: [query, catalogue.libraryID ?? "", browser.connection?.id ?? ""]) {
            if !downloadedOnly { await catalogue.search(query, browser: browser) }
        }
    }
}

struct JellyfinComicCard: View {
    let book: JellyfinBook
    @ObservedObject var browser: JellyfinBrowserModel
    var columns = 2
    @State private var cover: UIImage?

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            ZStack(alignment: .bottom) {
                if let cover {
                    LibraryCoverArtwork(image: cover)
                } else {
                    GeneratedCover(title: book.name, columns: columns)
                }
                if let percent = book.userData?.PlayedPercentage, percent.isFinite {
                    GeometryReader { geometry in
                        Leaf.ochre.frame(width: geometry.size.width * min(1, max(0, percent / 100)))
                    }
                    .frame(height: columns >= 4 ? 5 : 7)
                    .background(Leaf.ink)
                }
            }
            .aspectRatio(0.7, contentMode: .fit)
            .clipShape(RoundedCornerShape(cornerRadius: 4))
            .shadow(color: .black.opacity(0.24), radius: columns >= 4 ? 3 : 5, y: 3)
            Text(book.name)
                .leafControlFont(columns >= 4 ? 10 : 12, weight: 800)
                .foregroundStyle(Leaf.text).lineLimit(2)
            HStack(spacing: 4) {
                Text("Jellyfin")
                if browser.downloaded[book.id] != nil { Image(systemName: "arrow.down.circle.fill") }
                if book.userData?.Played == true { Text("Finished") }
            }
            .font(.caption2).foregroundStyle(Leaf.secondaryText)
        }
        .accessibilityElement(children: .combine)
        .accessibilityValue(browser.downloaded[book.id] != nil ? Text("Available offline") : Text("Needs connection"))
        .task(id: (browser.connection?.id ?? "") + book.id + (book.imageTags?["Primary"] ?? "")) {
            cover = nil
            if let local = browser.downloaded[book.id] {
                cover = await CoverLoader.load(for: local).cover
            } else if let client = browser.client, let data = try? await client.cover(book) {
                let image = await Task.detached(priority: .utility) { JellyfinBookRow.thumbnail(data) }.value
                if !Task.isCancelled { cover = image }
            }
        }
    }
}

struct JellyfinSeriesCard: View {
    let group: JellyfinCatalogueModel.Group
    @ObservedObject var browser: JellyfinBrowserModel
    let columns: Int
    let open: () -> Void
    private var progress: Double? {
        let values = group.books.compactMap { $0.userData?.PlayedPercentage }.filter { $0.isFinite }
        guard !values.isEmpty, values.count == group.books.count else { return nil }
        return values.reduce(0) { $0 + min(100, max(0, $1)) } / Double(values.count) / 100
    }

    var body: some View {
        ComicStackCard(seriesName: group.name, volume: nil, issues: [],
            totalIssues: group.books.count,
            unreadIssues: group.books.filter { $0.userData?.Played == false }.count,
            progressFraction: progress ?? 0, showsProgress: progress != nil, columns: columns,
            remoteBooks: group.books, remoteClient: browser.client,
            remoteProfileID: browser.connection?.id ?? "", remoteCopies: browser.downloaded, onOpenSeries: open)
    }
}

struct JellyfinGroupView: View {
    let group: JellyfinCatalogueModel.Group
    @ObservedObject var browser: JellyfinBrowserModel
    @ObservedObject var catalogue: JellyfinCatalogueModel
    @State var downloadedOnly: Bool
    @State private var readableCopies: Set<URL> = []
    @ObservedObject private var covers = CoverLoader.shared
    @Binding var unreadOnly: Bool
    var searchQuery = ""
    @EnvironmentObject private var library: LibraryStore
    @Environment(\.dismiss) private var dismiss

    private var currentBooks: [JellyfinBook] {
        if group.isCollection { return group.books }
        return catalogue.groups(downloadedOnly ? catalogue.knownBooks : catalogue.books).first { $0.id == group.id }?.books ?? []
    }

    var body: some View {
        ScrollView {
            UnreadFilterChip(isOn: $unreadOnly).padding(.horizontal)
            Toggle("Downloaded only", isOn: $downloadedOnly).padding()
            LazyVGrid(columns: [GridItem(.adaptive(minimum: 140))], spacing: 18) {
                ForEach(currentBooks.filter {
                    (!downloadedOnly || browser.downloaded[$0.id].map { readableCopies.contains($0) } == true) &&
                    LibraryBrowseState.includes(completed: browser.downloaded[$0.id].map { ReadingProgress.isCompleted($0) } ?? $0.userData?.Played, unreadOnly: unreadOnly) &&
                    LibraryBrowseState.matches(searchQuery, values: [$0.name, $0.seriesName ?? "", group.name])
                }) { book in
                    NavigationLink {
                        JellyfinDetailView(item: book, model: browser,
                            creatorLibraryID: catalogue.libraryID, creatorCachedBooks: catalogue.books).environmentObject(library)
                    } label: {
                        JellyfinComicCard(book: book, browser: browser)
                    }.buttonStyle(.plain)
                    .contextMenu {
                        if let connection = browser.connection {
                            JellyfinAddToListMenu(book: book, connection: connection, library: library)
                        }
                    }
                }
            }.padding()
        }
        .task(id: downloadedOnly ? covers.revisions(for: Array(browser.downloaded.values).sorted { $0.path < $1.path }) : []) {
            guard downloadedOnly else { return }
            if let readable = try? await LibraryBrowseState.readableCopies(Array(browser.downloaded.values)) {
                readableCopies = readable
            }
        }
        .navigationTitle(group.name)
        .toolbar {
            ToolbarItem(placement: .cancellationAction) { Button("Close") { dismiss() } }
            ToolbarItem(placement: .primaryAction) { UnreadFilterButton(isOn: $unreadOnly) }
        }
    }
}
