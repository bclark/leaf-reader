import SwiftUI

/// Identifiable wrapper so a series detail can be presented via `.fullScreenCover(item:)`.
private struct OpenSeries: Identifiable {
    let name: String
    let volume: String?
    var id: String { "\(name)__\(volume ?? "")" }
}

/// Identifiable wrapper so a reading list detail can be presented via `.fullScreenCover(item:)`.
private struct OpenReadingList: Identifiable {
    let id: String
    var showAddComics: Bool = false
}

/// The Leaf library — pulp-comic identity matching Android: ink ground with a halftone wash,
/// brand mark, Ochre badges, filter pills, interactive series neat stacks, reading lists,
/// and configurable grid columns.
struct LibraryView: View {
    @EnvironmentObject private var settings: LeafSettings
    @StateObject private var library = LibraryStore()

    @State private var selectedFilter: LibraryFilter = .all
    @State private var showPicker = false
    @State private var showImportSource = false
    @State private var showSmbBrowser = false
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
    @Namespace private var coverTransition
    @State private var openComic: OpenComic?
    @State private var openSeries: OpenSeries?
    @State private var openReadingList: OpenReadingList?

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

                // Extended "+ CREATE LIST" FAB in the LISTS tab matching Android
                if !isEditMode && selectedFilter == .readingLists {
                    HStack {
                        Spacer()
                        Button {
                            newListNameInput = ""
                            showCreateListAlert = true
                        } label: {
                            HStack(spacing: 6) {
                                Image(systemName: "plus")
                                    .font(.system(size: 13, weight: .bold))
                                Text("CREATE LIST")
                                    .font(.anton(13))
                            }
                            .foregroundColor(Leaf.ink)
                            .padding(.horizontal, 16)
                            .padding(.vertical, 12)
                            .background(Leaf.ochre)
                            .clipShape(Capsule())
                            .comicShadow(offset: 3, color: .black.opacity(0.75), corner: 22)
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel("Create Reading List")
                    }
                    .padding(.horizontal, 20)
                    .padding(.bottom, 20)
                }

                if isEditMode {
                    editActionBar
                }
            }
            .navigationBarHidden(true)
            .comicReaderPresentation(item: $openComic, namespace: coverTransition) {
                library.refresh()
            }
            .fullScreenCover(item: $openSeries) { series in
                SeriesDetailView(
                    seriesName: series.name,
                    volume: series.volume,
                    library: library,
                    onDismiss: { openSeries = nil }
                )
                .environmentObject(settings)
                .onDisappear { library.refresh() }
            }
            .fullScreenCover(item: $openReadingList) { listTarget in
                ReadingListDetailView(
                    listId: listTarget.id,
                    library: library,
                    onDismiss: { openReadingList = nil },
                    initialShowAddComics: listTarget.showAddComics
                )
                .environmentObject(settings)
                .onDisappear { library.refresh() }
            }
            .fullScreenCover(isPresented: $showMenu) {
                MenuView()
                    .environmentObject(settings)
                    .environmentObject(library)
            }
            .confirmationDialog("Add Comics", isPresented: $showImportSource, titleVisibility: .visible) {
                Button("From Files") { showPicker = true }
                Button("From SMB Drive") { showSmbBrowser = true }
            }
            .sheet(isPresented: $showSmbBrowser) {
                SmbBrowserView().environmentObject(library)
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
                setSeriesSheet.leafAppearance(settings.theme)
            }
            .sheet(isPresented: $showAddToListSheet) {
                addToListSheet.leafAppearance(settings.theme)
            }
            .alert("Create Reading List", isPresented: $showCreateListAlert) {
                TextField("e.g. Summer Crossover Event", text: $newListNameInput)
                Button("Cancel", role: .cancel) {}
                Button(selectedComics.isEmpty && !library.comics.isEmpty ? String(localized: "Create & Add Comics") : String(localized: "Create")) {
                    let name = newListNameInput.trimmingCharacters(in: .whitespacesAndNewlines)
                    guard !name.isEmpty else { return }
                    let preselected = Array(selectedComics)
                    let created = library.createReadingList(name: name, urls: preselected)
                    newListNameInput = ""
                    if isEditMode {
                        isEditMode = false
                        selectedComics.removeAll()
                    } else if preselected.isEmpty && !library.comics.isEmpty {
                        openReadingList = OpenReadingList(id: created.id, showAddComics: true)
                    }
                }
            } message: {
                Text("Group comics in any order to track a custom reading storyline.")
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
            .alert("Delete \(selectedComics.count) comics?", isPresented: $showBatchDeleteAlert) {
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
                    .accessibilityLabel(Text("MENU"))
                }
            }

            if isSearchOpen {
                HStack(spacing: 10) {
                    Image(systemName: "magnifyingglass")
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundColor(searchQuery.isEmpty ? Leaf.secondaryText : Leaf.ochre)
                    TextField("SEARCH COMICS, SERIES OR LISTS…", text: $searchQuery)
                        .font(.archivo(13, weight: 600))
                        .foregroundColor(Leaf.text)
                        .tint(Leaf.ochre)
                        .textInputAutocapitalization(.characters)
                        .disableAutocorrection(true)
                    if !searchQuery.isEmpty {
                        Button { searchQuery = "" } label: {
                            Image(systemName: "xmark.circle.fill")
                                .font(.system(size: 16))
                                .foregroundColor(Leaf.text.opacity(0.6))
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 9)
                .background(settings.groundSoft)
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
        .background(Leaf.leafPrimary.ignoresSafeArea(edges: .top))
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
                            .foregroundColor(isSelected ? Leaf.ink : Leaf.text)
                            .padding(.horizontal, 13)
                            .padding(.vertical, 6)
                            .background(isSelected ? Leaf.ochre : settings.groundSoft)
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

    // Keep display choices on their own row so the badge and editing actions
    // cannot compress column labels. At larger text sizes the groups stack.
    private func subheader(for width: CGFloat) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            ViewThatFits(in: .horizontal) {
                HStack(spacing: 12) {
                    libraryStatusBadge.fixedSize(horizontal: true, vertical: false)
                    Spacer(minLength: 0)
                    libraryEditActions.fixedSize(horizontal: true, vertical: false)
                }
                VStack(alignment: .leading, spacing: 8) {
                    libraryStatusBadge
                    libraryEditActions
                }
            }
            if !isEditMode {
                LibraryDisplayControls(
                    columns: availableColumns(for: width),
                    selectedColumn: actualColumns(for: width),
                    defaultRTL: $defaultRTL
                )
            }
        }
        .padding(.horizontal, 18)
        .padding(.top, 10)
        .padding(.bottom, 6)
    }

    @ViewBuilder
    private var libraryStatusBadge: some View {
        if isEditMode {
            OchreBadge(text: "\(selectedComics.count) SELECTED")
        } else if !searchQuery.isEmpty {
            OchreBadge(text: "FOUND \(currentItems.count) ITEMS")
        } else {
            switch selectedFilter {
            case .all: OchreBadge(text: "YOUR LIBRARY")
            case .series: OchreBadge(text: "SERIES STACKS")
            case .readingLists: OchreBadge(text: "READING LISTS")
            case .unread: OchreBadge(text: "UNREAD COMICS")
            }
        }
    }

    private var libraryEditActions: some View {
        HStack(spacing: 8) {
            if !isEditMode && !library.comics.isEmpty {
                Button {
                    withAnimation { isEditMode = true }
                } label: {
                    Text("EDIT")
                        .font(.anton(11))
                        .foregroundColor(Leaf.text)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 5)
                        .frame(minWidth: 44, minHeight: 44)
                        .background(settings.groundSoft)
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
                    Text(allSelected ? String(localized: "DESELECT ALL") : String(localized: "SELECT ALL"))
                        .font(.anton(11))
                        .foregroundColor(allSelected ? Leaf.ink : Leaf.text)
                        .padding(.horizontal, 9)
                        .padding(.vertical, 5)
                        .frame(minWidth: 44, minHeight: 44)
                        .background(allSelected ? Leaf.ochre : settings.groundSoft)
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
                        .frame(minWidth: 44, minHeight: 44)
                        .background(Leaf.ochre)
                        .clipShape(RoundedCornerShape(cornerRadius: 3))
                }
                .buttonStyle(.plain)
            }
        }
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
                            .foregroundColor(Leaf.secondaryText)
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
                        .foregroundColor(Leaf.secondaryText)
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
                                    isSelected: selectedComics.contains(url),
                                    coverTransition: coverTransition
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
                                    if isEditMode {
                                        let allInSeriesSelected = issues.allSatisfy { selectedComics.contains($0) }
                                        if allInSeriesSelected {
                                            issues.forEach { selectedComics.remove($0) }
                                        } else {
                                            issues.forEach { selectedComics.insert($0) }
                                        }
                                    } else {
                                        openSeries = OpenSeries(name: name, volume: volume)
                                    }
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
                                    Label(unread == 0 ? String(localized: "Mark All Unread") : String(localized: "Mark All Read"), systemImage: "checkmark.circle")
                                }
                            }

                        case .readingListItem(let list, let previewUrls, let count):
                            ReadingListCard(
                                list: list,
                                previewUrls: previewUrls,
                                itemCount: count,
                                columns: cols,
                                onOpenList: {
                                    openReadingList = OpenReadingList(id: list.id, showAddComics: false)
                                }
                            )
                            .contextMenu {
                                Button {
                                    openReadingList = OpenReadingList(id: list.id, showAddComics: false)
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
                                showImportSource = true
                            }
                        }
                    }
                }
                .padding(.horizontal, 18)
                .padding(.top, 10)
                .padding(.bottom, isEditMode ? 96 : (selectedFilter == .readingLists ? 84 : 48))
            }
        }
    }

    private var emptyMessage: String {
        switch selectedFilter {
        case .all: return String(localized: "No comics yet. Tap LOAD COMIC to add a CBZ, CBR, or PDF.")
        case .series: return String(localized: "No series stacks yet. Group comics by setting a Series Name.")
        case .readingLists: return String(localized: "No reading lists yet. Tap CREATE LIST to make one.")
        case .unread: return String(localized: "No unread comics in your library.")
        }
    }

    private func toggleSelect(_ url: URL) {
        if selectedComics.contains(url) {
            selectedComics.remove(url)
        } else {
            selectedComics.insert(url)
        }
    }

    // Floating batch edit action bar at the bottom matching Android's bottom Bar
    private var editActionBar: some View {
        HStack(spacing: 8) {
            VStack(alignment: .leading, spacing: 2) {
                Text("\(selectedComics.count) COMICS SELECTED")
                    .font(.anton(14))
                    .foregroundColor(Leaf.text)
                    .lineLimit(1)
                Text("Group into series, add to list, or delete")
                    .font(.archivo(10.5))
                    .foregroundColor(Leaf.secondaryText)
                    .lineLimit(1)
            }

            Spacer()

            Button {
                seriesInputName = ""
                seriesInputVolume = ""
                showSetSeriesSheet = true
            } label: {
                Text("SET SERIES")
                    .font(.anton(11))
                    .foregroundColor(Leaf.ink)
                    .padding(.horizontal, 9)
                    .padding(.vertical, 8)
                    .background(selectedComics.isEmpty ? Leaf.ochre.opacity(0.4) : Leaf.ochre)
                    .clipShape(RoundedCornerShape(cornerRadius: 3))
            }
            .buttonStyle(.plain)
            .disabled(selectedComics.isEmpty)

            Button {
                showAddToListSheet = true
            } label: {
                Text("ADD TO LIST")
                    .font(.anton(11))
                    .foregroundColor(Leaf.ink)
                    .padding(.horizontal, 9)
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
                    .font(.anton(11))
                    .foregroundColor(Leaf.text)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 8)
                    .background(selectedComics.isEmpty ? Leaf.crimson.opacity(0.4) : Leaf.crimson)
                    .clipShape(RoundedCornerShape(cornerRadius: 3))
            }
            .buttonStyle(.plain)
            .disabled(selectedComics.isEmpty)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
        .background(settings.groundSoft.ignoresSafeArea(edges: [.bottom, .horizontal]))
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
                        .foregroundColor(Leaf.secondaryText)
                        .padding(.top, 16)

                    VStack(alignment: .leading, spacing: 6) {
                        Text("SERIES NAME")
                            .font(.anton(12))
                            .foregroundColor(Leaf.text)
                        TextField("e.g. Deadpool", text: $seriesInputName)
                            .font(.archivo(14, weight: 600))
                            .padding(10)
                            .background(settings.groundSoft)
                            .clipShape(RoundedCornerShape(cornerRadius: 4))
                            .overlay(RoundedCornerShape(cornerRadius: 4).stroke(Leaf.inkSoft, lineWidth: 1))
                            .foregroundColor(Leaf.text)
                    }

                    VStack(alignment: .leading, spacing: 6) {
                        Text("VOLUME (OPTIONAL)")
                            .font(.anton(12))
                            .foregroundColor(Leaf.text)
                        TextField("e.g. Vol. 1", text: $seriesInputVolume)
                            .font(.archivo(14, weight: 600))
                            .padding(10)
                            .background(settings.groundSoft)
                            .clipShape(RoundedCornerShape(cornerRadius: 4))
                            .overlay(RoundedCornerShape(cornerRadius: 4).stroke(Leaf.inkSoft, lineWidth: 1))
                            .foregroundColor(Leaf.text)
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
                        .foregroundColor(Leaf.goldText)
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
                    .foregroundColor(Leaf.goldText)
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
                                        .foregroundColor(Leaf.goldText)
                                    Text("CREATE NEW READING LIST")
                                        .font(.anton(13))
                                        .foregroundColor(Leaf.text)
                                }
                            }
                            .listRowBackground(settings.groundSoft)
                        }

                        if !library.readingLists.isEmpty {
                            Section(header: Text("EXISTING LISTS").font(.anton(11)).foregroundColor(Leaf.secondaryText)) {
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
                                                    .foregroundColor(Leaf.text)
                                                Text("\(list.itemFilenames.count) comics")
                                                    .font(.archivo(11))
                                                    .foregroundColor(Leaf.secondaryText)
                                            }
                                            Spacer()
                                            Image(systemName: "chevron.right")
                                                .font(.system(size: 13, weight: .bold))
                                                .foregroundColor(Leaf.secondaryText)
                                        }
                                    }
                                    .listRowBackground(settings.groundSoft)
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
                        .foregroundColor(Leaf.goldText)
                }
            }
        }
    }
}

/// Interactive neat stack card matching Android's ComicStackCard.
/// Supports 3-layer paper card styling with bottom-left issue badge and vertical downward cover peel!
struct ComicStackCard: View {
    @EnvironmentObject private var settings: LeafSettings
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
    @State private var didInitTopIndex = false
    @State private var peelOffsetY: CGFloat = 0
    @State private var cardHeight: CGFloat = 220

    @State private var topCoverImage: UIImage?
    @State private var layer2CoverImage: UIImage?
    @State private var layer3CoverImage: UIImage?

    private var count: Int { max(issues.count, 1) }

    private var currentTopComic: URL {
        guard !issues.isEmpty else { return topCoverComic }
        let idx = (topIndex % count + count) % count
        return issues[idx]
    }

    private var layer2Comic: URL? {
        guard issues.count >= 2 else { return nil }
        let idx = ((topIndex + 1) % count + count) % count
        return issues[idx]
    }

    private var layer3Comic: URL? {
        guard issues.count >= 3 else { return nil }
        let idx = ((topIndex + 2) % count + count) % count
        return issues[idx]
    }

    private var currentIssueNum: Int {
        guard !issues.isEmpty else { return 1 }
        return (topIndex % count + count) % count + 1
    }

    private var layer2IssueNum: Int {
        guard !issues.isEmpty else { return 2 }
        return ((topIndex + 1) % count + count) % count + 1
    }

    private var peelProgress: CGFloat {
        guard cardHeight > 0 else { return 0 }
        return min(max(peelOffsetY / cardHeight, 0), 1)
    }

    private func lerp(_ a: CGFloat, _ b: CGFloat, _ t: CGFloat) -> CGFloat {
        a + (b - a) * t
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            ZStack(alignment: .bottom) {
                // Neat Layer 3 (Back) - for 3+ issues
                if totalIssues >= 3 {
                    ZStack {
                        Leaf.inkSoft
                        if let layer3CoverImage {
                            Image(uiImage: layer3CoverImage)
                                .resizable()
                                .scaledToFill()
                                .overlay(Color.black.opacity(peelProgress >= 0.35 ? 0 : 0.28)
                                    .animation(.easeInOut(duration: 0.18), value: peelProgress >= 0.35))
                        }
                    }
                    .aspectRatio(0.7, contentMode: .fit)
                    .clipShape(RoundedCornerShape(cornerRadius: 4))
                    .overlay(RoundedCornerShape(cornerRadius: 4).stroke(Color.clear, lineWidth: 1.5))
                    .shadow(color: .black.opacity(0.24), radius: columns >= 4 ? 3 : 5, y: 3)
                    .scaleEffect(x: lerp(0.93, 0.96, peelProgress), y: 1.0, anchor: .center)
                    .offset(y: lerp(-8, -4, peelProgress))
                }

                // Neat Layer 2 (Middle) - for 2+ issues
                if totalIssues >= 2 {
                    ZStack(alignment: .bottom) {
                        Group {
                            if let layer2CoverImage {
                                Image(uiImage: layer2CoverImage)
                                    .resizable()
                                    .scaledToFill()
                            } else {
                                GeneratedCover(title: seriesName, columns: columns)
                            }
                        }
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        .clipped()
                        .overlay(Color.black.opacity(peelProgress >= 0.35 ? 0 : 0.16)
                            .animation(.easeInOut(duration: 0.18), value: peelProgress >= 0.35))

                        stackBadge(issueNumber: layer2IssueNum)

                        progressBarView
                    }
                    .aspectRatio(0.7, contentMode: .fit)
                    .background(settings.groundSoft)
                    .clipShape(RoundedCornerShape(cornerRadius: 4))
                    .overlay(RoundedCornerShape(cornerRadius: 4).stroke(Color.clear, lineWidth: 2))
                    .shadow(color: .black.opacity(totalIssues == 2 ? 0.24 : 0), radius: columns >= 4 ? 3 : 5, y: 3)
                    .scaleEffect(x: lerp(0.96, 1.0, peelProgress), y: 1.0, anchor: .center)
                    .offset(y: lerp(-4, 0, peelProgress))
                }

                // Front Layer Cover
                ZStack(alignment: .bottom) {
                    Group {
                        if let topCoverImage {
                            Image(uiImage: topCoverImage).resizable().scaledToFill()
                        } else {
                            GeneratedCover(title: seriesName, columns: columns)
                        }
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .clipped()

                    stackBadge(issueNumber: currentIssueNum)

                    progressBarView
                }
                .aspectRatio(0.7, contentMode: .fit)
                .background(
                    GeometryReader { geo in
                        Leaf.deepInkSurface
                            .onAppear {
                                if geo.size.height > 0 { cardHeight = geo.size.height }
                            }
                            .onChange(of: geo.size.height) { newHeight in
                                if newHeight > 0 { cardHeight = newHeight }
                            }
                    }
                )
                .clipShape(RoundedCornerShape(cornerRadius: 4))
                .overlay(
                    RoundedCornerShape(cornerRadius: 4)
                        .stroke(isSelected ? Leaf.ochre : Color.clear, lineWidth: isSelected ? 3 : (columns >= 4 ? 2 : 3))
                )
                .shadow(color: .black.opacity(totalIssues <= 1 ? 0.24 : 0), radius: columns >= 4 ? 3 : 5, y: 3)
                .offset(y: peelOffsetY)
                .opacity(peelProgress > 0.75 ? Double(max(0, 1 - (peelProgress - 0.75) / 0.25)) : 1.0)
            }
            .aspectRatio(0.7, contentMode: .fit)
            .padding(.top, totalIssues >= 3 ? 10 : (totalIssues >= 2 ? 5 : 0))
            .contentShape(Rectangle())
            .onTapGesture {
                onOpenSeries()
            }
            .simultaneousGesture(
                DragGesture(minimumDistance: 12)
                    .onChanged { value in
                        guard totalIssues > 1 else { return }
                        let dy = value.translation.height
                        let dx = value.translation.width
                        // Only peel on downward vertical drags so horizontal/upward scrolls pass through
                        if dy > 0 && abs(dy) > abs(dx) {
                            peelOffsetY = min(dy, cardHeight * 1.15)
                        } else if peelOffsetY > 0 {
                            peelOffsetY = max(0, min(dy, cardHeight * 1.15))
                        }
                    }
                    .onEnded { value in
                        guard totalIssues > 1, peelOffsetY > 0 else { return }
                        let dy = value.translation.height
                        let predictedDy = value.predictedEndTranslation.height
                        let threshold = cardHeight * 0.35
                        if (dy > threshold && predictedDy >= dy - 120) || (dy >= cardHeight * 0.15 && predictedDy - dy > 120) {
                            withAnimation(.easeOut(duration: 0.18)) {
                                peelOffsetY = cardHeight * 1.15
                            }
                            DispatchQueue.main.asyncAfter(deadline: .now() + 0.18) {
                                topIndex = (topIndex + 1) % max(issues.count, 1)
                                peelOffsetY = 0
                            }
                        } else {
                            withAnimation(.spring(response: 0.24, dampingFraction: 0.78)) {
                                peelOffsetY = 0
                            }
                        }
                    }
            )

            // Series Name in Archivo ExtraBold
            Text(seriesName)
                .font(.archivo(columns >= 4 ? 10.5 : 12, weight: 800))
                .foregroundColor(Leaf.text)
                .lineLimit(2)
                .padding(.top, columns >= 4 ? 6 : 9)

            // Subtitle: X issues · Y unread
            Text("\(totalIssues) issues · \(unreadIssues) unread")
                .font(.archivo(columns >= 4 ? 8.5 : 9.5, weight: 600))
                .foregroundColor(unreadIssues > 0 ? Leaf.goldText : Leaf.secondaryText)
                .lineLimit(1)
                .padding(.top, 2)
        }
        .onAppear {
            if !didInitTopIndex {
                didInitTopIndex = true
                if let idx = issues.firstIndex(of: topCoverComic) {
                    topIndex = idx
                }
            }
        }
        .onChange(of: topCoverComic) { newTop in
            if let idx = issues.firstIndex(of: newTop) {
                topIndex = idx
            }
        }
        .task(id: currentTopComic) {
            topCoverImage = await CoverLoader.load(for: currentTopComic).cover
            if let l2 = layer2Comic {
                layer2CoverImage = await CoverLoader.load(for: l2).cover
            }
            if let l3 = layer3Comic {
                layer3CoverImage = await CoverLoader.load(for: l3).cover
            }
        }
    }

    @ViewBuilder
    private func stackBadge(issueNumber: Int) -> some View {
        HStack {
            Text(totalIssues > 1 ? String(localized: "ISSUE #\(issueNumber) OF \(totalIssues)") : (volume ?? String(localized: "1 ISSUE")))
                .font(.anton(columns >= 4 ? 7.5 : 8.5))
                .foregroundColor(Leaf.cream)
                .padding(.horizontal, columns >= 4 ? 4 : 5)
                .padding(.vertical, 2)
                .background(Leaf.ink.opacity(0.85))
                .clipShape(RoundedCornerShape(cornerRadius: 3))
                .overlay(
                    RoundedCornerShape(cornerRadius: 3)
                        .stroke(Leaf.ochre, lineWidth: 1)
                )
            Spacer()
        }
        .padding(.leading, columns >= 4 ? 4 : 6)
        .padding(.bottom, columns >= 4 ? 9 : 13)
    }

    private var progressBarView: some View {
        GeometryReader { geo in
            ZStack(alignment: .leading) {
                Leaf.ink
                Leaf.ochre.frame(width: geo.size.width * CGFloat(progressFraction))
            }
        }
        .frame(height: columns >= 4 ? 5 : 7)
    }
}

/// Reading List Card displayed in the library grid — "Messy Stack" visual with rotated layers
/// and downward cover peel matching Android's `ReadingListCard`.
struct ReadingListCard: View {
    @EnvironmentObject private var settings: LeafSettings
    let list: ReadingListRecord
    let previewUrls: [URL]
    let itemCount: Int
    var columns: Int = 2
    let onOpenList: () -> Void

    @State private var topIndex: Int = 0
    @State private var peelOffsetY: CGFloat = 0
    @State private var cardHeight: CGFloat = 220

    @State private var topCoverImage: UIImage?
    @State private var layer2CoverImage: UIImage?
    @State private var layer3CoverImage: UIImage?

    private var count: Int { max(previewUrls.count, 1) }

    private var currentTopComic: URL? {
        guard !previewUrls.isEmpty else { return nil }
        let idx = (topIndex % count + count) % count
        return previewUrls[idx]
    }

    private var layer2Comic: URL? {
        guard previewUrls.count >= 2 else { return nil }
        let idx = ((topIndex + 1) % count + count) % count
        return previewUrls[idx]
    }

    private var layer3Comic: URL? {
        guard previewUrls.count >= 3 else { return nil }
        let idx = ((topIndex + 2) % count + count) % count
        return previewUrls[idx]
    }

    private var currentItemNum: Int {
        guard !previewUrls.isEmpty else { return 1 }
        return (topIndex % count + count) % count + 1
    }

    private var layer2ItemNum: Int {
        guard !previewUrls.isEmpty else { return 2 }
        return ((topIndex + 1) % count + count) % count + 1
    }

    private var peelProgress: CGFloat {
        guard cardHeight > 0 else { return 0 }
        return min(max(peelOffsetY / cardHeight, 0), 1)
    }

    private func lerp(_ a: CGFloat, _ b: CGFloat, _ t: CGFloat) -> CGFloat {
        a + (b - a) * t
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            ZStack(alignment: .bottom) {
                // Messy Layer 3 (Rotated Left -6° -> +5°) - for 3+ items
                if itemCount >= 3 {
                    ZStack {
                        Leaf.inkSoft
                        if let layer3CoverImage {
                            Image(uiImage: layer3CoverImage)
                                .resizable()
                                .scaledToFill()
                                .overlay(Color.black.opacity(peelProgress >= 0.35 ? 0 : 0.28)
                                    .animation(.easeInOut(duration: 0.18), value: peelProgress >= 0.35))
                        }
                    }
                    .aspectRatio(0.7, contentMode: .fit)
                    .clipShape(RoundedCornerShape(cornerRadius: 4))
                    .overlay(RoundedCornerShape(cornerRadius: 4).stroke(Color.clear, lineWidth: 1.5))
                    .shadow(color: .black.opacity(0.24), radius: columns >= 4 ? 3 : 5, y: 3)
                    .rotationEffect(.degrees(Double(lerp(-6, 5, peelProgress))))
                    .offset(
                        x: lerp(-6, 7, peelProgress),
                        y: lerp(-4, -3, peelProgress)
                    )
                }

                // Messy Layer 2 (Rotated Right +5° -> 0°) - for 2+ items
                if itemCount >= 2 {
                    ZStack(alignment: .bottomLeading) {
                        Group {
                            if let layer2CoverImage {
                                Image(uiImage: layer2CoverImage)
                                    .resizable()
                                    .scaledToFill()
                            } else {
                                GeneratedCover(title: list.name, columns: columns)
                            }
                        }
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        .clipped()
                        .overlay(Color.black.opacity(peelProgress >= 0.35 ? 0 : 0.16)
                            .animation(.easeInOut(duration: 0.18), value: peelProgress >= 0.35))

                        Text("ITEM \(layer2ItemNum) OF \(itemCount)")
                            .font(.anton(columns >= 4 ? 7.5 : 8.5))
                            .foregroundColor(Leaf.cream)
                            .padding(.horizontal, columns >= 4 ? 4 : 5)
                            .padding(.vertical, 2)
                            .background(Leaf.ink.opacity(0.85))
                            .clipShape(RoundedCornerShape(cornerRadius: 3))
                            .overlay(
                                RoundedCornerShape(cornerRadius: 3)
                                    .stroke(Leaf.ochre, lineWidth: 1)
                            )
                            .padding(columns >= 4 ? 4 : 6)
                    }
                    .aspectRatio(0.7, contentMode: .fit)
                    .background(settings.groundSoft)
                    .clipShape(RoundedCornerShape(cornerRadius: 4))
                    .overlay(RoundedCornerShape(cornerRadius: 4).stroke(Color.clear, lineWidth: 2))
                    .shadow(color: .black.opacity(itemCount == 2 ? 0.24 : 0), radius: columns >= 4 ? 3 : 5, y: 3)
                    .rotationEffect(.degrees(Double(lerp(5, 0, peelProgress))))
                    .offset(
                        x: lerp(7, 0, peelProgress),
                        y: lerp(-3, 0, peelProgress)
                    )
                }

                // Front Main List Card (Straight 0°)
                ZStack(alignment: .bottomLeading) {
                    Group {
                        if let topCoverImage {
                            Image(uiImage: topCoverImage).resizable().scaledToFill()
                        } else {
                            GeneratedCover(title: list.name, columns: columns)
                        }
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .clipped()

                    Text("ITEM \(currentItemNum) OF \(itemCount)")
                        .font(.anton(columns >= 4 ? 7.5 : 8.5))
                        .foregroundColor(Leaf.cream)
                        .padding(.horizontal, columns >= 4 ? 4 : 5)
                        .padding(.vertical, 2)
                        .background(Leaf.ink.opacity(0.85))
                        .clipShape(RoundedCornerShape(cornerRadius: 3))
                        .overlay(
                            RoundedCornerShape(cornerRadius: 3)
                                .stroke(Leaf.ochre, lineWidth: 1)
                        )
                        .padding(columns >= 4 ? 4 : 6)
                }
                .aspectRatio(0.7, contentMode: .fit)
                .background(
                    GeometryReader { geo in
                        Leaf.deepInkSurface
                            .onAppear {
                                if geo.size.height > 0 { cardHeight = geo.size.height }
                            }
                            .onChange(of: geo.size.height) { newHeight in
                                if newHeight > 0 { cardHeight = newHeight }
                            }
                    }
                )
                .clipShape(RoundedCornerShape(cornerRadius: 4))
                .overlay(
                    RoundedCornerShape(cornerRadius: 4)
                        .stroke(Color.clear, lineWidth: columns >= 4 ? 2 : 3)
                )
                .shadow(color: .black.opacity(itemCount <= 1 ? 0.24 : 0), radius: columns >= 4 ? 3 : 5, y: 3)
                .offset(y: peelOffsetY)
                .opacity(peelProgress > 0.75 ? Double(max(0, 1 - (peelProgress - 0.75) / 0.25)) : 1.0)
            }
            .aspectRatio(0.7, contentMode: .fit)
            .padding(itemCount >= 2 ? 6 : 0)
            .contentShape(Rectangle())
            .onTapGesture { onOpenList() }
            .simultaneousGesture(
                DragGesture(minimumDistance: 12)
                    .onChanged { value in
                        guard itemCount > 1 else { return }
                        let dy = value.translation.height
                        let dx = value.translation.width
                        if dy > 0 && abs(dy) > abs(dx) {
                            peelOffsetY = min(dy, cardHeight * 1.15)
                        } else if peelOffsetY > 0 {
                            peelOffsetY = max(0, min(dy, cardHeight * 1.15))
                        }
                    }
                    .onEnded { value in
                        guard itemCount > 1, peelOffsetY > 0 else { return }
                        let dy = value.translation.height
                        let predictedDy = value.predictedEndTranslation.height
                        let threshold = cardHeight * 0.35
                        if (dy > threshold && predictedDy >= dy - 120) || (dy >= cardHeight * 0.15 && predictedDy - dy > 120) {
                            withAnimation(.easeOut(duration: 0.18)) {
                                peelOffsetY = cardHeight * 1.15
                            }
                            DispatchQueue.main.asyncAfter(deadline: .now() + 0.18) {
                                topIndex = (topIndex + 1) % max(previewUrls.count, 1)
                                peelOffsetY = 0
                            }
                        } else {
                            withAnimation(.spring(response: 0.24, dampingFraction: 0.78)) {
                                peelOffsetY = 0
                            }
                        }
                    }
            )

            Text(list.name)
                .font(.archivo(columns >= 4 ? 10.5 : 12, weight: 800))
                .foregroundColor(Leaf.text)
                .lineLimit(2)
                .padding(.top, columns >= 4 ? 6 : 9)

            Text("\(itemCount) items")
                .font(.archivo(columns >= 4 ? 8.5 : 9.5, weight: 600))
                .foregroundColor(Leaf.secondaryText)
                .lineLimit(1)
                .padding(.top, 2)
        }
        .task(id: currentTopComic) {
            if let top = currentTopComic {
                topCoverImage = await CoverLoader.load(for: top).cover
            } else {
                topCoverImage = nil
            }
            if let l2 = layer2Comic {
                layer2CoverImage = await CoverLoader.load(for: l2).cover
            } else {
                layer2CoverImage = nil
            }
            if let l3 = layer3Comic {
                layer3CoverImage = await CoverLoader.load(for: l3).cover
            } else {
                layer3CoverImage = nil
            }
        }
    }
}

/// A single comic in the grid
struct ComicCard: View {
    @EnvironmentObject private var settings: LeafSettings
    let url: URL
    let progress: Progress?
    var columns: Int = 2
    var isEditMode: Bool = false
    var isSelected: Bool = false
    var coverTransition: Namespace.ID? = nil
    @State private var cover: UIImage?
    @State private var pages = 0

    private var title: String { url.deletingPathExtension().lastPathComponent }
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
            .background(settings.groundSoft)
            .clipShape(RoundedCornerShape(cornerRadius: 4))
            .comicCoverSource(url: url, namespace: coverTransition)
            .overlay(
                RoundedCornerShape(cornerRadius: 4)
                    .stroke(isSelected ? Leaf.ochre : Color.clear, lineWidth: isSelected ? 3 : (columns >= 4 ? 2 : 3))
            )
            .shadow(color: .black.opacity(0.24), radius: columns >= 4 ? 3 : 5, y: 3)

            Text(title)
                .font(.archivo(columns >= 4 ? 10 : 12, weight: 800)).foregroundColor(Leaf.text)
                .lineLimit(2).multilineTextAlignment(.leading)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.top, columns >= 4 ? 6 : 9)
            Text(started
                 ? String(localized: "\(pct.formatted(.percent.precision(.fractionLength(0)))) · page \(lastPage + 1) of \(pageCount)")
                 : String(localized: "\(pageCount) pages"))
                .font(.archivo(columns >= 4 ? 8.5 : 9.5)).foregroundColor(Leaf.secondaryText)
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
    var title: LocalizedStringKey = "LOAD COMIC"
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
                Text(title).font(.anton(titleSize)).foregroundColor(Leaf.text)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .aspectRatio(0.7, contentMode: .fit)
            .frame(maxHeight: 430)
            .halftone(color: Leaf.ochre, alpha: 0.10)
            .overlay(
                RoundedCornerShape(cornerRadius: 4)
                    .stroke(Leaf.secondaryText, style: StrokeStyle(lineWidth: strokeWidth, dash: [14, 10]))
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

/// Display choices keep their full touch targets and reflow with available space.
private struct LibraryDisplayControls: View {
    let columns: [Int]
    let selectedColumn: Int
    @Binding var defaultRTL: Bool
    @EnvironmentObject private var settings: LeafSettings
    @ScaledMetric(relativeTo: .body) private var choiceSize = 15.0
    @ScaledMetric(relativeTo: .caption) private var labelSize = 12.0

    var body: some View {
        ViewThatFits(in: .horizontal) {
            HStack(alignment: .bottom, spacing: 16) {
                columnChoices.fixedSize(horizontal: true, vertical: false)
                Spacer(minLength: 0)
                directionChoice.fixedSize(horizontal: true, vertical: false)
            }
            VStack(alignment: .leading, spacing: 12) {
                columnChoices
                directionChoice
            }
        }
    }

    private var columnChoices: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("COLUMNS")
                .font(.archivo(labelSize, weight: 600))
                .foregroundColor(Leaf.secondaryText)
            HStack(spacing: 1) {
                ForEach(columns, id: \.self) { count in
                    let isSelected = selectedColumn == count
                    Button {
                        withAnimation(.easeInOut(duration: 0.2)) {
                            settings.libraryColumns = count
                        }
                    } label: {
                        Text("\(count)")
                            .font(.archivo(choiceSize, weight: 700))
                            .foregroundColor(isSelected ? Leaf.ink : Leaf.secondaryText)
                            .padding(.horizontal, 8)
                            .frame(minWidth: 44, minHeight: 44)
                            .background(isSelected ? Leaf.ochre : Color.clear)
                            .clipShape(RoundedCornerShape(cornerRadius: 3))
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(Text("COLUMNS"))
                    .accessibilityValue(Text("\(count)"))
                    .accessibilityAddTraits(isSelected ? .isSelected : [])
                }
            }
            .padding(1)
            .background(settings.groundSoft)
            .clipShape(RoundedCornerShape(cornerRadius: 3))
            .overlay(RoundedCornerShape(cornerRadius: 3).stroke(Leaf.secondaryText, lineWidth: 1))
        }
    }

    private var directionChoice: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("NEW COMICS")
                .font(.archivo(labelSize, weight: 600))
                .foregroundColor(Leaf.secondaryText)
            Button {
                defaultRTL.toggle()
                ReadingPrefs.defaultRightToLeft = defaultRTL
            } label: {
                Text(defaultRTL ? String(localized: "RTL") : String(localized: "LTR"))
                    .font(.archivo(choiceSize, weight: 700))
                    .foregroundColor(Leaf.ink)
                    .padding(.horizontal, 12)
                    .frame(minWidth: 44, minHeight: 44)
                    .background(Leaf.ochre)
                    .clipShape(RoundedCornerShape(cornerRadius: 3))
            }
            .buttonStyle(.plain)
            .accessibilityLabel(Text("NEW COMICS"))
            .accessibilityValue(Text(defaultRTL ? String(localized: "RTL") : String(localized: "LTR")))
        }
    }
}
