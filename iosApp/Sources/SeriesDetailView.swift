import SwiftUI

/// Dedicated screen for a series stack, matching Android's SeriesDetailScreen.kt.
/// Uses a compact single-row Hero Status Bar with unified read-progress badge, BookStatusBadgeIcon,
/// compact START / CONTINUE / RESTART pill, and a pinned bottom-right "+ ADD COMICS" FAB.
struct SeriesDetailView: View {
    let seriesName: String
    let volume: String?
    @ObservedObject var library: LibraryStore
    var linkedFiles: LinkedFilesStore? = nil
    let onDismiss: () -> Void
    @Binding var unreadOnly: Bool
    var searchQuery = ""
    var availableCopies: Set<URL>? = nil

    @Namespace private var coverTransition
    @State private var openComic: OpenComic?
    @State private var openLinked: LinkedComic?
    @State private var seriesError: String?
    @EnvironmentObject private var settings: LeafSettings
    @State private var showAddComicsSheet = false
    @State private var selectedToAdd: Set<URL> = []
    @State private var addSearchQuery = ""
    @State private var comicToRemove: URL?

    private var visibleLinks: [LinkedComic] { availableCopies == nil ? linkedFiles?.comics ?? [] : [] }

    private var seriesComics: [URL] {
        library.comics(inSeries: seriesName, linked: visibleLinks).filter { availableCopies?.contains($0) ?? true }
    }

    private func metadata(for url: URL) -> ComicMetadataRecord? {
        linkedFiles?.comic(for: url)?.shelfMetadata ?? library.metadata[url.lastPathComponent]
    }

    private func title(for url: URL) -> String {
        linkedFiles?.comic(for: url)?.title ?? metadata(for: url)?.title ?? url.deletingPathExtension().lastPathComponent
    }

    private func open(_ url: URL) {
        if let comic = linkedFiles?.comic(for: url) { openLinked = comic }
        else if url.isFileURL { openComic = OpenComic(url: url) }
    }

    private func mark(_ urls: [URL], read: Bool) {
        for url in urls {
            if let comic = linkedFiles?.comic(for: url) { linkedFiles?.markRead(comic, read: read) }
        }
        library.markComicsRead(urls: urls.filter(\.isFileURL), read: read)
    }

    private func updateSeries(_ urls: [URL], name: String?, volume: String?) {
        do {
            try linkedFiles?.setSeries(urls: urls, name: name, volume: volume)
            if let name { library.addToSeries(urls: urls.filter(\.isFileURL), name: name) }
            else { library.setSeries(urls: urls.filter(\.isFileURL), series: nil, volume: nil) }
        } catch { seriesError = error.localizedDescription }
    }

    private var completedCount: Int {
        seriesComics.filter { ReadingProgress.isCompleted($0) }.count
    }

    private var nextUnread: URL? {
        seriesComics.first { !ReadingProgress.isCompleted($0) } ?? seriesComics.first
    }

    private var allRead: Bool {
        guard !seriesComics.isEmpty else { return false }
        return completedCount == seriesComics.count
    }

    var heroButtonText: String {
        guard let target = nextUnread else { return String(localized: "START") }
        let title = title(for: target)
        if allRead {
            let firstTitle = seriesComics.first.map { self.title(for: $0) } ?? title
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
        .alert("Could not update linked Files", isPresented: Binding(
            get: { seriesError != nil }, set: { if !$0 { seriesError = nil } }
        )) { Button("OK", role: .cancel) {} } message: { Text(seriesError ?? "") }
        .sheet(isPresented: $showAddComicsSheet) {
            addComicsSheet.leafAppearance(settings.theme)
        }
        .alert("Remove from Series?", isPresented: Binding(
            get: { comicToRemove != nil },
            set: { if !$0 { comicToRemove = nil } }
        )) {
            Button("Cancel", role: .cancel) { comicToRemove = nil }
            Button("Remove", role: .destructive) {
                if let url = comicToRemove {
                    updateSeries([url], name: nil, volume: nil)
                }
                comicToRemove = nil
            }
        } message: {
            Text("This comic will be detached from “\(seriesName)” and returned to your standalone library.")
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
                Text(seriesName)
                    .font(.custom("Anton-Regular", size: 18, relativeTo: .headline))
                    .foregroundColor(Leaf.cream)
                    .lineLimit(1)
                if let vol = volume, !vol.isEmpty {
                    Text(vol)
                        .leafControlFont(11, weight: 600)
                        .foregroundColor(Leaf.creamMuted)
                } else {
                    Text("SERIES COLLECTION")
                        .leafControlFont(11, weight: 600)
                        .foregroundColor(Leaf.creamMuted)
                }
            }

            Spacer()
            UnreadFilterButton(isOn: $unreadOnly, onBrand: true)
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
                UnreadFilterChip(isOn: $unreadOnly)
                heroCard
                LazyVGrid(columns: gridColumns, spacing: cols >= 4 ? 14 : 18) {
                    ForEach(seriesComics.filter {
                        (!unreadOnly || !ReadingProgress.isCompleted($0)) &&
                        LibraryBrowseState.matches(searchQuery, values: [$0.lastPathComponent,
                            title(for: $0), seriesName])
                    }, id: \.self) { url in
                        let isRead = ReadingProgress.isCompleted(url)
                        let lastPage = ReadingProgress.get(url)?.page ?? 0
                        Group {
                            if let linkedFiles, let comic = linkedFiles.comic(for: url) {
                                Button { open(url) } label: {
                                    LinkedFileCard(comic: comic, store: linkedFiles, columns: cols)
                                }
                                .buttonStyle(.plain)
                                .accessibilityIdentifier("linked-file-\(comic.id.uuidString)")
                            } else {
                        SeriesIssueCard(
                            url: url,
                            metadata: metadata(for: url),
                            progress: ReadingProgress.get(url),
                            columns: cols,
                            coverTransition: coverTransition,
                            onOpen: { open(url) },
                            onRemove: { comicToRemove = url }
                        )
                            }
                        }
                        .contextMenu {
                            Button {
                                open(url)
                            } label: {
                                Label(
                                    lastPage > 0 ? String(localized: "Continue Reading (pg \(lastPage + 1))") : String(localized: "Read Comic"),
                                    systemImage: "play.fill"
                                )
                            }
                            Button {
                                mark([url], read: !isRead)
                            } label: {
                                Label(isRead ? String(localized: "Mark as Unread") : String(localized: "Mark as Read"), systemImage: "checkmark.circle")
                            }
                            Button(role: .destructive) {
                                comicToRemove = url
                            } label: {
                                Label("Remove from Series", systemImage: "rectangle.badge.minus")
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
        let statusText = seriesComics.isEmpty
            ? String(localized: "0 ISSUES")
            : String(localized: "\(completedCount) OF \(seriesComics.count) READ")
        let progressFraction = seriesComics.isEmpty ? 0.0 : Double(completedCount) / Double(seriesComics.count)

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

                if !seriesComics.isEmpty {
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
            if !seriesComics.isEmpty {
                HStack(spacing: 8) {
                    Button {
                        mark(seriesComics, read: !allRead)
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

                    if let target = allRead ? seriesComics.first : nextUnread {
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

    private var addComicsSheet: some View {
        NavigationStack {
            let q = addSearchQuery.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
            let availableComics = (library.comics + visibleLinks.map(\.progressURL)).filter { url in
                guard !seriesComics.contains(url) else { return false }
                if q.isEmpty { return true }
                let title = title(for: url).lowercased()
                let series = metadata(for: url)?.series?.lowercased() ?? ""
                return title.contains(q) || series.contains(q)
            }
            ZStack {
                settings.ground.ignoresSafeArea()
                VStack(spacing: 0) {
                    // Search bar
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

                    if availableComics.isEmpty {
                        VStack(spacing: 8) {
                            Text(q.isEmpty ? String(localized: "No other comics available to add.") : String(localized: "No comics matching “\(addSearchQuery)”"))
                                .leafControlFont(13)
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
                                    if let linkedFiles, let comic = linkedFiles.comic(for: url) {
                                        LinkedCoverThumb(comic: comic, store: linkedFiles)
                                    } else {
                                        SmallCoverThumb(url: url)
                                    }
                                    VStack(alignment: .leading, spacing: 3) {
                                        Text(title(for: url))
                                            .leafControlFont(13, weight: 700)
                                            .foregroundColor(Leaf.text)
                                            .lineLimit(1)
                                        if url.scheme == "leaf-files" {
                                            Text("Files").leafControlFont(11).foregroundColor(Leaf.goldText)
                                        }
                                        if let s = metadata(for: url)?.series, !s.isEmpty {
                                            Text("Current: \(s)")
                                                .leafControlFont(11)
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
            .navigationTitle("Add to \(seriesName)")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { showAddComicsSheet = false }
                        .foregroundColor(Leaf.goldText)
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Add (\(selectedToAdd.count))") {
                        updateSeries(Array(selectedToAdd), name: seriesName, volume: volume)
                        showAddComicsSheet = false
                    }
                    .foregroundColor(selectedToAdd.isEmpty ? Leaf.secondaryText : Leaf.goldText)
                    .disabled(selectedToAdd.isEmpty)
                }
            }
        }
    }
}

struct LinkedCoverThumb: View {
    let comic: LinkedComic
    @ObservedObject var store: LinkedFilesStore
    var body: some View {
        Group {
            if let image = store.cover(for: comic) {
                LibraryCoverArtwork(image: image)
            } else {
                GeneratedCover(title: comic.title, columns: 4)
            }
        }
        .frame(width: 36, height: 50)
        .clipShape(RoundedCornerShape(cornerRadius: 3))
        .accessibilityHidden(true)
    }
}

/// Small 36x50 cover thumbnail used in multi-select picker sheets.
struct SmallCoverThumb: View {
    @ObservedObject private var covers = CoverLoader.shared
    let url: URL
    @State private var cover: UIImage?

    var body: some View {
        Group {
            if let cover {
                Image(uiImage: cover)
                    .resizable()
                    .scaledToFill()
            } else {
                ZStack {
                    Leaf.inkSoft
                    Text(url.deletingPathExtension().lastPathComponent.prefix(2).uppercased())
                        .font(.anton(12))
                        .foregroundColor(Leaf.creamMuted)
                }
            }
        }
        .frame(width: 36, height: 50)
        .clipShape(RoundedCornerShape(cornerRadius: 3))
        .task(id: covers.revisions(for: [url])) {
            cover = await CoverLoader.load(for: url).cover
        }
    }
}

/// Single issue card in the series detail view.
struct SeriesIssueCard: View {
    @ObservedObject private var covers = CoverLoader.shared
    @EnvironmentObject private var settings: LeafSettings
    let url: URL
    let metadata: ComicMetadataRecord?
    let progress: Progress?
    var columns: Int = 2
    var coverTransition: Namespace.ID? = nil
    var onOpen: (() -> Void)? = nil
    var onRemove: (() -> Void)? = nil

    @State private var coverPixels = 0
    @State private var cover: UIImage?
    @State private var pages = 0

    private var issueBadge: String? {
        if let num = metadata?.issueNumber {
            return String(localized: "ISSUE #\(num.cleanNumber)")
        }
        return nil
    }

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

                // Quick-remove button at top-right matching Android
                if let onRemove {
                    VStack {
                        HStack {
                            Spacer()
                            Button(action: onRemove) {
                                Image(systemName: "xmark")
                                    .font(.system(size: 11, weight: .bold))
                                    .foregroundColor(Leaf.crimson)
                                    .frame(width: 24, height: 24)
                                    .background(Leaf.deepInk.opacity(0.85))
                                    .clipShape(Circle())
                                    .frame(width: 44, height: 44)
                                    .contentShape(Rectangle())
                            }
                            .buttonStyle(.plain)
                            .accessibilityLabel(Text("Remove from Series"))
                        }
                        Spacer()
                    }
                    .padding(6)
                }

                // Issue # badge at bottom-left above progress bar
                if let badge = issueBadge {
                    HStack {
                        Text(badge)
                            .font(.anton(columns >= 4 ? 8 : 9.5))
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
                    }
                    .padding(.leading, 6)
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
                onOpen?()
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

extension Float {
    var cleanNumber: String {
        self.truncatingRemainder(dividingBy: 1) == 0 ? String(format: "%.0f", self) : String(self)
    }
}
