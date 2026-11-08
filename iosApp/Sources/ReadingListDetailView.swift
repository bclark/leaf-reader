import SwiftUI

/// Dedicated screen for a custom reading list, matching Android's ReadingListDetailScreen.kt.
/// Uses a compact single-row Hero Status Bar with unified read-progress badge, BookStatusBadgeIcon,
/// compact START / CONTINUE / RESTART pill, and a pinned bottom-right "+ ADD COMICS" FAB.
struct ReadingListDetailView: View {
    let listId: String
    @ObservedObject var library: LibraryStore
    let onDismiss: () -> Void
    var initialShowAddComics: Bool = false

    @Namespace private var coverTransition
    @State private var openComic: OpenComic?
    @EnvironmentObject private var settings: LeafSettings
    @State private var showAddComicsSheet = false
    @State private var selectedToAdd: Set<URL> = []
    @State private var addSearchQuery = ""
    @State private var showRenameAlert = false
    @State private var renameInput = ""
    @State private var showDeleteListAlert = false
    @State private var comicToRemove: URL?

    private var currentList: ReadingListRecord? {
        library.readingLists.first { $0.id == listId }
    }

    private var listComics: [URL] {
        guard let list = currentList else { return [] }
        let map = Dictionary(uniqueKeysWithValues: library.comics.map { ($0.lastPathComponent, $0) })
        return list.itemFilenames.compactMap { map[$0] }
    }

    private var completedCount: Int {
        listComics.filter { ReadingProgress.isCompleted($0) }.count
    }

    private var allRead: Bool {
        guard !listComics.isEmpty else { return false }
        return completedCount == listComics.count
    }

    private var nextUnread: URL? {
        listComics.first { !ReadingProgress.isCompleted($0) } ?? listComics.first
    }

    var heroButtonText: String {
        guard let target = nextUnread else { return String(localized: "START") }
        let title = (library.metadata[target.lastPathComponent]?.title ?? target.deletingPathExtension().lastPathComponent)
        if allRead {
            let firstTitle = (listComics.first.flatMap { library.metadata[$0.lastPathComponent]?.title ?? $0.deletingPathExtension().lastPathComponent } ?? title)
            return String(localized: "READ FROM START: \(firstTitle)")
        }
        let started = (ReadingProgress.get(target)?.page ?? 0) > 0
        return started ? String(localized: "CONTINUE: \(title)") : String(localized: "START: \(title)")
    }

    /// Compact action pill label matching Android's redesigned hero card (`START`, `CONTINUE`, `RESTART`).
    var shortActionLabel: String {
        guard let target = nextUnread else { return String(localized: "START") }
        if allRead { return String(localized: "RESTART") }
        let started = (ReadingProgress.get(target)?.page ?? 0) > 0 || completedCount > 0
        return started ? String(localized: "CONTINUE") : String(localized: "START")
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
                                .font(.anton(15))
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
        .onAppear {
            if initialShowAddComics {
                showAddComicsSheet = true
            }
        }
        .sheet(isPresented: $showAddComicsSheet) {
            addComicsSheet.leafAppearance(settings.theme)
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
                if let url = comicToRemove {
                    library.removeComicFromReadingList(listId: listId, url: url)
                }
                comicToRemove = nil
            }
        } message: {
            let comicTitle = comicToRemove?.deletingPathExtension().lastPathComponent ?? String(localized: "this comic")
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
            }
            .buttonStyle(.plain)

            VStack(alignment: .leading, spacing: 1) {
                Text(currentList?.name ?? String(localized: "Reading List"))
                    .font(.anton(18))
                    .foregroundColor(Leaf.cream)
                    .lineLimit(1)
                Text("CUSTOM STORYLINE / READING LIST")
                    .font(.archivo(11, weight: 600))
                    .foregroundColor(Leaf.creamMuted)
                    .lineLimit(1)
            }

            Spacer()

            HStack(spacing: 8) {
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
        let gridColumns = Array(repeating: GridItem(.flexible(), spacing: spacing), count: cols)

        return ScrollView {
            VStack(spacing: 16) {
                heroCard

                if listComics.isEmpty {
                    emptyStateCard
                } else {
                    LazyVGrid(columns: gridColumns, spacing: cols >= 4 ? 14 : 18) {
                        ForEach(Array(listComics.enumerated()), id: \.element) { index, url in
                            let isRead = ReadingProgress.isCompleted(url)
                            let lastPage = ReadingProgress.get(url)?.page ?? 0
                            ReadingListComicCard(
                                url: url,
                                sequenceNumber: index + 1,
                                totalCount: listComics.count,
                                metadata: library.metadata[url.lastPathComponent],
                                progress: ReadingProgress.get(url),
                                columns: cols,
                                coverTransition: coverTransition,
                                onOpen: { openComic = OpenComic(url: url) },
                                onRemove: { comicToRemove = url },
                                onMoveUp: index > 0 ? {
                                    withAnimation(.easeInOut(duration: 0.18)) {
                                        library.moveComicInReadingList(listId: listId, url: url, direction: -1)
                                    }
                                } : nil,
                                onMoveDown: index < listComics.count - 1 ? {
                                    withAnimation(.easeInOut(duration: 0.18)) {
                                        library.moveComicInReadingList(listId: listId, url: url, direction: 1)
                                    }
                                } : nil
                            )
                            .contextMenu {
                                Button {
                                    openComic = OpenComic(url: url)
                                } label: {
                                    Label(
                                        lastPage > 0 ? String(localized: "Continue Reading (pg \(lastPage + 1))") : String(localized: "Read Comic"),
                                        systemImage: "play.fill"
                                    )
                                }
                                Button {
                                    library.markComicsRead(urls: [url], read: !isRead)
                                } label: {
                                    Label(isRead ? String(localized: "Mark as Unread") : String(localized: "Mark as Read"), systemImage: "checkmark.circle")
                                }
                                if index > 0 {
                                    Button {
                                        withAnimation(.easeInOut(duration: 0.18)) {
                                            library.moveComicInReadingList(listId: listId, url: url, direction: -1)
                                        }
                                    } label: {
                                        Label("Move Up in Reading Order", systemImage: "arrow.up")
                                    }
                                }
                                if index < listComics.count - 1 {
                                    Button {
                                        withAnimation(.easeInOut(duration: 0.18)) {
                                            library.moveComicInReadingList(listId: listId, url: url, direction: 1)
                                        }
                                    } label: {
                                        Label("Move Down in Reading Order", systemImage: "arrow.down")
                                    }
                                }
                                Button(role: .destructive) {
                                    comicToRemove = url
                                } label: {
                                    Label("Remove from Reading List", systemImage: "trash")
                                }
                            }
                        }
                    }
                }
            }
            .padding(.horizontal, 18)
            .padding(.top, 16)
            .padding(.bottom, 88)
        }
    }

    private var heroCard: some View {
        let badgeColor = allRead ? BookStatusBadgeIcon.completedGreen : Leaf.ochre
        let statusText = listComics.isEmpty
            ? String(localized: "0 COMICS")
            : String(localized: "\(completedCount) OF \(listComics.count) READ")
        let progressFraction = listComics.isEmpty ? 0.0 : Double(completedCount) / Double(listComics.count)

        return HStack(spacing: 12) {
            // Left: Unified Read Badge + Slim Progress Bar
            VStack(alignment: .leading, spacing: 6) {
                Text(statusText)
                    .font(.anton(12))
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

                if !listComics.isEmpty {
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
            if !listComics.isEmpty {
                HStack(spacing: 8) {
                    Button {
                        library.markReadingListRead(listId: listId, read: !allRead)
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
                    }
                    .buttonStyle(.plain)

                    if let target = allRead ? listComics.first : nextUnread {
                        Button {
                            openComic = OpenComic(url: target)
                        } label: {
                            HStack(spacing: 4) {
                                Image(systemName: "play.fill")
                                    .font(.system(size: 12, weight: .bold))
                                Text(shortActionLabel)
                                    .font(.anton(13))
                                    .tracking(0.6)
                                    .lineLimit(1)
                            }
                            .foregroundColor(Leaf.ink)
                            .padding(.horizontal, 12)
                            .frame(height: 36)
                            .background(Leaf.ochre)
                            .clipShape(RoundedCornerShape(cornerRadius: 8))
                            .overlay(
                                RoundedCornerShape(cornerRadius: 8)
                                    .stroke(Leaf.ink, lineWidth: 1.5)
                            )
                        }
                        .buttonStyle(.plain)
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
                .font(.anton(16))
                .foregroundColor(Leaf.text.opacity(0.75))
            Text("Tap + ADD COMICS in the bottom corner to curate your reading order.")
                .font(.archivo(12, weight: 500))
                .foregroundColor(Leaf.secondaryText)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 40)
    }

    private var addComicsSheet: some View {
        NavigationStack {
            let q = addSearchQuery.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
            let availableComics = library.comics.filter { url in
                guard !listComics.contains(url) else { return false }
                if q.isEmpty { return true }
                let title = url.deletingPathExtension().lastPathComponent.lowercased()
                let series = library.metadata[url.lastPathComponent]?.series?.lowercased() ?? ""
                return title.contains(q) || series.contains(q)
            }
            ZStack {
                settings.ground.ignoresSafeArea()
                VStack(spacing: 0) {
                    // Live search field matching Android's "SEARCH BY TITLE OR SERIES..."
                    HStack(spacing: 8) {
                        Image(systemName: "magnifyingglass")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundColor(addSearchQuery.isEmpty ? Leaf.secondaryText : Leaf.goldText)
                        TextField("SEARCH BY TITLE OR SERIES…", text: $addSearchQuery)
                            .font(.archivo(13, weight: 600))
                            .foregroundColor(Leaf.text)
                            .tint(Leaf.goldText)
                            .textInputAutocapitalization(.characters)
                            .disableAutocorrection(true)
                        if !addSearchQuery.isEmpty {
                            Button { addSearchQuery = "" } label: {
                                Image(systemName: "xmark.circle.fill")
                                    .foregroundColor(Leaf.secondaryText)
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

                    if availableComics.isEmpty {
                        VStack(spacing: 8) {
                            Text(q.isEmpty ? String(localized: "All library comics are already in this reading list.") : String(localized: "No comics matching “\(addSearchQuery)”"))
                                .font(.archivo(13))
                                .foregroundColor(Leaf.secondaryText)
                        }
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                    } else {
                        List(availableComics, id: \.self) { url in
                            let isSelected = selectedToAdd.contains(url)
                            Button {
                                if isSelected { selectedToAdd.remove(url) }
                                else { selectedToAdd.insert(url) }
                            } label: {
                                HStack(spacing: 12) {
                                    SmallCoverThumb(url: url)
                                    VStack(alignment: .leading, spacing: 3) {
                                        Text(url.deletingPathExtension().lastPathComponent)
                                            .font(.archivo(13, weight: 700))
                                            .foregroundColor(Leaf.text)
                                            .lineLimit(1)
                                        if let s = library.metadata[url.lastPathComponent]?.series, !s.isEmpty {
                                            let issuePart: String = {
                                                if let n = library.metadata[url.lastPathComponent]?.issueNumber {
                                                    return " #\(n.cleanNumber)"
                                                }
                                                return ""
                                            }()
                                            Text("\(s)\(issuePart)")
                                                .font(.archivo(11))
                                                .foregroundColor(Leaf.secondaryText)
                                                .lineLimit(1)
                                        }
                                    }
                                    Spacer()
                                    Image(systemName: isSelected ? "checkmark.circle.fill" : "circle")
                                        .font(.system(size: 18))
                                        .foregroundColor(isSelected ? Leaf.goldText : Leaf.secondaryText)
                                }
                            }
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
                        let orderedSelection = library.comics.filter { selectedToAdd.contains($0) }
                        library.addComicsToReadingList(listId: listId, urls: orderedSelection)
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
                        Image(uiImage: cover)
                            .resizable()
                            .scaledToFill()
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
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel("Remove from reading list")
                    }
                    Spacer()
                }
                .padding(6)

                // Quick Move Up / Move Down buttons at bottom-right above the progress bar
                if onMoveUp != nil || onMoveDown != nil {
                    HStack(spacing: 4) {
                        Spacer()
                        if let onMoveUp {
                            Button(action: onMoveUp) {
                                Image(systemName: "arrow.up")
                                    .font(.system(size: 10, weight: .bold))
                                    .foregroundColor(Leaf.cream)
                                    .frame(width: 22, height: 22)
                                    .background(Leaf.deepInk.opacity(0.85))
                                    .clipShape(Circle())
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
                            }
                            .buttonStyle(.plain)
                            .accessibilityLabel("Move later in reading order")
                        }
                    }
                    .padding(.trailing, 6)
                    .padding(.bottom, columns >= 4 ? 10 : 12)
                }

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
        .task {
            let loaded = await CoverLoader.load(for: url)
            cover = loaded.cover
            pages = loaded.pageCount
            ReadingProgress.resyncTotalIfNeeded(url, actualTotal: loaded.pageCount)
        }
    }
}
