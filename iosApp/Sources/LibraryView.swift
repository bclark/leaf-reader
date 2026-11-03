import SwiftUI

/// Identifiable wrapper so the reader can be presented via `.fullScreenCover(item:)`.
private struct OpenComic: Identifiable {
    let url: URL
    var id: String { url.absoluteString }
}

/// Identifiable wrapper so a series detail can be presented via `.fullScreenCover(item:)`.
private struct OpenSeries: Identifiable {
    let name: String
    let volume: String?
    var id: String { "\(name)__\(volume ?? "")" }
}

/// The Leaf library — pulp-comic identity matching Android: ink ground with a halftone wash,
/// brand mark, Ochre badges, filter pills, interactive series neat stacks, reading lists,
/// and configurable grid columns.
struct LibraryView: View {
    @EnvironmentObject private var settings: LeafSettings
    @StateObject private var library = LibraryStore()

    @State private var selectedFilter: LibraryFilter = .all
    @State private var showPicker = false
    @State private var showMenu = false

    // Single comic awaiting delete confirmation
    @State private var pendingDelete: URL?

    // Batch edit mode & selection
    @State private var isEditMode = false
    @State private var selectedComics: Set<URL> = []
    @State private var showBatchDeleteAlert = false

    // Series editing
    @State private var showSetSeriesSheet = false
    @State private var seriesInputName = ""
    @State private var seriesInputVolume = ""

    // Reading lists dialogs
    @State private var showCreateListAlert = false
    @State private var newListNameInput = ""
    @State private var showAddToListSheet = false

    // Detail screens
    @State private var openComic: OpenComic?
    @State private var openSeries: OpenSeries?
    @State private var openListId: String?

    // Search mode & query
    @State private var isSearchOpen = false
    @State private var searchQuery = ""

    // Global default reading direction
    @State private var defaultRTL = ReadingPrefs.defaultRightToLeft

    private var currentItems: [LibraryItem] {
        library.libraryItems(filter: selectedFilter, searchQuery: searchQuery)
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
                Halftone(color: Leaf.crimson, alpha: 0.05).ignoresSafeArea()

                StarburstShape().fill(Leaf.crimson.opacity(0.14))
                    .frame(width: 180, height: 180)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topTrailing)
                    .padding(.top, 10)
                    .ignoresSafeArea()
                    .allowsHitTesting(false)

                VStack(spacing: 0) {
                    appBar
                    filterBar
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
                    .onDisappear { library.refresh() }
            }
            .fullScreenCover(item: $openSeries) { series in
                SeriesDetailView(
                    seriesName: series.name,
                    volume: series.volume,
                    library: library,
                    onOpenComic: { url in
                        openComic = OpenComic(url: url)
                    },
                    onDismiss: { openSeries = nil }
                )
                .environmentObject(settings)
                .onDisappear { library.refresh() }
            }
            .fullScreenCover(isPresented: Binding(
                get: { openListId != nil },
                set: { if !$0 { openListId = nil } }
            )) {
                if let listId = openListId {
                    ReadingListDetailView(
                        listId: listId,
                        library: library,
                        onOpenComic: { url in
                            openComic = OpenComic(url: url)
                        },
                        onDismiss: { openListId = nil }
                    )
                    .environmentObject(settings)
                    .onDisappear { library.refresh() }
                }
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
            .sheet(isPresented: $showSetSeriesSheet) {
                setSeriesSheet
            }
            .sheet(isPresented: $showAddToListSheet) {
                addToListSheet
            }
            .alert("Create Reading List", isPresented: $showCreateListAlert) {
                TextField("List Name", text: $newListNameInput)
                Button("Cancel", role: .cancel) {}
                Button("Create") {
                    let name = newListNameInput.trimmingCharacters(in: .whitespacesAndNewlines)
                    if !name.isEmpty {
                        library.createReadingList(name: name, urls: Array(selectedComics))
                        newListNameInput = ""
                        if isEditMode {
                            isEditMode = false
                            selectedComics.removeAll()
                        }
                    }
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
            .onAppear { library.refresh(); autoOpenIfDebug() }
        }
    }

    private func autoOpenIfDebug() {
        guard openComic == nil,
              let target = LeafDebug.env("LEAF_DEBUG_OPEN"),
              !library.comics.isEmpty else { return }
        let match = Int(target).flatMap { library.comics.indices.contains($0) ? library.comics[$0] : nil }
            ?? library.comics.first { $0.lastPathComponent.localizedCaseInsensitiveContains(target) }
            ?? library.comics.first
        if let match { openComic = OpenComic(url: match) }
    }

    // Top App Bar
    private var appBar: some View {
        VStack(spacing: 10) {
            HStack(alignment: .center) {
                LeafWordmark()
                Spacer()
                HStack(spacing: 8) {
                    Button {
                        withAnimation(.easeInOut(duration: 0.2)) {
                            isSearchOpen.toggle()
                            if !isSearchOpen { searchQuery = "" }
                        }
                    } label: {
                        Image(systemName: "magnifyingglass")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundColor(isSearchOpen ? Leaf.ink : Leaf.paperWhite)
                            .frame(width: 36, height: 36)
                            .background(isSearchOpen ? Leaf.ochre : Leaf.paperWhite.opacity(0.14))
                            .clipShape(Circle())
                    }
                    .buttonStyle(.plain)

                    Button { showMenu = true } label: {
                        Image(systemName: "line.3.horizontal")
                            .font(.system(size: 18, weight: .bold))
                            .foregroundColor(Leaf.paperWhite)
                            .frame(width: 36, height: 36)
                            .background(Leaf.paperWhite.opacity(0.14))
                            .clipShape(Circle())
                    }
                    .buttonStyle(.plain)
                }
            }

            if isSearchOpen {
                HStack(spacing: 10) {
                    Image(systemName: "magnifyingglass")
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundColor(searchQuery.isEmpty ? Leaf.creamMuted : Leaf.ochre)
                    TextField("SEARCH COMICS OR SERIES…", text: $searchQuery)
                        .font(.archivo(13, weight: 600))
                        .foregroundColor(Leaf.paperWhite)
                        .tint(Leaf.ochre)
                        .textInputAutocapitalization(.characters)
                        .disableAutocorrection(true)
                    if !searchQuery.isEmpty {
                        Button { searchQuery = "" } label: {
                            Image(systemName: "xmark.circle.fill")
                                .font(.system(size: 16))
                                .foregroundColor(Leaf.paperWhite.opacity(0.6))
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 9)
                .background(Leaf.deepInkSurface)
                .clipShape(RoundedCornerShape(cornerRadius: 6))
                .overlay(
                    RoundedCornerShape(cornerRadius: 6)
                        .stroke(!searchQuery.isEmpty ? Leaf.ochre : Leaf.deepInk, lineWidth: 2)
                )
                .comicShadow(offset: 3, color: .black.opacity(0.70), corner: 6)
                .transition(.move(edge: .top).combined(with: .opacity))
            }
        }
        .padding(.horizontal, 18)
        .padding(.top, 10)
        .padding(.bottom, 6)
        .frame(maxWidth: .infinity)
    }

    // Filter pill row: ALL | SERIES | LISTS | UNREAD
    private var filterBar: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(LibraryFilter.allCases) { filter in
                    let isSelected = selectedFilter == filter
                    Button {
                        withAnimation(.easeInOut(duration: 0.18)) {
                            selectedFilter = filter
                        }
                    } label: {
                        Text(filter.label)
                            .font(.archivo(11, weight: 700))
                            .foregroundColor(isSelected ? Leaf.ink : Leaf.cream)
                            .padding(.horizontal, 13)
                            .padding(.vertical, 6)
                            .background(isSelected ? Leaf.ochre : Leaf.inkSoft)
                            .clipShape(Capsule())
                            .overlay(
                                Capsule().stroke(isSelected ? Leaf.ochre : Leaf.ink, lineWidth: 1)
                            )
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, 18)
            .padding(.vertical, 4)
        }
    }

    // Subheader row
    private func subheader(for width: CGFloat) -> some View {
        HStack(alignment: .center) {
            if isEditMode {
                OchreBadge(text: "\(selectedComics.count) SELECTED")
            } else if !searchQuery.isEmpty {
                OchreBadge(text: "FOUND \(currentItems.count) ITEMS")
            } else {
                switch selectedFilter {
                case .all: OchreBadge(text: "Your Library")
                case .series: OchreBadge(text: "Series Stacks")
                case .readingLists: OchreBadge(text: "Reading Lists")
                case .unread: OchreBadge(text: "Unread Comics")
                }
            }

            Spacer()

            HStack(spacing: 8) {
                if !isEditMode && !library.comics.isEmpty {
                    Button {
                        withAnimation { isEditMode = true }
                    } label: {
                        Text("EDIT")
                            .font(.anton(11))
                            .foregroundColor(Leaf.cream)
                            .padding(.horizontal, 10)
                            .padding(.vertical, 5)
                            .background(Leaf.inkSoft)
                            .clipShape(RoundedCornerShape(cornerRadius: 3))
                            .overlay(RoundedCornerShape(cornerRadius: 3).stroke(Leaf.ink, lineWidth: 1))
                    }
                    .buttonStyle(.plain)
                } else if isEditMode {
                    let allSelected = !library.comics.isEmpty && selectedComics.count == library.comics.count
                    Button {
                        withAnimation {
                            if allSelected { selectedComics.removeAll() }
                            else { selectedComics = Set(library.comics) }
                        }
                    } label: {
                        Text(allSelected ? "DESELECT ALL" : "SELECT ALL")
                            .font(.anton(11))
                            .foregroundColor(allSelected ? Leaf.ink : Leaf.cream)
                            .padding(.horizontal, 9)
                            .padding(.vertical, 5)
                            .background(allSelected ? Leaf.ochre : Leaf.inkSoft)
                            .clipShape(RoundedCornerShape(cornerRadius: 3))
                            .overlay(RoundedCornerShape(cornerRadius: 3).stroke(Leaf.ink, lineWidth: 1))
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
                            .foregroundColor(Leaf.ink)
                            .padding(.horizontal, 10)
                            .padding(.vertical, 5)
                            .background(Leaf.ochre)
                            .clipShape(RoundedCornerShape(cornerRadius: 3))
                    }
                    .buttonStyle(.plain)
                }

                if !isEditMode {
                    // Columns / Density toggle
                    VStack(alignment: .trailing, spacing: 1) {
                        Text("COLUMNS")
                            .font(.archivo(7, weight: 600))
                            .foregroundColor(Leaf.creamMuted)
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
                                        .foregroundColor(isSelected ? Leaf.ink : Leaf.creamMuted)
                                        .padding(.horizontal, 7)
                                        .padding(.vertical, 3.5)
                                        .background(isSelected ? Leaf.ochre : Color.clear)
                                        .clipShape(RoundedCornerShape(cornerRadius: 2))
                                }
                                .buttonStyle(.plain)
                            }
                        }
                        .padding(1)
                        .background(Leaf.inkSoft)
                        .clipShape(RoundedCornerShape(cornerRadius: 3))
                        .overlay(RoundedCornerShape(cornerRadius: 3).stroke(Leaf.ink, lineWidth: 1))
                    }

                    // Global default direction
                    Button {
                        defaultRTL.toggle()
                        ReadingPrefs.defaultRightToLeft = defaultRTL
                    } label: {
                        VStack(alignment: .trailing, spacing: 1) {
                            Text("NEW COMICS")
                                .font(.archivo(7, weight: 600))
                                .foregroundColor(Leaf.creamMuted)
                            Text(defaultRTL ? "RTL" : "LTR")
                                .font(.archivo(12, weight: 700))
                                .foregroundColor(Leaf.ink)
                                .padding(.horizontal, 10)
                                .padding(.vertical, 4)
                                .background(Leaf.ochre)
                                .clipShape(RoundedCornerShape(cornerRadius: 3))
                        }
                    }
                    .buttonStyle(.plain)
                }
            }
        }
        .padding(.horizontal, 18)
        .padding(.top, 10)
        .padding(.bottom, 6)
    }

    // Main Grid Content
    private func content(for width: CGFloat) -> some View {
        let cols = actualColumns(for: width)
        let spacing: CGFloat = cols >= 4 ? 12 : 16
        let gridColumns = Array(repeating: GridItem(.flexible(), spacing: spacing), count: cols)
        let items = currentItems

        return ScrollView {
            VStack(spacing: 0) {
                subheader(for: width)

                if !searchQuery.isEmpty && items.isEmpty {
                    VStack(spacing: 12) {
                        Text("No items matching “\(searchQuery)”")
                            .font(.archivo(13))
                            .foregroundColor(Leaf.creamMuted)
                            .padding(.top, 36)
                        Button { searchQuery = "" } label: {
                            Text("CLEAR SEARCH")
                                .font(.anton(12))
                                .foregroundColor(Leaf.ink)
                                .padding(.horizontal, 14)
                                .padding(.vertical, 6)
                                .background(Leaf.ochre)
                                .clipShape(RoundedCornerShape(cornerRadius: 3))
                        }
                        .buttonStyle(.plain)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.top, 20)
                } else if items.isEmpty {
                    Text(emptyMessage)
                        .font(.archivo(13))
                        .foregroundColor(Leaf.creamMuted)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal, 18)
                        .padding(.vertical, 14)
                }

                LazyVGrid(columns: gridColumns, spacing: cols >= 4 ? 14 : 18) {
                    ForEach(items) { item in
                        switch item {
                        case .singleComic(let url, let meta):
                            Button {
                                if isEditMode {
                                    toggleSelect(url)
                                } else {
                                    openComic = OpenComic(url: url)
                                }
                            } label: {
                                ComicCard(
                                    url: url,
                                    progress: ReadingProgress.get(url),
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
                                    Button {
                                        selectedComics = [url]
                                        seriesInputName = meta?.series ?? ""
                                        seriesInputVolume = meta?.volume ?? ""
                                        showSetSeriesSheet = true
                                    } label: {
                                        Label("Set Series Name", systemImage: "square.stack.3d.up")
                                    }
                                    Button {
                                        selectedComics = [url]
                                        showAddToListSheet = true
                                    } label: {
                                        Label("Add to Reading List", systemImage: "list.bullet")
                                    }
                                    Button(role: .destructive) { pendingDelete = url } label: {
                                        Label("Remove", systemImage: "trash")
                                    }
                                }
                            }

                        case .seriesStack(let name, let volume, let issues, let topComic, let total, let unread, let fraction):
                            ComicStackCard(
                                seriesName: name,
                                volume: volume,
                                issues: issues,
                                topCoverComic: topComic,
                                totalIssues: total,
                                unreadIssues: unread,
                                progressFraction: fraction,
                                columns: cols,
                                isEditMode: isEditMode,
                                isSelected: issues.allSatisfy { selectedComics.contains($0) },
                                onOpenSeries: {
                                    openSeries = OpenSeries(name: name, volume: volume)
                                }
                            )
                            .contextMenu {
                                Button {
                                    openSeries = OpenSeries(name: name, volume: volume)
                                } label: {
                                    Label("Open Series", systemImage: "books.vertical")
                                }
                                Button {
                                    let allDone = unread == 0
                                    library.markComicsRead(urls: issues, read: !allDone)
                                } label: {
                                    Label(unread == 0 ? "Mark All Unread" : "Mark All Read", systemImage: "checkmark.circle")
                                }
                            }

                        case .readingListItem(let list, let previewUrls, let count):
                            ReadingListCard(
                                list: list,
                                previewUrls: previewUrls,
                                itemCount: count,
                                columns: cols,
                                onOpenList: {
                                    openListId = list.id
                                }
                            )
                            .contextMenu {
                                Button {
                                    openListId = list.id
                                } label: {
                                    Label("Open List", systemImage: "list.bullet")
                                }
                                Button(role: .destructive) {
                                    library.deleteReadingList(id: list.id)
                                } label: {
                                    Label("Delete List", systemImage: "trash")
                                }
                            }
                        }
                    }

                    if !isEditMode && searchQuery.isEmpty {
                        AddTile(
                            importing: library.importing,
                            columns: cols,
                            title: selectedFilter == .readingLists ? "CREATE LIST" : "LOAD COMIC"
                        ) {
                            if selectedFilter == .readingLists {
                                newListNameInput = ""
                                showCreateListAlert = true
                            } else {
                                showPicker = true
                            }
                        }
                    }
                }
                .padding(.horizontal, 18)
                .padding(.top, 10)
                .padding(.bottom, isEditMode ? 96 : 48)
            }
        }
    }

    private var emptyMessage: String {
        switch selectedFilter {
        case .all: return "No comics yet. Tap LOAD COMIC to add a CBZ, CBR, or PDF."
        case .series: return "No series stacks yet. Group comics by setting a Series Name."
        case .readingLists: return "No reading lists yet. Tap CREATE LIST to make one."
        case .unread: return "No unread comics in your library."
        }
    }

    private func toggleSelect(_ url: URL) {
        if selectedComics.contains(url) {
            selectedComics.remove(url)
        } else {
            selectedComics.insert(url)
        }
    }

    // Floating batch edit action bar at the bottom
    private var editActionBar: some View {
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 2) {
                Text("\(selectedComics.count) SELECTED")
                    .font(.anton(15))
                    .foregroundColor(Leaf.paperWhite)
                Text("Group, add to list, or delete")
                    .font(.archivo(11))
                    .foregroundColor(Leaf.creamMuted)
            }

            Spacer()

            Button {
                seriesInputName = ""
                seriesInputVolume = ""
                showSetSeriesSheet = true
            } label: {
                Text("SERIES")
                    .font(.anton(12))
                    .foregroundColor(Leaf.ink)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 8)
                    .background(selectedComics.isEmpty ? Leaf.ochre.opacity(0.4) : Leaf.ochre)
                    .clipShape(RoundedCornerShape(cornerRadius: 3))
            }
            .buttonStyle(.plain)
            .disabled(selectedComics.isEmpty)

            Button {
                showAddToListSheet = true
            } label: {
                Text("LIST")
                    .font(.anton(12))
                    .foregroundColor(Leaf.ink)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 8)
                    .background(selectedComics.isEmpty ? Leaf.ochre.opacity(0.4) : Leaf.ochre)
                    .clipShape(RoundedCornerShape(cornerRadius: 3))
            }
            .buttonStyle(.plain)
            .disabled(selectedComics.isEmpty)

            Button {
                showBatchDeleteAlert = true
            } label: {
                Text("DELETE")
                    .font(.anton(12))
                    .foregroundColor(Leaf.paperWhite)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 8)
                    .background(selectedComics.isEmpty ? Leaf.crimson.opacity(0.4) : Leaf.crimson)
                    .clipShape(RoundedCornerShape(cornerRadius: 3))
            }
            .buttonStyle(.plain)
            .disabled(selectedComics.isEmpty)
        }
        .padding(.horizontal, 18)
        .padding(.vertical, 14)
        .background(Leaf.deepInkSurface.ignoresSafeArea(edges: [.bottom, .horizontal]))
        .overlay(
            Rectangle().frame(height: 1).foregroundColor(Leaf.ink),
            alignment: .top
        )
        .shadow(color: .black.opacity(0.4), radius: 6, y: -2)
        .transition(.move(edge: .bottom).combined(with: .opacity))
    }

    private var setSeriesSheet: some View {
        NavigationStack {
            ZStack {
                settings.ground.ignoresSafeArea()
                VStack(alignment: .leading, spacing: 18) {
                    Text("Comics with the same Series Name automatically collapse into a Series Stack.")
                        .font(.archivo(13))
                        .foregroundColor(Leaf.creamMuted)
                        .padding(.top, 16)

                    VStack(alignment: .leading, spacing: 6) {
                        Text("SERIES NAME")
                            .font(.anton(12))
                            .foregroundColor(Leaf.cream)
                        TextField("e.g. Deadpool", text: $seriesInputName)
                            .font(.archivo(14, weight: 600))
                            .padding(10)
                            .background(Leaf.deepInkSurface)
                            .clipShape(RoundedCornerShape(cornerRadius: 4))
                            .overlay(RoundedCornerShape(cornerRadius: 4).stroke(Leaf.inkSoft, lineWidth: 1))
                            .foregroundColor(Leaf.cream)
                    }

                    VStack(alignment: .leading, spacing: 6) {
                        Text("VOLUME (OPTIONAL)")
                            .font(.anton(12))
                            .foregroundColor(Leaf.cream)
                        TextField("e.g. Vol. 1", text: $seriesInputVolume)
                            .font(.archivo(14, weight: 600))
                            .padding(10)
                            .background(Leaf.deepInkSurface)
                            .clipShape(RoundedCornerShape(cornerRadius: 4))
                            .overlay(RoundedCornerShape(cornerRadius: 4).stroke(Leaf.inkSoft, lineWidth: 1))
                            .foregroundColor(Leaf.cream)
                    }

                    Spacer()
                }
                .padding(.horizontal, 20)
            }
            .navigationTitle("Set Series")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { showSetSeriesSheet = false }
                        .foregroundColor(Leaf.ochre)
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        library.setSeries(
                            urls: Array(selectedComics),
                            series: seriesInputName.trimmingCharacters(in: .whitespacesAndNewlines),
                            volume: seriesInputVolume.trimmingCharacters(in: .whitespacesAndNewlines)
                        )
                        showSetSeriesSheet = false
                        if isEditMode {
                            isEditMode = false
                            selectedComics.removeAll()
                        }
                    }
                    .foregroundColor(Leaf.ochre)
                }
            }
        }
    }

    private var addToListSheet: some View {
        NavigationStack {
            ZStack {
                settings.ground.ignoresSafeArea()
                VStack(spacing: 0) {
                    List {
                        Section {
                            Button {
                                showAddToListSheet = false
                                newListNameInput = ""
                                showCreateListAlert = true
                            } label: {
                                HStack {
                                    Image(systemName: "plus.circle.fill")
                                        .foregroundColor(Leaf.ochre)
                                    Text("CREATE NEW READING LIST")
                                        .font(.anton(13))
                                        .foregroundColor(Leaf.cream)
                                }
                            }
                            .listRowBackground(Leaf.deepInkSurface)
                        }

                        if !library.readingLists.isEmpty {
                            Section(header: Text("EXISTING LISTS").font(.anton(11)).foregroundColor(Leaf.creamMuted)) {
                                ForEach(library.readingLists) { list in
                                    Button {
                                        library.addComicsToReadingList(listId: list.id, urls: Array(selectedComics))
                                        showAddToListSheet = false
                                        if isEditMode {
                                            isEditMode = false
                                            selectedComics.removeAll()
                                        }
                                    } label: {
                                        HStack {
                                            VStack(alignment: .leading, spacing: 3) {
                                                Text(list.name)
                                                    .font(.archivo(13, weight: 600))
                                                    .foregroundColor(Leaf.cream)
                                                Text("\(list.itemFilenames.count) comics")
                                                    .font(.archivo(11))
                                                    .foregroundColor(Leaf.creamMuted)
                                            }
                                            Spacer()
                                            Image(systemName: "chevron.right")
                                                .font(.system(size: 13, weight: .bold))
                                                .foregroundColor(Leaf.creamMuted)
                                        }
                                    }
                                    .listRowBackground(Leaf.deepInkSurface)
                                }
                            }
                        }
                    }
                    .listStyle(.insetGrouped)
                }
            }
            .navigationTitle("Add to Reading List")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { showAddToListSheet = false }
                        .foregroundColor(Leaf.ochre)
                }
            }
        }
    }
}

/// Interactive neat stack card matching Android's ComicStackCard.
/// Supports multi-layer paper card styling and tapping or swiping to cycle through covers in the stack!
struct ComicStackCard: View {
    let seriesName: String
    let volume: String?
    let issues: [URL]
    let topCoverComic: URL
    let totalIssues: Int
    let unreadIssues: Int
    let progressFraction: Double
    var columns: Int = 2
    var isEditMode: Bool = false
    var isSelected: Bool = false
    let onOpenSeries: () -> Void

    @State private var topIndex: Int = 0
    @State private var topCoverImage: UIImage?

    private var currentTopComic: URL {
        guard !issues.isEmpty else { return topCoverComic }
        let idx = (topIndex % issues.count + issues.count) % issues.count
        return issues[idx]
    }

    private var currentIssueNum: String {
        guard !issues.isEmpty else { return "1" }
        let idx = (topIndex % issues.count + issues.count) % issues.count
        return "\(idx + 1)"
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            ZStack(alignment: .bottom) {
                // Layer 3 (for 3+ issues)
                if totalIssues >= 3 {
                    RoundedCornerShape(cornerRadius: 4)
                        .fill(Leaf.inkSoft.opacity(0.85))
                        .aspectRatio(0.7, contentMode: .fit)
                        .overlay(RoundedCornerShape(cornerRadius: 4).stroke(Leaf.ink, lineWidth: 1.5))
                        .offset(y: -8)
                }

                // Layer 2 (for 2+ issues)
                if totalIssues >= 2 {
                    RoundedCornerShape(cornerRadius: 4)
                        .fill(Leaf.inkSoft)
                        .aspectRatio(0.7, contentMode: .fit)
                        .overlay(RoundedCornerShape(cornerRadius: 4).stroke(Leaf.ink, lineWidth: 2))
                        .offset(y: -4)
                }

                // Top Layer Cover
                ZStack(alignment: .bottom) {
                    Group {
                        if let topCoverImage {
                            Image(uiImage: topCoverImage).resizable().scaledToFill()
                        } else {
                            GeneratedCover(title: seriesName.uppercased(), columns: columns)
                        }
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .clipped()

                    // Top Stack Badge
                    HStack {
                        Text(totalIssues > 1 ? "ISSUE \(currentIssueNum) OF \(totalIssues)" : (volume ?? "SERIES"))
                            .font(.anton(columns >= 4 ? 9 : 10.5))
                            .foregroundColor(Leaf.ink)
                            .padding(.horizontal, 6)
                            .padding(.vertical, 3)
                            .background(Leaf.ochre)
                            .clipShape(RoundedCornerShape(cornerRadius: 2))
                        Spacer()
                    }
                    .padding(6)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)

                    // Composite progress bar inside bottom edge
                    GeometryReader { geo in
                        ZStack(alignment: .leading) {
                            Leaf.ink
                            Leaf.ochre.frame(width: geo.size.width * CGFloat(progressFraction))
                        }
                    }
                    .frame(height: columns >= 4 ? 5 : 7)
                }
                .aspectRatio(0.7, contentMode: .fit)
                .background(Leaf.inkSoft)
                .clipShape(RoundedCornerShape(cornerRadius: 4))
                .overlay(
                    RoundedCornerShape(cornerRadius: 4)
                        .stroke(isSelected ? Leaf.ochre : Leaf.ink, lineWidth: isSelected ? 3 : (columns >= 4 ? 2 : 3))
                )
                .comicShadow(offset: columns >= 4 ? 3 : 5, color: .black.opacity(0.70), corner: 4)
            }
            .aspectRatio(0.7, contentMode: .fit)
            .padding(.top, totalIssues >= 3 ? 8 : (totalIssues >= 2 ? 4 : 0))
            .onTapGesture {
                onOpenSeries()
            }
            .gesture(
                DragGesture(minimumDistance: 24)
                    .onEnded { value in
                        if totalIssues > 1 {
                            withAnimation(.easeInOut(duration: 0.15)) {
                                if value.translation.width < 0 {
                                    topIndex += 1
                                } else {
                                    topIndex -= 1
                                }
                            }
                        }
                    }
            )

            // Series Name in Anton
            Text(seriesName.uppercased())
                .font(.anton(columns >= 4 ? 12 : 14))
                .foregroundColor(Leaf.cream)
                .lineLimit(1)
                .padding(.top, columns >= 4 ? 6 : 9)

            // Subtitle: X issues · Y unread
            Text("\(totalIssues) issues · \(unreadIssues > 0 ? "\(unreadIssues) unread" : "all read")")
                .font(.archivo(columns >= 4 ? 8.5 : 9.5))
                .foregroundColor(unreadIssues > 0 ? Leaf.ochre : Leaf.creamMuted)
                .lineLimit(1)
                .padding(.top, 2)
        }
        .task(id: currentTopComic) {
            topCoverImage = await CoverLoader.load(for: currentTopComic).cover
        }
    }
}

/// Reading List Card displayed in the library grid.
struct ReadingListCard: View {
    let list: ReadingListRecord
    let previewUrls: [URL]
    let itemCount: Int
    var columns: Int = 2
    let onOpenList: () -> Void

    @State private var previewCover: UIImage?

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            ZStack(alignment: .bottom) {
                if itemCount >= 3 {
                    RoundedCornerShape(cornerRadius: 4)
                        .fill(Leaf.inkSoft.opacity(0.85))
                        .aspectRatio(0.7, contentMode: .fit)
                        .overlay(RoundedCornerShape(cornerRadius: 4).stroke(Leaf.ink, lineWidth: 1.5))
                        .offset(y: -8)
                }
                if itemCount >= 2 {
                    RoundedCornerShape(cornerRadius: 4)
                        .fill(Leaf.inkSoft)
                        .aspectRatio(0.7, contentMode: .fit)
                        .overlay(RoundedCornerShape(cornerRadius: 4).stroke(Leaf.ink, lineWidth: 2))
                        .offset(y: -4)
                }

                ZStack(alignment: .bottom) {
                    Group {
                        if let previewCover {
                            Image(uiImage: previewCover).resizable().scaledToFill()
                        } else {
                            GeneratedCover(title: list.name.uppercased(), columns: columns)
                        }
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .clipped()

                    HStack {
                        Text("\(itemCount) COMICS")
                            .font(.anton(columns >= 4 ? 9 : 10.5))
                            .foregroundColor(Leaf.ink)
                            .padding(.horizontal, 6)
                            .padding(.vertical, 3)
                            .background(Leaf.ochre)
                            .clipShape(RoundedCornerShape(cornerRadius: 2))
                        Spacer()
                    }
                    .padding(6)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
                }
                .aspectRatio(0.7, contentMode: .fit)
                .background(Leaf.inkSoft)
                .clipShape(RoundedCornerShape(cornerRadius: 4))
                .overlay(
                    RoundedCornerShape(cornerRadius: 4)
                        .stroke(Leaf.ink, lineWidth: columns >= 4 ? 2 : 3)
                )
                .comicShadow(offset: columns >= 4 ? 3 : 5, color: .black.opacity(0.70), corner: 4)
            }
            .aspectRatio(0.7, contentMode: .fit)
            .padding(.top, itemCount >= 3 ? 8 : (itemCount >= 2 ? 4 : 0))
            .onTapGesture { onOpenList() }

            Text(list.name.uppercased())
                .font(.anton(columns >= 4 ? 12 : 14))
                .foregroundColor(Leaf.cream)
                .lineLimit(1)
                .padding(.top, columns >= 4 ? 6 : 9)

            Text("\(itemCount) \(itemCount == 1 ? "comic" : "comics")")
                .font(.archivo(columns >= 4 ? 8.5 : 9.5))
                .foregroundColor(Leaf.creamMuted)
                .lineLimit(1)
                .padding(.top, 2)
        }
        .task {
            if let first = previewUrls.first {
                previewCover = await CoverLoader.load(for: first).cover
            }
        }
    }
}

/// A single comic in the grid
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

                if isEditMode {
                    ZStack {
                        Circle()
                            .fill(isSelected ? Leaf.ochre : Leaf.deepInk.opacity(0.80))
                            .frame(width: 24, height: 24)
                            .overlay(Circle().stroke(isSelected ? Leaf.ink : Leaf.paperWhite, lineWidth: 1.5))
                        if isSelected {
                            Image(systemName: "checkmark")
                                .font(.system(size: 13, weight: .bold))
                                .foregroundColor(Leaf.ink)
                        }
                    }
                    .padding(8)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topTrailing)
                }

                GeometryReader { geo in
                    ZStack(alignment: .leading) {
                        Leaf.ink
                        Leaf.ochre.frame(width: geo.size.width * CGFloat(pct))
                    }
                }
                .frame(height: columns >= 4 ? 5 : 7)
            }
            .aspectRatio(0.7, contentMode: .fit)
            .frame(maxHeight: 430)
            .background(Leaf.inkSoft)
            .clipShape(RoundedCornerShape(cornerRadius: 4))
            .overlay(
                RoundedCornerShape(cornerRadius: 4)
                    .stroke(isSelected ? Leaf.ochre : Leaf.ink, lineWidth: isSelected ? 3 : (columns >= 4 ? 2 : 3))
            )
            .comicShadow(offset: columns >= 4 ? 3 : 5, color: .black.opacity(0.70), corner: 4)

            Text(title)
                .font(.archivo(columns >= 4 ? 10 : 12, weight: 800)).foregroundColor(Leaf.cream)
                .lineLimit(2).multilineTextAlignment(.leading)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.top, columns >= 4 ? 6 : 9)
            Text(started
                 ? "\(Int(pct * 100))% · pg \(lastPage + 1)/\(pageCount)"
                 : "\(pageCount) pages")
                .font(.archivo(columns >= 4 ? 8.5 : 9.5)).foregroundColor(Leaf.creamMuted)
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

/// Placeholder cover
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
            Leaf.inkSoft
            Halftone(color: Leaf.leafPrimary, alpha: 0.18)
            Text(title).font(.anton(fontSize)).foregroundColor(Leaf.cream).padding(columns >= 4 ? 8 : 12)
        }
    }
}

/// The in-grid "LOAD COMIC" / "CREATE LIST" tile
struct AddTile: View {
    let importing: Bool
    var columns: Int = 2
    var title: String = "LOAD COMIC"
    let onTap: () -> Void

    private var leafSize: CGFloat { columns >= 4 ? 48 : 64 }
    private var iconSize: CGFloat { columns >= 4 ? 18 : 22 }
    private var titleSize: CGFloat { columns >= 4 ? 12 : 14 }
    private var strokeWidth: CGFloat { columns >= 4 ? 2 : 3 }

    var body: some View {
        Button(action: onTap) {
            VStack(spacing: columns >= 4 ? 8 : 12) {
                ZStack {
                    LeafShape().fill(Leaf.ochre).frame(width: leafSize, height: leafSize)
                    if importing {
                        ProgressView().tint(Leaf.ink)
                    } else {
                        Image(systemName: "plus").font(.system(size: iconSize, weight: .bold)).foregroundColor(Leaf.ink)
                    }
                }
                Text(title).font(.anton(titleSize)).foregroundColor(Leaf.cream)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .aspectRatio(0.7, contentMode: .fit)
            .frame(maxHeight: 430)
            .halftone(color: Leaf.ochre, alpha: 0.10)
            .overlay(
                RoundedCornerShape(cornerRadius: 4)
                    .stroke(Leaf.creamMuted, style: StrokeStyle(lineWidth: strokeWidth, dash: [14, 10]))
            )
            .clipShape(RoundedCornerShape(cornerRadius: 4))
        }
        .buttonStyle(.plain)
    }
}

struct RoundedCornerShape: Shape {
    var cornerRadius: CGFloat
    func path(in rect: CGRect) -> Path {
        Path(roundedRect: rect, cornerRadius: cornerRadius)
    }
}
