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
            if catalogue.fromCache { Text("Saved catalogue").font(.footnote) }
            if catalogue.loading { ProgressView("Loading Jellyfin…") }
            if catalogue.message != nil || catalogue.fromCache || browser.needsSignIn || catalogue.requiresSignIn {
                HStack {
                    if browser.needsSignIn || catalogue.requiresSignIn {
                        Button("Sign in") { browser.needsSignIn = true; connect() }
                    } else {
                        Button("Retry") { Task { await catalogue.start(browser: browser, force: true) } }
                            .disabled(catalogue.loading)
                    }
                }
                .frame(minHeight: 44)
            }
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
                Text("Choose a Jellyfin library in Menu.").foregroundStyle(.secondary)
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
    @EnvironmentObject private var settings: LeafSettings
    let book: JellyfinBook
    @ObservedObject var browser: JellyfinBrowserModel
    var columns = 2
    @State private var loadedIdentity = ""
    @State private var coverPixels = 0
    @State private var cover: UIImage?
    private var readingState: LibraryReadingState {
        if let copy = browser.downloaded[book.id] { return .local(ReadingProgress.get(copy)) }
        return .remote(book.userData)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            ZStack(alignment: .bottom) {
                if let cover {
                    LibraryCoverArtwork(image: cover)
                } else {
                    GeneratedCover(title: book.name, columns: columns)
                }
                LibraryReadingIndicator(state: readingState)
                if browser.downloaded[book.id] != nil {
                    Image(systemName: "arrow.down.circle.fill")
                        .foregroundStyle(Leaf.paperWhite).padding(6)
                        .background(Leaf.ink.opacity(0.85), in: Circle()).padding(6)
                        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
                }
            }
            .aspectRatio(0.7, contentMode: .fit)
            .clipShape(RoundedCornerShape(cornerRadius: 4))
            .shadow(color: .black.opacity(0.24), radius: columns >= 4 ? 3 : 5, y: 3)
            .accessibilityHidden(true)
            if settings.libraryShowTitles {
            Text(book.name)
                .leafControlFont(columns >= 4 ? 10 : 12, weight: 800)
                .foregroundStyle(Leaf.text).lineLimit(2)
            }
            if settings.libraryShowDetails {
            HStack(spacing: 4) {
                Text("Jellyfin")
                if browser.downloaded[book.id] != nil { Image(systemName: "arrow.down.circle.fill") }

            }
            .font(.caption2).foregroundStyle(Leaf.secondaryText)
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(verbatim: book.name + ", Jellyfin"))
        .accessibilityValue(Text("\(browser.downloaded[book.id] != nil ? String(localized: "Available offline") : String(localized: "Needs connection")), \(readingState.label)"))
        .libraryCoverResolution($coverPixels)
        .task(id: (browser.connection?.id ?? "") + book.id + (book.imageTags?["Primary"] ?? "") + ":" + String(coverPixels)) {
            guard coverPixels > 0 else { return }
            let identity = (browser.connection?.id ?? "") + book.id + (book.imageTags?["Primary"] ?? "")
            if loadedIdentity != identity { cover = nil; loadedIdentity = identity }
            if let local = browser.downloaded[book.id] {
                let image = await CoverLoader.load(for: local, maxPixel: coverPixels).cover
                if !Task.isCancelled { cover = image }
            } else if let client = browser.client, let data = try? await client.cover(book, maxWidth: coverPixels) {
                let image = await Task.detached(priority: .utility) { JellyfinBookRow.thumbnail(data, maxPixel: coverPixels) }.value
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
    var body: some View {
        ComicStackCard(seriesName: group.name, volume: nil, issues: [],
            totalIssues: group.books.count,
            unreadIssues: group.books.filter { $0.userData?.Played == false }.count,
            progressFraction: 0, columns: columns,
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
