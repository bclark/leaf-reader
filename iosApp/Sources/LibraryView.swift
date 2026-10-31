import SwiftUI

/// Identifiable wrapper so the reader can be presented via `.fullScreenCover(item:)`.
private struct OpenComic: Identifiable {
    let url: URL
    var id: String { url.absoluteString }
}

/// The Chika library — pulp-comic identity matching the Android design: ink ground with a halftone
/// wash, the three-panel mark + CHI·KA wordmark, an ochre "YOUR LIBRARY" badge, and a configurable
/// grid of comic cards with issue tags and Anton titles.
struct LibraryView: View {
    @EnvironmentObject private var settings: ChikaSettings
    @StateObject private var library = LibraryStore()
    @State private var showPicker = false
    @State private var showMenu = false
    // Comic awaiting single-delete confirmation from context menu.
    @State private var pendingDelete: URL?
    // Batch edit mode & selection.
    @State private var isEditMode = false
    @State private var selectedComics: Set<URL> = []
    @State private var showBatchDeleteAlert = false
    // Search mode & query.
    @State private var isSearchOpen = false
    @State private var searchQuery = ""
    // Resume snapshot keyed by filename, refreshed on appear and when returning from the reader so
    // cards show fresh progress without resetting scroll position.
    @State private var progress: [String: Progress] = [:]
    // The comic being read, presented as a full-screen cover.
    @State private var openComic: OpenComic?
    // Global default reading direction for comics not yet opened.
    @State private var defaultRTL = ReadingPrefs.defaultRightToLeft

    private var filteredComics: [URL] {
        let q = searchQuery.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard !q.isEmpty else { return library.comics }
        return library.comics.filter {
            $0.deletingPathExtension().lastPathComponent.lowercased().contains(q)
        }
    }

    private func availableColumns(for width: CGFloat) -> [Int] {
        if width >= 600 {
            return [2, 3, 4, 6]
        } else if width >= 380 {
            return [2, 3, 4]
        } else {
            return [2, 3]
        }
    }

    private func actualColumns(for width: CGFloat) -> Int {
        let avail = availableColumns(for: width)
        return min(max(settings.libraryColumns, 2), avail.last ?? 2)
    }

    var body: some View {
        NavigationStack {
            ZStack(alignment: .bottom) {
                settings.ground.ignoresSafeArea()
                Halftone(color: Chika.crimson, alpha: 0.05).ignoresSafeArea()
                // Decorative action-burst behind the header (Android LibraryScreen).
                StarburstShape().fill(Chika.crimson.opacity(0.14))
                    .frame(width: 180, height: 180)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topTrailing)
                    .padding(.top, 10)
                    .ignoresSafeArea()
                    .allowsHitTesting(false)

                VStack(spacing: 0) {
                    appBar
                    GeometryReader { geo in
                        content(for: geo.size.width)
                    }
                }

                if isEditMode {
                    editActionBar
                }
            }
            .navigationBarHidden(true)
            .fullScreenCover(item: $openComic) { item in
                ReaderView(comicURL: item.url)
                    .environmentObject(settings)
                    .onDisappear { reloadProgress() }
            }
            .fullScreenCover(isPresented: $showMenu) {
                MenuView().environmentObject(settings)
            }
            .fileImporter(
                isPresented: $showPicker,
                allowedContentTypes: LibraryStore.importableTypes,
                allowsMultipleSelection: true
            ) { result in
                switch result {
                case .success(let urls): urls.forEach(library.importComic)
                case .failure(let error): library.importError = error.localizedDescription
                }
            }
            .alert("Import failed", isPresented: Binding(
                get: { library.importError != nil },
                set: { if !$0 { library.importError = nil } }
            )) {
                Button("OK", role: .cancel) {}
            } message: {
                Text(library.importError ?? "")
            }
            .alert("Remove comic?", isPresented: Binding(
                get: { pendingDelete != nil },
                set: { if !$0 { pendingDelete = nil } }
            )) {
                Button("Cancel", role: .cancel) { pendingDelete = nil }
                Button("Delete", role: .destructive) {
                    if let url = pendingDelete { library.delete(url) }
                    pendingDelete = nil
                }
            } message: {
                Text("This removes the imported copy. The original file is untouched.")
            }
            .alert("Delete \(selectedComics.count) \(selectedComics.count == 1 ? "comic" : "comics")?", isPresented: $showBatchDeleteAlert) {
                Button("Cancel", role: .cancel) {}
                Button("Delete", role: .destructive) {
                    withAnimation {
                        library.deleteBatch(selectedComics)
                        selectedComics.removeAll()
                        isEditMode = false
                    }
                }
            } message: {
                Text("This will remove the imported copies from your device. Your original files are unaffected.")
            }
            .onAppear { library.refresh(); reloadProgress(); autoOpenIfDebug() }
        }
    }

    /// QA hook: when launched with the CHIKA_DEBUG_OPEN env var, auto-open that comic.
    private func autoOpenIfDebug() {
        guard openComic == nil,
              let target = ChikaDebug.env("CHIKA_DEBUG_OPEN"),
              !library.comics.isEmpty else { return }
        let match = Int(target).flatMap { library.comics.indices.contains($0) ? library.comics[$0] : nil }
            ?? library.comics.first { $0.lastPathComponent.localizedCaseInsensitiveContains(target) }
            ?? library.comics.first
        if let match { openComic = OpenComic(url: match) }
    }

    private func reloadProgress() {
        var map: [String: Progress] = [:]
        for url in library.comics { map[url.lastPathComponent] = ReadingProgress.get(url) }
        progress = map
    }

    // Top App Bar with Leaf brand primary color, search button, and menu button.
    private var appBar: some View {
        VStack(spacing: 10) {
            HStack(alignment: .center) {
                ChikaWordmark()
                Spacer()
                HStack(spacing: 8) {
                    Button {
                        withAnimation(.easeInOut(duration: 0.2)) {
                            isSearchOpen.toggle()
                            if !isSearchOpen {
                                searchQuery = ""
                            }
                        }
                    } label: {
                        Image(systemName: "magnifyingglass")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundColor(isSearchOpen ? Chika.ink : Chika.paperWhite)
                            .frame(width: 36, height: 36)
                            .background(isSearchOpen ? Chika.ochre : Chika.paperWhite.opacity(0.14))
                            .clipShape(Circle())
                    }
                    .buttonStyle(.plain)

                    Button { showMenu = true } label: {
                        Image(systemName: "line.3.horizontal")
                            .font(.system(size: 18, weight: .bold))
                            .foregroundColor(Chika.paperWhite)
                            .frame(width: 36, height: 36)
                            .background(Chika.paperWhite.opacity(0.14))
                            .clipShape(Circle())
                    }
                    .buttonStyle(.plain)
                }
            }

            if isSearchOpen {
                HStack(spacing: 10) {
                    Image(systemName: "magnifyingglass")
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundColor(searchQuery.isEmpty ? Chika.creamMuted : Chika.ochre)
                    TextField("SEARCH COMICS BY TITLE…", text: $searchQuery)
                        .font(.archivo(13, weight: 600))
                        .foregroundColor(Chika.paperWhite)
                        .tint(Chika.ochre)
                        .textInputAutocapitalization(.characters)
                        .disableAutocorrection(true)
                    if !searchQuery.isEmpty {
                        Button { searchQuery = "" } label: {
                            Image(systemName: "xmark.circle.fill")
                                .font(.system(size: 16))
                                .foregroundColor(Chika.paperWhite.opacity(0.6))
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 9)
                .background(Chika.deepInkSurface)
                .clipShape(RoundedCornerShape(cornerRadius: 6))
                .overlay(
                    RoundedCornerShape(cornerRadius: 6)
                        .stroke(!searchQuery.isEmpty ? Chika.ochre : Chika.deepInk, lineWidth: 2)
                )
                .comicShadow(offset: 3, color: .black.opacity(0.70), corner: 6)
                .transition(.move(edge: .top).combined(with: .opacity))
            }
        }
        .padding(.horizontal, 18)
        .padding(.top, 10)
        .padding(.bottom, 8)
        .frame(maxWidth: .infinity)
        .background(Color.clear)
    }

    // Subheader row: badge on left, Edit / Columns / Reading Direction toggles on right.
    private func subheader(for width: CGFloat) -> some View {
        HStack(alignment: .center) {
            if isEditMode {
                OchreBadge(text: "\(selectedComics.count) SELECTED")
            } else if !searchQuery.isEmpty {
                OchreBadge(text: "FOUND \(filteredComics.count) \(filteredComics.count == 1 ? "COMIC" : "COMICS")")
            } else {
                OchreBadge(text: "Your Library")
            }

            Spacer()

            HStack(spacing: 8) {
                if !isEditMode && !library.comics.isEmpty {
                    Button {
                        withAnimation { isEditMode = true }
                    } label: {
                        Text("EDIT")
                            .font(.anton(11))
                            .foregroundColor(Chika.cream)
                            .padding(.horizontal, 10)
                            .padding(.vertical, 5)
                            .background(Chika.inkSoft)
                            .clipShape(RoundedCornerShape(cornerRadius: 3))
                            .overlay(RoundedCornerShape(cornerRadius: 3).stroke(Chika.ink, lineWidth: 1))
                    }
                    .buttonStyle(.plain)
                } else if isEditMode {
                    let allSelected = !filteredComics.isEmpty && selectedComics.count == filteredComics.count
                    Button {
                        withAnimation {
                            if allSelected {
                                selectedComics.removeAll()
                            } else {
                                selectedComics = Set(filteredComics)
                            }
                        }
                    } label: {
                        Text(allSelected ? "DESELECT ALL" : "SELECT ALL")
                            .font(.anton(11))
                            .foregroundColor(allSelected ? Chika.ink : Chika.cream)
                            .padding(.horizontal, 9)
                            .padding(.vertical, 5)
                            .background(allSelected ? Chika.ochre : Chika.inkSoft)
                            .clipShape(RoundedCornerShape(cornerRadius: 3))
                            .overlay(RoundedCornerShape(cornerRadius: 3).stroke(Chika.ink, lineWidth: 1))
                    }
                    .buttonStyle(.plain)

                    Button {
                        withAnimation {
                            isEditMode = false
                            selectedComics.removeAll()
                        }
                    } label: {
                        Text("DONE")
                            .font(.anton(11))
                            .foregroundColor(Chika.ink)
                            .padding(.horizontal, 10)
                            .padding(.vertical, 5)
                            .background(Chika.ochre)
                            .clipShape(RoundedCornerShape(cornerRadius: 3))
                    }
                    .buttonStyle(.plain)
                }

                if !isEditMode {
                    // Columns / Density toggle
                    VStack(alignment: .trailing, spacing: 1) {
                        Text("COLUMNS")
                            .font(.archivo(7, weight: 600))
                            .foregroundColor(Chika.creamMuted)
                        HStack(spacing: 1) {
                            ForEach(availableColumns(for: width), id: \.self) { count in
                                let isSelected = actualColumns(for: width) == count
                                Button {
                                    withAnimation(.easeInOut(duration: 0.2)) {
                                        settings.libraryColumns = count
                                    }
                                } label: {
                                    Text("\(count)")
                                        .font(.archivo(11.5, weight: 700))
                                        .foregroundColor(isSelected ? Chika.ink : Chika.creamMuted)
                                        .padding(.horizontal, 7)
                                        .padding(.vertical, 3.5)
                                        .background(isSelected ? Chika.ochre : Color.clear)
                                        .clipShape(RoundedCornerShape(cornerRadius: 2))
                                }
                                .buttonStyle(.plain)
                            }
                        }
                        .padding(1)
                        .background(Chika.inkSoft)
                        .clipShape(RoundedCornerShape(cornerRadius: 3))
                        .overlay(RoundedCornerShape(cornerRadius: 3).stroke(Chika.ink, lineWidth: 1))
                    }

                    // Global default direction for new comics
                    Button {
                        defaultRTL.toggle()
                        ReadingPrefs.defaultRightToLeft = defaultRTL
                    } label: {
                        VStack(alignment: .trailing, spacing: 1) {
                            Text("NEW COMICS OPEN")
                                .font(.archivo(7, weight: 600))
                                .foregroundColor(Chika.creamMuted)
                            Text(defaultRTL ? "RTL" : "LTR")
                                .font(.archivo(12, weight: 700))
                                .foregroundColor(Chika.ink)
                                .padding(.horizontal, 10)
                                .padding(.vertical, 4)
                                .background(Chika.ochre)
                                .clipShape(RoundedCornerShape(cornerRadius: 3))
                        }
                    }
                    .buttonStyle(.plain)
                }
            }
        }
        .padding(.horizontal, 18)
        .padding(.top, 14)
        .padding(.bottom, 6)
    }

    // Grid of comic cards followed by the "LOAD COMIC" tile.
    private func content(for width: CGFloat) -> some View {
        let cols = actualColumns(for: width)
        let spacing: CGFloat = cols >= 4 ? 12 : 16
        let gridColumns = Array(repeating: GridItem(.flexible(), spacing: spacing), count: cols)

        return ScrollView {
            VStack(spacing: 0) {
                subheader(for: width)

                if !searchQuery.isEmpty && filteredComics.isEmpty {
                    VStack(spacing: 12) {
                        Text("No comics matching “\(searchQuery)”")
                            .font(.archivo(13))
                            .foregroundColor(Chika.creamMuted)
                            .padding(.top, 36)
                        Button {
                            searchQuery = ""
                        } label: {
                            Text("CLEAR SEARCH")
                                .font(.anton(12))
                                .foregroundColor(Chika.ink)
                                .padding(.horizontal, 14)
                                .padding(.vertical, 6)
                                .background(Chika.ochre)
                                .clipShape(RoundedCornerShape(cornerRadius: 3))
                        }
                        .buttonStyle(.plain)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.top, 20)
                } else if library.comics.isEmpty {
                    Text("No comics yet. Tap LOAD COMIC to add a CBZ, CBR, or PDF.")
                        .font(.archivo(13)).foregroundColor(Chika.creamMuted)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal, 18).padding(.vertical, 12)
                }

                LazyVGrid(columns: gridColumns, spacing: cols >= 4 ? 14 : 18) {
                    ForEach(filteredComics, id: \.self) { url in
                        Button {
                            if isEditMode {
                                if selectedComics.contains(url) {
                                    selectedComics.remove(url)
                                } else {
                                    selectedComics.insert(url)
                                }
                            } else {
                                openComic = OpenComic(url: url)
                            }
                        } label: {
                            ComicCard(
                                url: url,
                                progress: progress[url.lastPathComponent] ?? nil,
                                columns: cols,
                                isEditMode: isEditMode,
                                isSelected: selectedComics.contains(url)
                            )
                        }
                        .buttonStyle(.plain)
                        .simultaneousGesture(
                            LongPressGesture().onEnded { _ in
                                if !isEditMode {
                                    isEditMode = true
                                    selectedComics.insert(url)
                                }
                            }
                        )
                        .contextMenu {
                            if !isEditMode {
                                Button {
                                    isEditMode = true
                                    selectedComics.insert(url)
                                } label: {
                                    Label("Select", systemImage: "checkmark.circle")
                                }
                                Button(role: .destructive) { pendingDelete = url } label: {
                                    Label("Remove", systemImage: "trash")
                                }
                            }
                        }
                    }

                    if !isEditMode && searchQuery.isEmpty {
                        AddTile(importing: library.importing, columns: cols) { showPicker = true }
                    }
                }
                .padding(.horizontal, 18)
                .padding(.top, 10)
                .padding(.bottom, isEditMode ? 96 : 48)
            }
        }
    }

    // Floating batch edit action bar at the bottom.
    private var editActionBar: some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text("\(selectedComics.count) \(selectedComics.count == 1 ? "COMIC" : "COMICS") SELECTED")
                    .font(.anton(15))
                    .foregroundColor(Chika.paperWhite)
                Text("Tap delete to remove selected copies")
                    .font(.archivo(11))
                    .foregroundColor(Chika.creamMuted)
            }
            Spacer()
            Button {
                showBatchDeleteAlert = true
            } label: {
                Text("DELETE (\(selectedComics.count))")
                    .font(.anton(13))
                    .foregroundColor(Chika.paperWhite)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 9)
                    .background(selectedComics.isEmpty ? Chika.crimson.opacity(0.5) : Chika.crimson)
                    .clipShape(RoundedCornerShape(cornerRadius: 4))
            }
            .buttonStyle(.plain)
            .disabled(selectedComics.isEmpty)
        }
        .padding(.horizontal, 18)
        .padding(.vertical, 14)
        .background(Chika.deepInkSurface.ignoresSafeArea(edges: [.bottom, .horizontal]))
        .overlay(
            Rectangle().frame(height: 1).foregroundColor(Chika.ink),
            alignment: .top
        )
        .shadow(color: .black.opacity(0.4), radius: 6, y: -2)
        .transition(.move(edge: .bottom).combined(with: .opacity))
    }
}

/// A single comic in the grid, matching Android: a 0.7-aspect cover under a hard comic shadow with
/// a full-width progress bar along its bottom edge, the title in Archivo below the cover, and a
/// progress/page-count line.
struct ComicCard: View {
    let url: URL
    let progress: Progress?
    var columns: Int = 2
    var isEditMode: Bool = false
    var isSelected: Bool = false
    @State private var cover: UIImage?
    @State private var pages = 0

    private var title: String { url.deletingPathExtension().lastPathComponent.uppercased() }
    private var lastPage: Int { progress?.page ?? 0 }
    private var pageCount: Int { progress?.total ?? pages }
    private var started: Bool { lastPage > 0 }
    // Android's fill formula: (lastPage + 1) / pageCount — page 0 already counts as "on page 1".
    private var pct: Double { pageCount > 0 ? min(max(Double(lastPage + 1) / Double(pageCount), 0), 1) : 0 }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            ZStack(alignment: .bottom) {
                Group {
                    if let cover {
                        Image(uiImage: cover).resizable().scaledToFill()
                    } else {
                        GeneratedCover(title: title, columns: columns)
                    }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .clipped()

                // Edit mode selection checkbox in top right
                if isEditMode {
                    ZStack {
                        Circle()
                            .fill(isSelected ? Chika.ochre : Chika.deepInk.opacity(0.80))
                            .frame(width: 24, height: 24)
                            .overlay(Circle().stroke(isSelected ? Chika.ink : Chika.paperWhite, lineWidth: 1.5))
                        if isSelected {
                            Image(systemName: "checkmark")
                                .font(.system(size: 13, weight: .bold))
                                .foregroundColor(Chika.ink)
                        }
                    }
                    .padding(8)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topTrailing)
                }

                // Progress bar inside the cover's bottom edge — always shown (0% when unstarted).
                GeometryReader { geo in
                    ZStack(alignment: .leading) {
                        Chika.ink
                        Chika.ochre.frame(width: geo.size.width * CGFloat(pct))
                    }
                }
                .frame(height: columns >= 4 ? 5 : 7)
            }
            .aspectRatio(0.7, contentMode: .fit)
            .frame(maxHeight: 430)
            .background(Chika.inkSoft)
            .clipShape(RoundedCornerShape(cornerRadius: 4))
            .overlay(
                RoundedCornerShape(cornerRadius: 4)
                    .stroke(isSelected ? Chika.ochre : Chika.ink, lineWidth: isSelected ? 3 : (columns >= 4 ? 2 : 3))
            )
            .comicShadow(offset: columns >= 4 ? 3 : 5, color: .black.opacity(0.70), corner: 4)

            Text(title)
                .font(.archivo(columns >= 4 ? 10 : 12, weight: 800)).foregroundColor(Chika.cream)
                .lineLimit(2).multilineTextAlignment(.leading)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.top, columns >= 4 ? 6 : 9)
            Text(started
                 ? "\(Int(pct * 100))% · pg \(lastPage + 1)/\(pageCount)"
                 : "\(pageCount) pages")
                .font(.archivo(columns >= 4 ? 8.5 : 9.5)).foregroundColor(Chika.creamMuted)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.top, 2)
        }
        .task {
            let loaded = await CoverLoader.load(for: url)
            cover = loaded.cover
            pages = loaded.pageCount
        }
    }
}

/// Placeholder cover for comics with no extractable page-0 image: ink-soft ground, crimson halftone,
/// and the title in Anton bottom-left — Android's GeneratedCover.
struct GeneratedCover: View {
    let title: String
    var columns: Int = 2

    private var fontSize: CGFloat {
        if columns >= 5 { return 13 }
        if columns >= 4 { return 15 }
        if columns == 3 { return 18 }
        return 22
    }

    var body: some View {
        ZStack(alignment: .bottomLeading) {
            Chika.inkSoft
            Halftone(color: Chika.leafPrimary, alpha: 0.18)
            Text(title).font(.anton(fontSize)).foregroundColor(Chika.cream).padding(columns >= 4 ? 8 : 12)
        }
    }
}

/// The in-grid "LOAD COMIC" tile: a dashed-border cell with an ochre halftone and an upright gold leaf
/// holding a + (or a spinner while importing) — matching Android's updated AddTile.
struct AddTile: View {
    let importing: Bool
    var columns: Int = 2
    let onTap: () -> Void

    private var leafSize: CGFloat { columns >= 4 ? 48 : 64 }
    private var iconSize: CGFloat { columns >= 4 ? 18 : 22 }
    private var titleSize: CGFloat { columns >= 4 ? 12 : 14 }
    private var strokeWidth: CGFloat { columns >= 4 ? 2 : 3 }

    var body: some View {
        Button(action: onTap) {
            VStack(spacing: columns >= 4 ? 8 : 12) {
                ZStack {
                    LeafShape().fill(Chika.ochre).frame(width: leafSize, height: leafSize)
                    if importing {
                        ProgressView().tint(Chika.ink)
                    } else {
                        Image(systemName: "plus").font(.system(size: iconSize, weight: .bold)).foregroundColor(Chika.ink)
                    }
                }
                Text("LOAD COMIC").font(.anton(titleSize)).foregroundColor(Chika.cream)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .aspectRatio(0.7, contentMode: .fit)
            .frame(maxHeight: 430)
            .halftone(color: Chika.ochre, alpha: 0.10)
            .overlay(
                RoundedCornerShape(cornerRadius: 4)
                    .stroke(Chika.creamMuted, style: StrokeStyle(lineWidth: strokeWidth, dash: [14, 10]))
            )
            .clipShape(RoundedCornerShape(cornerRadius: 4))
        }
        .buttonStyle(.plain)
    }
}

/// RoundedRectangle alias matching Android's RoundedCornerShape naming, for readability.
struct RoundedCornerShape: Shape {
    var cornerRadius: CGFloat
    func path(in rect: CGRect) -> Path {
        Path(roundedRect: rect, cornerRadius: cornerRadius)
    }
}
