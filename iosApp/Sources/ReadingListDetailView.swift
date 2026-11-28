import SwiftUI

/// Dedicated screen for a custom reading list, matching Android's ReadingListDetailScreen.kt.
/// Uses a compact single-row Hero Status Bar with unified read-progress badge, BookStatusBadgeIcon,
/// compact START / CONTINUE / RESTART pill, and a pinned bottom-right "+ ADD COMICS" FAB.
struct ReadingListDetailView: View {
    let listId: String
    @ObservedObject var library: LibraryStore
    var linkedFiles: LinkedFilesStore? = nil
    let onDismiss: () -> Void
    var initialShowAddComics: Bool = false
    @ObservedObject var jellyfin: JellyfinBrowserModel
    @ObservedObject var catalogue: JellyfinCatalogueModel
    @State var downloadedOnly = false
    @Binding var unreadOnly: Bool
    var searchQuery = ""

    @Namespace private var coverTransition
    @State private var openComic: OpenComic?
    @State private var openLinked: LinkedComic?
    @EnvironmentObject private var settings: LeafSettings
    @State private var showAddComicsSheet = false
    @State private var selectedToAdd: Set<String> = []
    @State private var addSearchQuery = ""
    @State private var showRenameAlert = false
    @State private var renameInput = ""
    @State private var showDeleteListAlert = false
    @State private var comicToRemove: String?
    @State private var selectedRemote: JellyfinBook?
    @State private var readableCopies: Set<URL> = []
    @ObservedObject private var covers = CoverLoader.shared

    private var currentList: ReadingListRecord? {
        library.readingLists.first { $0.id == listId }
    }

    private var catalogueBooks: [JellyfinBook] {
        catalogue.isCurrent(for: jellyfin.connection) ? catalogue.knownBooks : []
    }

    private var allMembers: [ReadingListMember] {
        ReadingListMember.resolve(currentList?.itemFilenames ?? [], local: library.comics,
            metadata: library.metadata, books: catalogueBooks, connection: jellyfin.connection,
            linked: linkedFiles?.comics ?? [])
    }

    private var members: [ReadingListMember] {
        allMembers.filter { !downloadedOnly || $0.url.map { readableCopies.contains($0) } == true }
    }

    private var completedCount: Int { members.filter { $0.completed == true }.count }
    private var allRead: Bool { !members.isEmpty && members.allSatisfy { $0.completed == true } }
    private var nextUnread: ReadingListMember? { members.first { $0.completed != true } ?? members.first }

    var heroButtonText: String {
        guard let target = nextUnread else { return String(localized: "START") }
        if allRead { return String(localized: "READ FROM START: \(target.title)") }
        return shortActionLabel + ": " + target.title
    }

    var shortActionLabel: String {
        guard let target = nextUnread else { return String(localized: "START") }
        if allRead { return String(localized: "RESTART") }
        let started = (target.url ?? target.linked?.progressURL).flatMap { ReadingProgress.get($0)?.page }.map { $0 > 0 }
            ?? ((target.book?.userData?.PlayedPercentage ?? 0) > 0)
        return started || completedCount > 0 ? String(localized: "CONTINUE") : String(localized: "START")
    }

    private func open(_ member: ReadingListMember) {
        if let comic = member.linked { openLinked = comic }
        else if let url = member.url { openComic = OpenComic(url: url) }
        else if let book = member.book { selectedRemote = book }
    }

    private func move(_ member: ReadingListMember, direction: Int) {
        ReadingListStore.moveComic(in: listId, filename: member.id, direction: direction)
        library.refresh()
    }

    var body: some View {
        ZStack {
            settings.ground.ignoresSafeArea()
            Halftone(color: Leaf.crimson, alpha: 0.05).ignoresSafeArea()

            VStack(spacing: 0) {
                appBar
                GeometryReader { geo in
                    content(for: geo.size.width)
                }
            }

            // Pinned Bottom-Right Extended FAB (+ ADD COMICS)
            VStack {
                Spacer()
                HStack {
                    Spacer()
                    Button {
                        selectedToAdd.removeAll()
                        addSearchQuery = ""
                        showAddComicsSheet = true
                    } label: {
                        HStack(spacing: 8) {
                            Image(systemName: "plus")
                                .font(.system(size: 16, weight: .bold))
                                .foregroundColor(Leaf.ink)
                            Text("ADD COMICS")
                                .font(.custom("Anton-Regular", size: 15, relativeTo: .headline))
                                .tracking(0.8)
                                .foregroundColor(Leaf.ink)
                        }
                        .padding(.horizontal, 16)
                        .padding(.vertical, 12)
                        .background(Leaf.ochre)
                        .clipShape(RoundedCornerShape(cornerRadius: 6))
                        .overlay(RoundedCornerShape(cornerRadius: 6).stroke(Leaf.ink, lineWidth: 2))
                        .comicShadow(offset: 4, color: .black.opacity(0.75), corner: 6)
                    }
                    .buttonStyle(.plain)
                    .padding(20)
                }
            }
        }
        .leafAppearance(settings.theme)
        .navigationBarHidden(true)
        .comicReaderPresentation(item: $openComic, namespace: coverTransition) {
            library.refresh()
        }
        .linkedReaderPresentation(item: $openLinked, store: linkedFiles) {
            library.refresh(backfill: false)
        }
        .onChange(of: jellyfin.connection?.id) { _ in
            selectedToAdd.removeAll()
            selectedRemote = nil
        }
        .onAppear {
            if initialShowAddComics {
                showAddComicsSheet = true
            }
        }
        .sheet(isPresented: $showAddComicsSheet) {
            addComicsSheet.leafAppearance(settings.theme)
        }
        .sheet(item: $selectedRemote) { book in
            NavigationStack {
                JellyfinDetailView(item: book, model: jellyfin, creatorLibraryID: catalogue.libraryID,
                    creatorCachedBooks: catalogue.books).environmentObject(library)
                    .toolbar { ToolbarItem(placement: .cancellationAction) {
                        Button("Close") { selectedRemote = nil }
                    } }
            }
        }
        .task(id: downloadedOnly ? covers.revisions(for: library.comics) : []) {
            if downloadedOnly, let readable = try? await LibraryBrowseState.readableCopies(library.comics) {
                readableCopies = readable
            }
        }
        .alert("Rename Reading List", isPresented: $showRenameAlert) {
            TextField("List Name", text: $renameInput)
            Button("Cancel", role: .cancel) {}
            Button("Save") {
                let trimmed = renameInput.trimmingCharacters(in: .whitespacesAndNewlines)
                if !trimmed.isEmpty {
                    library.renameReadingList(id: listId, newName: trimmed)
                }
            }
        }
        .alert("Delete Reading List?", isPresented: $showDeleteListAlert) {
            Button("Cancel", role: .cancel) {}
            Button("Delete", role: .destructive) {
                library.deleteReadingList(id: listId)
                onDismiss()
            }
        } message: {
            Text("Delete “\(currentList?.name ?? String(localized: "Reading List"))”? The comics in this list will remain in your library.")
        }
        .alert("Remove from Reading List?", isPresented: Binding(
            get: { comicToRemove != nil },
            set: { if !$0 { comicToRemove = nil } }
        )) {
            Button("Cancel", role: .cancel) { comicToRemove = nil }
            Button("Remove", role: .destructive) {
                if let member = comicToRemove {
                    ReadingListStore.removeComic(from: listId, filename: member)
                    library.refresh()
                }
                comicToRemove = nil
            }
        } message: {
            let comicTitle = allMembers.first { $0.id == comicToRemove }?.title ?? String(localized: "this comic")
            Text("Remove “\(comicTitle)” from “\(currentList?.name ?? String(localized: "Reading List"))”? The comic file will stay in your library.")
        }
    }

    private var appBar: some View {
        HStack(spacing: 14) {
            Button { onDismiss() } label: {
                Image(systemName: "chevron.left")
                    .font(.system(size: 16, weight: .bold))
                    .foregroundColor(Leaf.cream)
                    .frame(width: 38, height: 38)
                    .background(Leaf.cream.opacity(0.14))
                    .clipShape(Circle())
                    .frame(width: 44, height: 44)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Back")

            VStack(alignment: .leading, spacing: 1) {
                Text(currentList?.name ?? String(localized: "Reading List"))
                    .font(.custom("Anton-Regular", size: 18, relativeTo: .headline))
                    .foregroundColor(Leaf.cream)
                    .lineLimit(1)
                Text("CUSTOM STORYLINE / READING LIST")
                    .leafControlFont(11, weight: 600)
                    .foregroundColor(Leaf.creamMuted)
                    .lineLimit(1)
            }

            Spacer()

            HStack(spacing: 4) {
                UnreadFilterButton(isOn: $unreadOnly, onBrand: true)
                Button {
                    renameInput = currentList?.name ?? ""
                    showRenameAlert = true
                } label: {
                    Image(systemName: "pencil")
                        .font(.system(size: 15, weight: .bold))
                        .foregroundColor(Leaf.cream)
                        .frame(width: 36, height: 36)
                        .background(Leaf.cream.opacity(0.14))
                        .clipShape(Circle())
                        .frame(width: 44, height: 44)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Rename Reading List")

                Button {
                    showDeleteListAlert = true
                } label: {
                    Image(systemName: "trash")
                        .font(.system(size: 15, weight: .bold))
                        .foregroundColor(Leaf.cream)
                        .frame(width: 36, height: 36)
                        .background(Leaf.cream.opacity(0.14))
                        .clipShape(Circle())
                        .frame(width: 44, height: 44)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Delete Reading List")
            }
        }
        .padding(.horizontal, 18)
        .padding(.top, 12)
        .padding(.bottom, 12)
        .background(Leaf.leafPrimary.ignoresSafeArea(edges: .top))
    }

    private func content(for width: CGFloat) -> some View {
        let cols = max(2, min(settings.libraryColumns, width >= 600 ? 4 : (width >= 380 ? 3 : 2)))
        let spacing: CGFloat = cols >= 4 ? 12 : 16
        let gridColumns = Array(repeating: GridItem(.flexible(), spacing: spacing, alignment: .top), count: cols)

        return ScrollView {
            VStack(spacing: 16) {
                UnreadFilterChip(isOn: $unreadOnly)
                if allMembers.contains(where: { JellyfinListReference(token: $0.id) != nil }) {
                    Toggle("Downloaded only", isOn: $downloadedOnly)
                }
                heroCard
                if let next = nextUnread, next.url == nil && next.book == nil && next.linked == nil {
                    Text("The list keeps this item. Reconnect its account or restore the local copy to read it.")
                        .font(.footnote).foregroundStyle(.secondary)
                }
                if allMembers.isEmpty {
                    emptyStateCard
                } else {
                    LazyVGrid(columns: gridColumns, spacing: cols >= 4 ? 14 : 18) {
                        ForEach(Array(members.enumerated()).filter {
                            LibraryBrowseState.includes(completed: $0.element.completed, unreadOnly: unreadOnly) &&
                            LibraryBrowseState.matches(searchQuery, values: [$0.element.title, currentList?.name ?? ""])
                        }, id: \.element.id) { index, member in
                            let order = (currentList?.itemFilenames.firstIndex(of: member.id) ?? index) + 1
                            Group {
                                if let url = member.url {
                                    ReadingListComicCard(url: url, sequenceNumber: order, totalCount: allMembers.count,
                                        metadata: library.metadata[url.lastPathComponent], progress: ReadingProgress.get(url),
                                        columns: cols, coverTransition: coverTransition,
                                        onOpen: { open(member) }, onRemove: { comicToRemove = member.id },
                                        onMoveUp: order > 1 ? { move(member, direction: -1) } : nil,
                                        onMoveDown: order < allMembers.count ? { move(member, direction: 1) } : nil)
                                    .contextMenu {
                                        Button("Read Comic") { open(member) }
                                        Button(ReadingProgress.isCompleted(url) ? "Mark as Unread" : "Mark as Read") {
                                            library.markComicsRead(urls: [url], read: !ReadingProgress.isCompleted(url))
                                        }
                                        Button("Move Up in Reading Order") { move(member, direction: -1) }.disabled(order == 1)
                                        Button("Move Down in Reading Order") { move(member, direction: 1) }.disabled(order == allMembers.count)
                                        Button("Remove from Reading List", role: .destructive) { comicToRemove = member.id }
                                    }
                                } else {
                                    remoteCard(member, order: order, columns: cols)
                                }
                            }
                            .accessibilityIdentifier("readingList.member." + member.id)
                        }
                    }
                }
            }
            .padding(.horizontal, 18)
            .padding(.top, 16)
            .padding(.bottom, 88)
        }
    }

    private func remoteCard(_ member: ReadingListMember, order: Int, columns: Int) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            ZStack(alignment: .topLeading) {
                Button { open(member) } label: {
                    if let comic = member.linked, let linkedFiles {
                        LinkedFileCard(comic: comic, store: linkedFiles, columns: columns)
                    } else if let book = member.book {
                        JellyfinComicCard(book: book, browser: jellyfin, columns: columns)
                    } else {
                        VStack(alignment: .leading, spacing: 6) {
                            GeneratedCover(title: member.title, columns: columns).aspectRatio(0.7, contentMode: .fit)
                            Text(member.title).leafControlFont(12, weight: 800)
                            Text("The list keeps this item. Reconnect its account or restore the local copy to read it.")
                                .font(.caption).foregroundStyle(.secondary)
                        }
                    }
                }
                .buttonStyle(.plain)
                .disabled(member.book == nil && member.linked == nil)
                HStack(alignment: .top) {
                    Text("#\(order)")
                        .font(.anton(columns >= 4 ? 8.5 : 10)).foregroundStyle(Leaf.cream)
                        .padding(.horizontal, 6).padding(.vertical, 2)
                        .background(Leaf.ink.opacity(0.88), in: RoundedCornerShape(cornerRadius: 3))
                    Spacer()
                    Button { comicToRemove = member.id } label: {
                        Image(systemName: "xmark").font(.system(size: 11, weight: .bold))
                            .foregroundStyle(Leaf.crimson).frame(width: 22, height: 22)
                            .background(Leaf.deepInk.opacity(0.85), in: Circle())
                            .frame(width: 44, height: 44).contentShape(Rectangle())
                    }
                    .buttonStyle(.plain).accessibilityLabel("Remove from reading list")
                }
                .padding(6)
            }
            if order > 1 || order < allMembers.count {
                LeafAdaptiveControls(spacing: 4) {
                    if order > 1 {
                        Button { move(member, direction: -1) } label: {
                            Image(systemName: "arrow.up").font(.system(size: 10, weight: .bold))
                                .foregroundStyle(Leaf.cream).frame(width: 22, height: 22)
                                .background(Leaf.deepInk.opacity(0.85), in: Circle())
                                .frame(width: 44, height: 44).contentShape(Rectangle())
                        }.buttonStyle(.plain).accessibilityLabel("Move earlier in reading order")
                    }
                    if order < allMembers.count {
                        Button { move(member, direction: 1) } label: {
                            Image(systemName: "arrow.down").font(.system(size: 10, weight: .bold))
                                .foregroundStyle(Leaf.cream).frame(width: 22, height: 22)
                                .background(Leaf.deepInk.opacity(0.85), in: Circle())
                                .frame(width: 44, height: 44).contentShape(Rectangle())
                        }.buttonStyle(.plain).accessibilityLabel("Move later in reading order")
                    }
                }
                .frame(maxWidth: .infinity, alignment: .trailing)
            }
        }
    }

    private var heroCard: some View {
        let badgeColor = allRead ? BookStatusBadgeIcon.completedGreen : Leaf.ochre
        let progressKnown = members.allSatisfy { $0.completed != nil }
        let statusText = members.isEmpty ? String(localized: "0 COMICS")
            : progressKnown ? String(localized: "\(completedCount) OF \(members.count) READ")
            : String(localized: "\(members.count) comics")
        let progressFraction = members.isEmpty ? 0.0 : Double(completedCount) / Double(members.count)

        return LeafAdaptiveControls(spacing: 12) {
            // Left: Unified Read Badge + Slim Progress Bar
            VStack(alignment: .leading, spacing: 6) {
                Text(statusText)
                    .font(.custom("Anton-Regular", size: 12, relativeTo: .headline))
                    .tracking(0.5)
                    .foregroundColor(badgeColor)
                    .lineLimit(1)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 2)
                    .background(Leaf.ink.opacity(0.6))
                    .clipShape(RoundedCornerShape(cornerRadius: 4))
                    .overlay(
                        RoundedCornerShape(cornerRadius: 4)
                            .stroke(badgeColor.opacity(0.55), lineWidth: 1)
                    )

                if !members.isEmpty && progressKnown {
                    GeometryReader { geo in
                        ZStack(alignment: .leading) {
                            RoundedCornerShape(cornerRadius: 1.5)
                                .fill(Leaf.inkSoft.opacity(0.25))
                            RoundedCornerShape(cornerRadius: 1.5)
                                .fill(badgeColor)
                                .frame(width: geo.size.width * CGFloat(min(max(progressFraction, 0), 1)))
                        }
                    }
                    .frame(height: 3)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            // Right: Book Status Toggle + Compact Start/Continue/Restart Pill
            if !members.isEmpty {
                HStack(spacing: 8) {
                    if members.allSatisfy({ $0.url != nil || $0.linked != nil }) {
                    Button {
                        let read = !allRead
                        for comic in members.compactMap(\.linked) { linkedFiles?.markRead(comic, read: read) }
                        library.markReadingListRead(listId: listId, read: read)
                    } label: {
                        BookStatusBadgeIcon(isAllCompleted: allRead)
                            .frame(width: 36, height: 36)
                            .background(settings.groundSoft)
                            .clipShape(RoundedCornerShape(cornerRadius: 8))
                            .overlay(
                                RoundedCornerShape(cornerRadius: 8)
                                    .stroke(
                                        allRead ? BookStatusBadgeIcon.unreadRose.opacity(0.6) : Leaf.secondaryText.opacity(0.35),
                                        lineWidth: 1
                                    )
                            )
                            .frame(width: 44, height: 44)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)

                    }
                    if let target = nextUnread {
                        Button {
                            open(target)
                        } label: {
                            HStack(spacing: 4) {
                                Image(systemName: "play.fill")
                                    .font(.system(size: 12, weight: .bold))
                                Text(shortActionLabel)
                                    .font(.custom("Anton-Regular", size: 13, relativeTo: .headline))
                                    .tracking(0.6)
                                    .fixedSize(horizontal: false, vertical: true)
                            }
                            .foregroundColor(Leaf.ink)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 8)
                            .frame(minHeight: 44)
                            .background(Leaf.ochre)
                            .clipShape(RoundedCornerShape(cornerRadius: 8))
                            .overlay(
                                RoundedCornerShape(cornerRadius: 8)
                                    .stroke(Leaf.ink, lineWidth: 1.5)
                            )
                        }
                        .buttonStyle(.plain)
                        .disabled(target.url == nil && target.book == nil && target.linked == nil)
                        .accessibilityLabel(heroButtonText)
                    }
                }
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 10)
        .background(settings.groundSoft)
        .clipShape(RoundedCornerShape(cornerRadius: 10))
        .overlay(
            RoundedCornerShape(cornerRadius: 10)
                .stroke(Leaf.inkSoft.opacity(0.28), lineWidth: 1)
        )
        .comicShadow(offset: 4, color: .black.opacity(0.75), corner: 10)
    }

    private var emptyStateCard: some View {
        VStack(spacing: 10) {
            Text("THIS LIST IS EMPTY")
                .font(.custom("Anton-Regular", size: 16, relativeTo: .headline))
                .foregroundColor(Leaf.text.opacity(0.75))
            Text("Tap + ADD COMICS in the bottom corner to curate your reading order.")
                .leafControlFont(12, weight: 500)
                .foregroundColor(Leaf.secondaryText)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 40)
    }

    private var addComicsSheet: some View {
        NavigationStack {
            let q = addSearchQuery.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
            let availableComics = ReadingListMember.choices(local: library.comics, metadata: library.metadata,
                books: catalogue.isCurrent(for: jellyfin.connection) ? catalogue.books : [],
                connection: jellyfin.connection, excluding: currentList?.itemFilenames ?? [], query: q,
                linked: downloadedOnly ? [] : linkedFiles?.comics ?? [])
            ZStack {
                settings.ground.ignoresSafeArea()
                VStack(spacing: 0) {
                    // Live search field matching Android's "SEARCH BY TITLE OR SERIES..."
                    HStack(spacing: 8) {
                        Image(systemName: "magnifyingglass")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundColor(addSearchQuery.isEmpty ? Leaf.secondaryText : Leaf.goldText)
                        TextField("SEARCH BY TITLE OR SERIES…", text: $addSearchQuery)
                            .leafControlFont(13, weight: 600)
                            .foregroundColor(Leaf.text)
                            .tint(Leaf.goldText)
                            .textInputAutocapitalization(.characters)
                            .disableAutocorrection(true)
                        if !addSearchQuery.isEmpty {
                            Button { addSearchQuery = "" } label: {
                                Image(systemName: "xmark.circle.fill")
                                    .foregroundColor(Leaf.secondaryText)
                                    .frame(width: 44, height: 44)
                                    .contentShape(Rectangle())
                                    .accessibilityLabel("CLEAR SEARCH")
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .padding(10)
                    .background(settings.groundSoft)
                    .clipShape(RoundedCornerShape(cornerRadius: 6))
                    .overlay(
                        RoundedCornerShape(cornerRadius: 6)
                            .stroke(addSearchQuery.isEmpty ? Leaf.inkSoft : Leaf.ochre, lineWidth: 1.5)
                    )
                    .padding(.horizontal, 16)
                    .padding(.vertical, 10)

                    if catalogue.loading { ProgressView("Loading Jellyfin…") }
                    if jellyfin.connection != nil && !catalogue.indexComplete {
                        Text("Only loaded catalogue comics are shown.").font(.footnote).foregroundStyle(.secondary)
                    }
                    if availableComics.isEmpty {
                        VStack(spacing: 8) {
                            Text(q.isEmpty ? String(localized: "No more comics in the loaded library.") : String(localized: "No comics matching “\(addSearchQuery)”"))
                                .leafControlFont(13)
                                .foregroundColor(Leaf.secondaryText)
                        }
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                    } else {
                        List(availableComics) { member in
                            let isSelected = selectedToAdd.contains(member.id)
                            Button {
                                if isSelected { selectedToAdd.remove(member.id) }
                                else { selectedToAdd.insert(member.id) }
                            } label: {
                                HStack(spacing: 12) {
                                    if let url = member.url { SmallCoverThumb(url: url) }
                                    else if let comic = member.linked, let linkedFiles {
                                        LinkedCoverThumb(comic: comic, store: linkedFiles)
                                    } else { Image(systemName: "book.closed").frame(width: 44, height: 60).accessibilityHidden(true) }
                                    VStack(alignment: .leading, spacing: 3) {
                                        Text(member.title).leafControlFont(13, weight: 700).foregroundStyle(Leaf.text).lineLimit(2)
                                        if member.linked != nil {
                                            Text("Files").leafControlFont(11).foregroundStyle(Leaf.goldText)
                                        } else if let book = member.book {
                                            Text(book.seriesName ?? String(localized: "Jellyfin"))
                                                .leafControlFont(11).foregroundStyle(Leaf.secondaryText).lineLimit(1)
                                        }
                                    }
                                    Spacer()
                                    Image(systemName: isSelected ? "checkmark.circle.fill" : "circle")
                                        .font(.system(size: 18)).foregroundStyle(isSelected ? Leaf.goldText : Leaf.secondaryText)
                                }
                                .frame(minHeight: 60).contentShape(Rectangle())
                            }
                            .accessibilityIdentifier("readingList.choice." + member.id)
                            .accessibilityValue(isSelected ? Text("Selected") : Text("Not selected"))
                            .listRowBackground(settings.groundSoft)
                        }
                        .listStyle(.plain)
                    }
                }
            }
            .navigationTitle("Add Comics to List")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { showAddComicsSheet = false }
                        .foregroundColor(Leaf.goldText)
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Add (\(selectedToAdd.count))") {
                        let orderedSelection = ReadingListMember.choices(local: library.comics, metadata: library.metadata,
                            books: catalogue.isCurrent(for: jellyfin.connection) ? catalogue.books : [],
                            connection: jellyfin.connection, excluding: currentList?.itemFilenames ?? [], query: "",
                            linked: downloadedOnly ? [] : linkedFiles?.comics ?? [])
                            .filter { selectedToAdd.contains($0.id) }.map(\.id)
                        ReadingListStore.addComics(to: listId, filenames: orderedSelection)
                        library.refresh()
                        showAddComicsSheet = false
                    }
                    .foregroundColor(selectedToAdd.isEmpty ? Leaf.secondaryText : Leaf.goldText)
                    .disabled(selectedToAdd.isEmpty)
                }
            }
        }
    }
}

/// Comic card inside `ReadingListDetailView` showing sequence badge (#1, #2),
/// top-right quick-remove (x), and bottom-right Move Up / Move Down arrows.
struct ReadingListComicCard: View {
    @ObservedObject private var covers = CoverLoader.shared
    @EnvironmentObject private var settings: LeafSettings
    let url: URL
    let sequenceNumber: Int
    let totalCount: Int
    let metadata: ComicMetadataRecord?
    let progress: Progress?
    var columns: Int = 2
    var coverTransition: Namespace.ID? = nil
    let onOpen: () -> Void
    let onRemove: () -> Void
    let onMoveUp: (() -> Void)?
    let onMoveDown: (() -> Void)?

    @State private var coverPixels = 0
    @State private var cover: UIImage?
    @State private var pages = 0

    private var displayTitle: String {
        metadata?.title ?? url.deletingPathExtension().lastPathComponent
    }

    private var lastPage: Int { progress?.page ?? 0 }
    private var pageCount: Int { progress?.total ?? pages }
    private var started: Bool { lastPage > 0 }
    private var progressFraction: Double {
        guard pageCount > 0 else { return 0 }
        if progress?.isCompleted == true { return 1.0 }
        return min(max(Double(lastPage + 1) / Double(pageCount), 0), 1)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            ZStack(alignment: .bottom) {
                Group {
                    if let cover {
                        LibraryCoverArtwork(image: cover)
                    } else {
                        GeneratedCover(
                            title: url.deletingPathExtension().lastPathComponent,
                            columns: columns
                        )
                    }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .clipped()

                // Top-left sequence badge (#1, #2, ...) and top-right quick remove (x)
                VStack {
                    HStack(alignment: .top) {
                        Text("#\(sequenceNumber)")
                            .font(.anton(columns >= 4 ? 8.5 : 10))
                            .foregroundColor(Leaf.cream)
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2)
                            .background(Leaf.ink.opacity(0.88))
                            .clipShape(RoundedCornerShape(cornerRadius: 3))
                            .overlay(
                                RoundedCornerShape(cornerRadius: 3)
                                    .stroke(Leaf.ochre, lineWidth: 1)
                            )

                        Spacer()

                        Button(action: onRemove) {
                            Image(systemName: "xmark")
                                .font(.system(size: 11, weight: .bold))
                                .foregroundColor(Leaf.crimson)
                                .frame(width: 22, height: 22)
                                .background(Leaf.deepInk.opacity(0.85))
                                .clipShape(Circle())
                                .frame(width: 44, height: 44)
                                .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel("Remove from reading list")
                    }
                    Spacer()
                }
                .padding(6)

                // Bottom progress bar
                GeometryReader { geo in
                    ZStack(alignment: .leading) {
                        Leaf.ink
                        Leaf.ochre.frame(width: started ? geo.size.width * CGFloat(progressFraction) : 0)
                    }
                }
                .frame(height: columns >= 4 ? 5 : 7)
            }
            .aspectRatio(0.7, contentMode: .fit)
            .background(settings.groundSoft)
            .clipShape(RoundedCornerShape(cornerRadius: 4))
            .comicCoverSource(url: url, namespace: coverTransition)
            .overlay(RoundedCornerShape(cornerRadius: 4).stroke(Color.clear, lineWidth: columns >= 4 ? 2 : 3))
            .shadow(color: .black.opacity(0.24), radius: columns >= 4 ? 3 : 5, y: 3)
            .contentShape(Rectangle())
            .onTapGesture {
                onOpen()
            }

            // Keep reorder targets separate from the cover in dense grids.
            if onMoveUp != nil || onMoveDown != nil {
                LeafAdaptiveControls(spacing: 4) {
                    if let onMoveUp {
                        Button(action: onMoveUp) {
                            Image(systemName: "arrow.up")
                                .font(.system(size: 10, weight: .bold))
                                .foregroundColor(Leaf.cream)
                                .frame(width: 22, height: 22)
                                .background(Leaf.deepInk.opacity(0.85))
                                .clipShape(Circle())
                                .frame(width: 44, height: 44)
                                .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel("Move earlier in reading order")
                    }
                    if let onMoveDown {
                        Button(action: onMoveDown) {
                            Image(systemName: "arrow.down")
                                .font(.system(size: 10, weight: .bold))
                                .foregroundColor(Leaf.cream)
                                .frame(width: 22, height: 22)
                                .background(Leaf.deepInk.opacity(0.85))
                                .clipShape(Circle())
                                .frame(width: 44, height: 44)
                                .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel("Move later in reading order")
                    }
                }
                .frame(maxWidth: .infinity, alignment: .trailing)
            }

            Text(displayTitle)
                .font(.archivo(columns >= 4 ? 10 : 12, weight: 800))
                .foregroundColor(Leaf.text)
                .lineLimit(2)
                .padding(.top, columns >= 4 ? 6 : 9)

            Text(started
                 ? String(localized: "\(progressFraction.formatted(.percent.precision(.fractionLength(0)))) · page \(lastPage + 1) of \(pageCount)")
                 : String(localized: "\(pageCount) pages"))
                .font(.archivo(columns >= 4 ? 8.5 : 9.5, weight: 500))
                .foregroundColor(started ? Leaf.goldText : Leaf.secondaryText)
                .lineLimit(1)
                .padding(.top, 2)
        }
        .libraryCoverResolution($coverPixels)
        .task(id: StackCoverRequest(keys: covers.revisions(for: [url]).map { .local($0) }, pixels: coverPixels)) {
            guard coverPixels > 0 else { return }
            let loaded = await CoverLoader.load(for: url, maxPixel: coverPixels)
            guard !Task.isCancelled else { return }
            cover = loaded.cover
            pages = loaded.pageCount
            ReadingProgress.resyncTotalIfNeeded(url, actualTotal: loaded.pageCount)
        }
    }
}
