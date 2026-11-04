import SwiftUI

/// Dedicated screen for a series stack, matching Android's SeriesDetailScreen.kt.
/// Uses a compact single-row Hero Status Bar with unified read-progress badge, BookStatusBadgeIcon,
/// compact START / CONTINUE / RESTART pill, and a pinned bottom-right "+ ADD COMICS" FAB.
struct SeriesDetailView: View {
    let seriesName: String
    let volume: String?
    @ObservedObject var library: LibraryStore
    let onOpenComic: (URL) -> Void
    let onDismiss: () -> Void

    @EnvironmentObject private var settings: LeafSettings
    @State private var showAddComicsSheet = false
    @State private var selectedToAdd: Set<URL> = []
    @State private var addSearchQuery = ""
    @State private var comicToRemove: URL?

    private var seriesComics: [URL] {
        library.comics.filter { url in
            let m = library.metadata[url.lastPathComponent]
            guard let s = m?.series, s.caseInsensitiveCompare(seriesName) == .orderedSame else { return false }
            if let vol = volume, !vol.isEmpty {
                return (m?.volume ?? "").caseInsensitiveCompare(vol) == .orderedSame
            }
            return true
        }.sorted { a, b in
            let ma = library.metadata[a.lastPathComponent]
            let mb = library.metadata[b.lastPathComponent]
            let numA = ma?.issueNumber ?? Float.greatestFiniteMagnitude
            let numB = mb?.issueNumber ?? Float.greatestFiniteMagnitude
            if numA != numB { return numA < numB }
            return a.lastPathComponent.localizedStandardCompare(b.lastPathComponent) == .orderedAscending
        }
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
        guard let target = nextUnread else { return "START" }
        let title = (library.metadata[target.lastPathComponent]?.title ?? target.deletingPathExtension().lastPathComponent).uppercased()
        if allRead {
            let firstTitle = (seriesComics.first.flatMap { library.metadata[$0.lastPathComponent]?.title ?? $0.deletingPathExtension().lastPathComponent } ?? title).uppercased()
            return "READ FROM START: \(firstTitle)"
        }
        let started = (ReadingProgress.get(target)?.page ?? 0) > 0
        return "\(started ? "CONTINUE" : "START"): \(title)"
    }

    /// Compact action pill label matching Android's redesigned hero card (`START`, `CONTINUE`, `RESTART`).
    var shortActionLabel: String {
        guard let target = nextUnread else { return "START" }
        if allRead { return "RESTART" }
        let started = (ReadingProgress.get(target)?.page ?? 0) > 0 || completedCount > 0
        return started ? "CONTINUE" : "START"
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
        .navigationBarHidden(true)
        .sheet(isPresented: $showAddComicsSheet) {
            addComicsSheet
        }
        .alert("Remove from Series?", isPresented: Binding(
            get: { comicToRemove != nil },
            set: { if !$0 { comicToRemove = nil } }
        )) {
            Button("Cancel", role: .cancel) { comicToRemove = nil }
            Button("Remove", role: .destructive) {
                if let url = comicToRemove {
                    library.setSeries(urls: [url], series: nil, volume: nil)
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
            }
            .buttonStyle(.plain)

            VStack(alignment: .leading, spacing: 1) {
                Text(seriesName.uppercased())
                    .font(.anton(18))
                    .foregroundColor(Leaf.cream)
                    .lineLimit(1)
                if let vol = volume, !vol.isEmpty {
                    Text(vol.uppercased())
                        .font(.archivo(11, weight: 600))
                        .foregroundColor(Leaf.creamMuted)
                } else {
                    Text("SERIES COLLECTION")
                        .font(.archivo(11, weight: 600))
                        .foregroundColor(Leaf.creamMuted)
                }
            }

            Spacer()
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
                LazyVGrid(columns: gridColumns, spacing: cols >= 4 ? 14 : 18) {
                    ForEach(seriesComics, id: \.self) { url in
                        let isRead = ReadingProgress.isCompleted(url)
                        let lastPage = ReadingProgress.get(url)?.page ?? 0
                        SeriesIssueCard(
                            url: url,
                            metadata: library.metadata[url.lastPathComponent],
                            progress: ReadingProgress.get(url),
                            columns: cols,
                            onOpen: { onOpenComic(url) },
                            onRemove: { comicToRemove = url }
                        )
                        .contextMenu {
                            Button {
                                onOpenComic(url)
                            } label: {
                                Label(
                                    lastPage > 0 ? "Continue Reading (pg \(lastPage + 1))" : "Read Comic",
                                    systemImage: "play.fill"
                                )
                            }
                            Button {
                                library.markComicsRead(urls: [url], read: !isRead)
                            } label: {
                                Label(isRead ? "Mark as Unread" : "Mark as Read", systemImage: "checkmark.circle")
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
            ? "0 ISSUES"
            : "\(completedCount) OF \(seriesComics.count) READ"
        let progressFraction = seriesComics.isEmpty ? 0.0 : Double(completedCount) / Double(seriesComics.count)

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
                        library.markComicsRead(urls: seriesComics, read: !allRead)
                    } label: {
                        BookStatusBadgeIcon(isAllCompleted: allRead)
                            .frame(width: 36, height: 36)
                            .background(Leaf.inkSoft)
                            .clipShape(RoundedCornerShape(cornerRadius: 8))
                            .overlay(
                                RoundedCornerShape(cornerRadius: 8)
                                    .stroke(
                                        allRead ? BookStatusBadgeIcon.unreadRose.opacity(0.6) : Leaf.creamMuted.opacity(0.35),
                                        lineWidth: 1
                                    )
                            )
                    }
                    .buttonStyle(.plain)

                    if let target = allRead ? seriesComics.first : nextUnread {
                        Button {
                            onOpenComic(target)
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
        .background(Leaf.deepInkSurface)
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
            let availableComics = library.comics.filter { url in
                guard !seriesComics.contains(url) else { return false }
                if q.isEmpty { return true }
                let title = url.deletingPathExtension().lastPathComponent.lowercased()
                let series = library.metadata[url.lastPathComponent]?.series?.lowercased() ?? ""
                return title.contains(q) || series.contains(q)
            }
            ZStack {
                settings.ground.ignoresSafeArea()
                VStack(spacing: 0) {
                    // Search bar
                    HStack(spacing: 8) {
                        Image(systemName: "magnifyingglass")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundColor(addSearchQuery.isEmpty ? Leaf.creamMuted : Leaf.ochre)
                        TextField("SEARCH BY TITLE OR SERIES…", text: $addSearchQuery)
                            .font(.archivo(13, weight: 600))
                            .foregroundColor(Leaf.cream)
                            .tint(Leaf.ochre)
                            .textInputAutocapitalization(.characters)
                            .disableAutocorrection(true)
                        if !addSearchQuery.isEmpty {
                            Button { addSearchQuery = "" } label: {
                                Image(systemName: "xmark.circle.fill")
                                    .foregroundColor(Leaf.creamMuted)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .padding(10)
                    .background(Leaf.deepInkSurface)
                    .clipShape(RoundedCornerShape(cornerRadius: 6))
                    .overlay(
                        RoundedCornerShape(cornerRadius: 6)
                            .stroke(addSearchQuery.isEmpty ? Leaf.inkSoft : Leaf.ochre, lineWidth: 1.5)
                    )
                    .padding(.horizontal, 16)
                    .padding(.vertical, 10)

                    if availableComics.isEmpty {
                        VStack(spacing: 8) {
                            Text(q.isEmpty ? "No other comics available to add." : "No comics matching “\(addSearchQuery)”")
                                .font(.archivo(13))
                                .foregroundColor(Leaf.creamMuted)
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
                                            .foregroundColor(Leaf.cream)
                                            .lineLimit(1)
                                        if let s = library.metadata[url.lastPathComponent]?.series, !s.isEmpty {
                                            Text("Current: \(s)")
                                                .font(.archivo(11))
                                                .foregroundColor(Leaf.creamMuted)
                                                .lineLimit(1)
                                        }
                                    }
                                    Spacer()
                                    Image(systemName: isSelected ? "checkmark.circle.fill" : "circle")
                                        .font(.system(size: 18))
                                        .foregroundColor(isSelected ? Leaf.ochre : Leaf.creamMuted)
                                }
                            }
                            .listRowBackground(Leaf.deepInkSurface)
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
                        .foregroundColor(Leaf.ochre)
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Add (\(selectedToAdd.count))") {
                        library.setSeries(urls: Array(selectedToAdd), series: seriesName, volume: volume)
                        showAddComicsSheet = false
                    }
                    .foregroundColor(selectedToAdd.isEmpty ? Leaf.creamMuted : Leaf.ochre)
                    .disabled(selectedToAdd.isEmpty)
                }
            }
        }
    }
}

/// Small 36x50 cover thumbnail used in multi-select picker sheets.
struct SmallCoverThumb: View {
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
        .task(id: url) {
            cover = await CoverLoader.load(for: url).cover
        }
    }
}

/// Single issue card in the series detail view.
struct SeriesIssueCard: View {
    let url: URL
    let metadata: ComicMetadataRecord?
    let progress: Progress?
    var columns: Int = 2
    var onOpen: (() -> Void)? = nil
    var onRemove: (() -> Void)? = nil

    @State private var cover: UIImage?
    @State private var pages = 0

    private var issueBadge: String? {
        if let num = metadata?.issueNumber {
            return "ISSUE #\(num.cleanNumber)"
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
                        Image(uiImage: cover)
                            .resizable()
                            .scaledToFill()
                    } else {
                        GeneratedCover(
                            title: url.deletingPathExtension().lastPathComponent.uppercased(),
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
                            }
                            .buttonStyle(.plain)
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
            .background(Leaf.deepInkSurface)
            .clipShape(RoundedCornerShape(cornerRadius: 4))
            .comicShadow(offset: columns >= 4 ? 3 : 5, color: .black.opacity(0.70), corner: 4)
            .contentShape(Rectangle())
            .onTapGesture {
                onOpen?()
            }

            Text(displayTitle)
                .font(.archivo(columns >= 4 ? 10 : 12, weight: 800))
                .foregroundColor(Leaf.paperWhite)
                .lineLimit(2)
                .padding(.top, columns >= 4 ? 6 : 9)

            Text(started
                 ? "\(Int((progressFraction * 100).rounded()))% · pg \(lastPage + 1)/\(pageCount)"
                 : "\(pageCount) pages")
                .font(.archivo(columns >= 4 ? 8.5 : 9.5, weight: 500))
                .foregroundColor(started ? Leaf.ochre : Leaf.creamMuted)
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

extension Float {
    var cleanNumber: String {
        self.truncatingRemainder(dividingBy: 1) == 0 ? String(format: "%.0f", self) : String(self)
    }
}
