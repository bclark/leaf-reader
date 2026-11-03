import SwiftUI

/// Dedicated screen for a series stack, matching Android's SeriesDetailScreen.kt.
/// Shows series title, issue count, aggregate progress, a prominent "READ NEXT / CONTINUE" button,
/// and a grid of all issues in reading order.
struct SeriesDetailView: View {
    let seriesName: String
    let volume: String?
    @ObservedObject var library: LibraryStore
    let onOpenComic: (URL) -> Void
    let onDismiss: () -> Void

    @EnvironmentObject private var settings: LeafSettings
    @State private var showAddComicsSheet = false
    @State private var selectedToAdd: Set<URL> = []
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

    private var nextUnread: URL? {
        seriesComics.first { url in
            let p = ReadingProgress.get(url)
            let tot = p?.total ?? 1
            let pg = p?.page ?? 0
            return tot > 0 && pg < tot - 1
        } ?? seriesComics.first
    }

    private var allRead: Bool {
        guard !seriesComics.isEmpty else { return false }
        return seriesComics.allSatisfy { url in
            let p = ReadingProgress.get(url)
            let tot = p?.total ?? 1
            let pg = p?.page ?? 0
            return tot > 0 && pg >= tot - 1
        }
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
                        Button {
                            onOpenComic(url)
                        } label: {
                            SeriesIssueCard(
                                url: url,
                                metadata: library.metadata[url.lastPathComponent],
                                progress: ReadingProgress.get(url),
                                columns: cols
                            )
                        }
                        .buttonStyle(.plain)
                        .contextMenu {
                            Button {
                                onOpenComic(url)
                            } label: {
                                Label("Read", systemImage: "book.open")
                            }
                            Button {
                                let isRead = (ReadingProgress.get(url)?.page ?? 0) >= ((ReadingProgress.get(url)?.total ?? 1) - 1)
                                library.markComicsRead(urls: [url], read: !isRead)
                            } label: {
                                Label("Toggle Read Status", systemImage: "checkmark.circle")
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
            .padding(.bottom, 48)
        }
    }

    private var heroCard: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack {
                OchreBadge(text: "\(seriesComics.count) ISSUES TOTAL")
                Spacer()
                HStack(spacing: 8) {
                    Button {
                        selectedToAdd.removeAll()
                        showAddComicsSheet = true
                    } label: {
                        HStack(spacing: 4) {
                            Image(systemName: "plus")
                                .font(.system(size: 11, weight: .bold))
                            Text("ADD COMICS")
                                .font(.anton(11))
                        }
                        .foregroundColor(Leaf.ink)
                        .padding(.horizontal, 9)
                        .padding(.vertical, 5)
                        .background(Leaf.ochre)
                        .clipShape(RoundedCornerShape(cornerRadius: 3))
                    }
                    .buttonStyle(.plain)

                    Button {
                        library.markComicsRead(urls: seriesComics, read: !allRead)
                    } label: {
                        Text(allRead ? "MARK UNREAD" : "MARK READ")
                            .font(.anton(11))
                            .foregroundColor(Leaf.ochre)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 5)
                            .background(Leaf.inkSoft)
                            .clipShape(RoundedCornerShape(cornerRadius: 3))
                    }
                    .buttonStyle(.plain)
                }
            }

            if let target = nextUnread {
                let p = ReadingProgress.get(target)
                let started = (p?.page ?? 0) > 0
                let issueNum: String = {
                    if let n = library.metadata[target.lastPathComponent]?.issueNumber {
                        return "ISSUE #\(n.cleanNumber)"
                    }
                    return target.deletingPathExtension().lastPathComponent
                }()

                Button {
                    onOpenComic(target)
                } label: {
                    HStack(spacing: 8) {
                        Image(systemName: "play.fill")
                            .font(.system(size: 12))
                        Text("\(started ? "CONTINUE" : "READ NEXT"): \(issueNum.uppercased())")
                            .font(.anton(13))
                            .lineLimit(1)
                    }
                    .foregroundColor(Leaf.ink)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 10)
                    .background(Leaf.ochre)
                    .clipShape(RoundedCornerShape(cornerRadius: 4))
                }
                .buttonStyle(.plain)
            }
        }
        .padding(14)
        .background(Leaf.deepInkSurface)
        .clipShape(RoundedCornerShape(cornerRadius: 6))
        .comicShadow(offset: 3, color: .black.opacity(0.6), corner: 6)
    }

    private var addComicsSheet: some View {
        NavigationStack {
            let availableComics = library.comics.filter { !seriesComics.contains($0) }
            ZStack {
                settings.ground.ignoresSafeArea()
                VStack(spacing: 0) {
                    if availableComics.isEmpty {
                        VStack(spacing: 8) {
                            Text("No other comics available to add.")
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
                                HStack {
                                    VStack(alignment: .leading, spacing: 3) {
                                        Text(url.deletingPathExtension().lastPathComponent)
                                            .font(.archivo(13, weight: 600))
                                            .foregroundColor(Leaf.cream)
                                        if let s = library.metadata[url.lastPathComponent]?.series {
                                            Text("Current: \(s)")
                                                .font(.archivo(11))
                                                .foregroundColor(Leaf.creamMuted)
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

/// Single issue card in the series detail view.
struct SeriesIssueCard: View {
    let url: URL
    let metadata: ComicMetadataRecord?
    let progress: Progress?
    var columns: Int = 2

    @State private var cover: UIImage?
    @State private var pages = 0

    private var issueLabel: String {
        if let num = metadata?.issueNumber {
            return "ISSUE #\(num.cleanNumber)"
        }
        return url.deletingPathExtension().lastPathComponent.uppercased()
    }

    private var lastPage: Int { progress?.page ?? 0 }
    private var pageCount: Int { progress?.total ?? pages }
    private var started: Bool { lastPage > 0 }

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            ZStack(alignment: .bottom) {
                if let cover {
                    Image(uiImage: cover)
                        .resizable()
                        .scaledToFill()
                        .frame(maxWidth: .infinity)
                        .aspectRatio(0.7, contentMode: .fit)
                        .clipped()
                } else {
                    Rectangle()
                        .fill(Leaf.inkSoft)
                        .aspectRatio(0.7, contentMode: .fit)
                        .overlay(
                            Text(url.deletingPathExtension().lastPathComponent.prefix(2).uppercased())
                                .font(.anton(28))
                                .foregroundColor(Leaf.creamMuted)
                        )
                }

                if pageCount > 1 {
                    GeometryReader { geo in
                        ZStack(alignment: .leading) {
                            Rectangle().fill(Leaf.ink.opacity(0.65))
                            Rectangle()
                                .fill(Leaf.ochre)
                                .frame(width: max(0, min(geo.size.width, geo.size.width * CGFloat(progress?.fraction ?? 0))))
                        }
                    }
                    .frame(height: 4)
                }
            }
            .clipShape(RoundedCornerShape(cornerRadius: 4))
            .comicShadow(offset: columns >= 4 ? 2 : 3, color: .black.opacity(0.75), corner: 4)

            Text(issueLabel)
                .font(.archivo(columns >= 4 ? 11 : 12, weight: 700))
                .foregroundColor(Leaf.cream)
                .lineLimit(1)
                .padding(.top, 2)

            Text(started
                 ? "Page \(lastPage + 1) of \(pageCount) · \(progress?.percent ?? 0)%"
                 : (pageCount > 0 ? "\(pageCount) pages" : "Unread"))
                .font(.archivo(columns >= 4 ? 9.5 : 10.5, weight: 500))
                .foregroundColor(started ? Leaf.ochre : Leaf.creamMuted)
                .lineLimit(1)
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
