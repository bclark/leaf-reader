import SwiftUI
import UniformTypeIdentifiers

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
private struct LibraryPrimaryActionHeight: PreferenceKey {
    static var defaultValue: CGFloat { 0 }
    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) { value = max(value, nextValue()) }
}

struct LibraryView: View {
    @EnvironmentObject private var settings: LeafSettings
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @StateObject private var library = LibraryStore()
    @StateObject private var linkedFiles = LinkedFilesStore()
    @StateObject private var jellyfin = JellyfinBrowserModel()
    @StateObject private var catalogue = JellyfinCatalogueModel()
    @StateObject private var onboarding = FirstLaunchOnboarding()
    @State private var onboardingFocusRequest = 0
    @State private var browse = LibraryBrowseState()
    @State private var offlineComics: Set<URL> = []
    @State private var primaryActionHeight: CGFloat = 0
    @State private var offlineRevisions: Set<CoverLoader.Revision> = []
    @State private var checkingOffline = false
    private typealias ScrollTarget = (id: String, anchor: UnitPoint)
    @State private var scrollPositions: [LibraryBrowseState.ViewMode: ScrollTarget] = [:]
    @State private var pendingScrollTarget: ScrollTarget?
    @State private var restoringView: LibraryBrowseState.ViewMode?
    @AccessibilityFocusState private var unreadButtonFocused: Bool
    @ObservedObject private var covers = CoverLoader.shared
    private var downloadedOnly: Bool { browse.view == .offline }
    @State private var remoteBook: JellyfinBook?
    @State private var remoteGroup: JellyfinCatalogueModel.Group?
    @State private var showRemoteDetail = false

    private var showsRemote: Bool {
        jellyfin.connection != nil && !downloadedOnly
    }

    private var selectedFilter: LibraryFilter { browse.view.filter }
    @State private var showPicker = false
    private enum FilesAction { case copy, link }
    @State private var filesAction = FilesAction.copy
    @State private var openedLinkedFile: LinkedComic?
    @State private var pendingUnlink: LinkedComic?
    @State private var linkingFile = false
    @State private var linkedFileError: String?
    @State private var showImportSource = false
    @State private var showSmbBrowser = false
    @State private var showJellyfinBrowser = false
    @State private var showMenu = false
    @State private var showLibraryAppearance = false

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
    @State private var showCreateListSheet = false
    @State private var createListAfterAddSheet = false
    @State private var pendingCreatedListID: String?
    @State private var newListNameInput = ""
    @State private var showAddToListSheet = false

    // Detail screens
    @Namespace private var coverTransition
    @State private var openComic: OpenComic?
    @State private var openSeries: OpenSeries?
    @State private var openReadingList: OpenReadingList?

    // Search mode & query
    @State private var isSearchOpen = false
    @Environment(\.scenePhase) private var scenePhase
    @State private var searchQuery = ""
    @ScaledMetric(relativeTo: .subheadline) private var searchTextSize = 13.0
    @ScaledMetric(relativeTo: .headline) private var primaryActionTextSize = 16.0
    @FocusState private var searchFieldFocused: Bool
    @AccessibilityFocusState private var searchButtonFocused: Bool
    @AccessibilityFocusState private var searchFieldAccessibilityFocused: Bool

    private var readableOfflineComics: Set<URL> {
        offlineRevisions == Set(covers.revisions(for: library.comics)) ? offlineComics : []
    }

    @State private var readableContinue: Set<URL> = []
    @State private var continueRevisions: [CoverLoader.Revision] = []

    private var localContinue: [LibraryContinueItem.Local] {
        library.comics.compactMap { url in
            ReadingProgress.get(url).map { .init(url: url, progress: $0, openedAt: ReadingProgress.openedAt(url)) }
        }
    }

    private var remoteContinueAvailable: Bool {
        !downloadedOnly && catalogue.isCurrent(for: jellyfin.connection) && jellyfin.client != nil && !jellyfin.needsSignIn
            && !catalogue.fromCache && !catalogue.unreachable && !catalogue.unavailable && !catalogue.requiresSignIn
    }

    private var continueItems: [LibraryContinueItem] {
        let revisions = covers.revisions(for: library.comics)
        return LibraryContinueItem.ranked(local: continueRevisions == revisions
            ? localContinue.filter { readableContinue.contains($0.url) } : [],
            remote: catalogue.books, downloadedIDs: Set(jellyfin.downloaded.keys),
            remoteAvailable: remoteContinueAvailable, linked: downloadedOnly ? [] : linkedFiles.comics)
    }

    private func resume(_ item: LibraryContinueItem) {
        if let linked = item.linked {
            openedLinkedFile = linked
        } else if let url = item.url {
            openComic = OpenComic(url: url)
        } else if remoteContinueAvailable, let book = item.book {
            remoteBook = book
            showRemoteDetail = true
        }
    }

    /// Nothing local and no server: the empty state replaces the grid, tile and onboarding card.
    private var showsEmptyLibrary: Bool {
        library.comics.isEmpty && (downloadedOnly || linkedFiles.comics.isEmpty)
            && jellyfin.connection == nil && searchQuery.isEmpty
            && selectedFilter != .readingLists && !isEditMode
    }

    private var showsEmptyLists: Bool {
        selectedFilter == .readingLists && library.readingLists.isEmpty && searchQuery.isEmpty && !isEditMode
    }

    private var currentItems: [LibraryItem] {
        let items = library.libraryItems(filter: selectedFilter, searchQuery: searchQuery,
            unreadOnly: browse.unreadOnly, available: downloadedOnly ? readableOfflineComics : nil,
            linked: isEditMode ? [] : linkedFiles.comics)
        guard showsRemote, selectedFilter != .readingLists else { return items }
        // Preserve the existing Jellyfin catalogue while keeping Files comics in the normal card grid.
        return items.filter {
            switch $0 {
            case .singleComic(let url, _): return url.scheme == "leaf-files"
            case .seriesStack(_, _, let issues, _, _, _, _): return issues.contains { $0.scheme == "leaf-files" }
            case .readingListItem: return false
            }
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
        if dynamicTypeSize.isAccessibilitySize { return 1 }
        let avail = availableColumns(for: width)
        return min(max(settings.libraryColumns, 2), avail.last ?? 2)
    }

    private func openFiles(_ action: FilesAction) {
        filesAction = action
        showPicker = true
    }

    var body: some View {
        Group {
            if onboarding.isComplete {
                libraryNavigation
            } else {
                FirstLaunchOnboardingView(onboarding: onboarding, busy: library.importing,
                    focusRequest: onboardingFocusRequest,
                    chooseFile: { openFiles(.copy) }, connect: { showJellyfinBrowser = true })
            }
        }
        .environmentObject(library)
        .environment(\.tipPromptAllowed, !isEditMode && !isSearchOpen && !showMenu && !showLibraryAppearance
            && !showPicker && openedLinkedFile == nil
            && !showImportSource && !showSmbBrowser && !showJellyfinBrowser)
        .sheet(isPresented: $showJellyfinBrowser, onDismiss: {
            onboardingFocusRequest += 1
            Task {
                await catalogue.start(browser: jellyfin, force: true)
                await jellyfin.restoreDownloads(library: library)
            }
        }) {
            JellyfinBrowserView(model: jellyfin, catalogue: catalogue, onConnected: {
                guard !onboarding.isComplete, showJellyfinBrowser else { return }
                onboarding.finish()
                showJellyfinBrowser = false
            }).environmentObject(library)
        }
        .fileImporter(isPresented: $showPicker, allowedContentTypes: filesAction == .copy
            ? LibraryStore.importableTypes : LinkedFilesStore.supportedTypes,
            allowsMultipleSelection: filesAction == .copy) { result in
            switch result {
            case .success(let urls):
                switch filesAction {
                case .copy:
                    urls.forEach { url in library.importComic(from: url) { _ in onboarding.finish() } }
                case .link:
                    guard let url = urls.first else { return }
                    linkingFile = true
                    Task { @MainActor in
                        defer { linkingFile = false }
                        do {
                            try await linkedFiles.link(url)
                            if downloadedOnly { browse.select(.series) }
                        } catch { linkedFileError = error.localizedDescription }
                    }
                }
            case .failure(let error):
                if (error as? CocoaError)?.code != .userCancelled {
                    if filesAction == .link { linkedFileError = error.localizedDescription }
                    else { library.importError = error.localizedDescription }
                }
            }
        }
        .alert("Could not update linked Files", isPresented: Binding(
            get: { linkedFileError != nil }, set: { if !$0 { linkedFileError = nil } }
        )) {
            Button("OK", role: .cancel) {}
        } message: { Text(linkedFileError ?? "") }
        .onChange(of: showPicker) { presented in if !presented { onboardingFocusRequest += 1 } }
        .onChange(of: library.importing) { busy in if !busy { onboardingFocusRequest += 1 } }
        .alert("Import failed", isPresented: Binding(
            get: { library.importError != nil },
            set: { if !$0 {
                // Native presentation bindings can clear during a view update.
                // Publish the observed error change after that update completes.
                let dismissedError = library.importError
                DispatchQueue.main.async {
                    if library.importError == dismissedError { library.importError = nil }
                    onboardingFocusRequest += 1
                }
            } }
        )) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(library.importError ?? "")
        }
        .task {
            await jellyfin.restoreDownloads(library: library)
            await catalogue.start(browser: jellyfin)
            if catalogue.unreachable { browse.handleUnreachableLaunch() }
            browse.finishLaunchCheck()
        }
    }

    private var libraryNavigation: some View {
        NavigationStack {
            GeometryReader { window in
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
                    appBar(wide: window.size.width >= 840 && dynamicTypeSize < .xxxLarge)
                    if !isEditMode {
                        if window.size.width < 840 || dynamicTypeSize >= .xxxLarge { filterBar }
                        HStack(spacing: 8) {
                            UnreadFilterChip(isOn: $browse.unreadOnly) { unreadButtonFocused = true }
                        }
                        .frame(maxWidth: .infinity, alignment: .trailing)
                        .padding(.horizontal, 18)
                    }
                    GeometryReader { geo in
                        content(for: geo.size)
                    }
                }
                .overlay(alignment: .bottomTrailing) { libraryPrimaryAction }
                .onPreferenceChange(LibraryPrimaryActionHeight.self) { primaryActionHeight = $0 }

                if isEditMode {
                    editActionBar
                }
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
                    library: library, linkedFiles: linkedFiles,
                    onDismiss: { openSeries = nil },
                    unreadOnly: $browse.unreadOnly, searchQuery: searchQuery,
                    availableCopies: downloadedOnly ? readableOfflineComics : nil
                )
                .environmentObject(settings)
                .onDisappear { library.refresh() }
            }
            .fullScreenCover(item: $openReadingList) { listTarget in
                ReadingListDetailView(
                    listId: listTarget.id,
                    library: library, linkedFiles: linkedFiles,
                    onDismiss: { openReadingList = nil },
                    initialShowAddComics: listTarget.showAddComics,
                    jellyfin: jellyfin, catalogue: catalogue, downloadedOnly: downloadedOnly,
                    unreadOnly: $browse.unreadOnly, searchQuery: searchQuery
                )
                .environmentObject(settings)
                .onDisappear { library.refresh() }
            }
            .sheet(isPresented: $showLibraryAppearance) {
                NavigationStack {
                    Form { LibraryAppearanceControls() }
                        .navigationTitle("Library appearance")
                        .toolbar { ToolbarItem(placement: .confirmationAction) {
                            Button("Done") { showLibraryAppearance = false }
                        } }
                }
                .environmentObject(settings)
                .leafAppearance(settings.theme)
            }
            .fullScreenCover(isPresented: $showMenu) {
                MenuView(jellyfin: jellyfin, catalogue: catalogue)
                    .environmentObject(settings)
                    .environmentObject(library)
            }
            .confirmationDialog("Add Comics", isPresented: $showImportSource, titleVisibility: .visible) {
                Button("Copy from Files") { openFiles(.copy) }
                Button("Link from Files") { openFiles(.link) }.disabled(linkingFile)
                Button("From SMB Drive") { showSmbBrowser = true }
                Button("From Jellyfin") { showJellyfinBrowser = true }
            }
            .linkedReaderPresentation(item: $openedLinkedFile, store: linkedFiles) {
                library.refresh(backfill: false)
            }
            .alert("Remove link?", isPresented: Binding(
                get: { pendingUnlink != nil }, set: { if !$0 { pendingUnlink = nil } }
            )) {
                Button("Cancel", role: .cancel) { pendingUnlink = nil }
                Button("Remove Link", role: .destructive) {
                    if let comic = pendingUnlink {
                        do { try linkedFiles.remove(comic); library.refresh(backfill: false) }
                        catch { linkedFileError = error.localizedDescription }
                    }
                    pendingUnlink = nil
                }
            } message: {
                Text("The original in Files stays untouched. Leaf removes this shelf entry and its reading progress.")
            }
            .navigationDestination(isPresented: $showRemoteDetail) {
                if let remoteBook {
                    JellyfinDetailView(item: remoteBook, model: jellyfin,
                        creatorLibraryID: catalogue.libraryID, creatorCachedBooks: catalogue.books)
                        .environmentObject(library)
                }
            }
            .sheet(item: $remoteGroup) { group in
                NavigationStack {
                    JellyfinGroupView(group: group, browser: jellyfin, catalogue: catalogue,
                        downloadedOnly: downloadedOnly, unreadOnly: $browse.unreadOnly, searchQuery: searchQuery)
                        .environmentObject(library)
                }
            }
            .onChange(of: openComic?.url) { comic in
                if comic != nil { searchFieldFocused = false; searchFieldAccessibilityFocused = false }
            }
            .onChange(of: showRemoteDetail) { presented in
                if presented { searchFieldFocused = false; searchFieldAccessibilityFocused = false }
            }
            .task(id: downloadedOnly ? covers.revisions(for: library.comics) : []) {
                guard downloadedOnly else { return }
                checkingOffline = true
                let revisions = Set(covers.revisions(for: library.comics))
                do {
                    offlineComics = try await LibraryBrowseState.readableCopies(library.comics)
                    offlineRevisions = revisions
                    checkingOffline = false
                } catch { /* A superseded view/file scan must not publish partial results. */ }
            }
            .onChange(of: scenePhase) { phase in
                if phase == .active {
                    Task { await catalogue.refreshIfNeeded(browser: jellyfin) }
                }
            }
            .sheet(isPresented: $showSmbBrowser) {
                SmbBrowserView().environmentObject(library)
            }
            .sheet(isPresented: $showSetSeriesSheet) {
                setSeriesSheet.leafAppearance(settings.theme)
            }
            .sheet(isPresented: $showAddToListSheet, onDismiss: {
                if createListAfterAddSheet {
                    createListAfterAddSheet = false
                    showCreateListSheet = true
                }
            }) {
                addToListSheet.leafAppearance(settings.theme)
            }
            .sheet(isPresented: $showCreateListSheet, onDismiss: {
                if let id = pendingCreatedListID {
                    pendingCreatedListID = nil
                    openReadingList = OpenReadingList(id: id, showAddComics: true)
                }
            }) {
                createListSheet.leafAppearance(settings.theme)
            }
            .alert("Remove comic?", isPresented: Binding(
                get: { pendingDelete != nil },
                set: { if !$0 { pendingDelete = nil } }
            )) {
                Button("Cancel", role: .cancel) { pendingDelete = nil }
                Button("Delete", role: .destructive) {
                    if let url = pendingDelete {
                        Task {
                            await library.deletePreservingJellyfinProgress([url])
                            await jellyfin.restoreDownloads(library: library)
                        }
                    }
                    pendingDelete = nil
                }
            } message: {
                Text("This removes the imported copy. The original file is untouched.")
            }
            .alert("Delete \(selectedComics.count) comics?", isPresented: $showBatchDeleteAlert) {
                Button("Cancel", role: .cancel) {}
                Button("Delete", role: .destructive) {
                    let selected = selectedComics
                    Task {
                        await library.deletePreservingJellyfinProgress(selected)
                        await jellyfin.restoreDownloads(library: library)
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
    private func appBar(wide: Bool) -> some View {
        VStack(spacing: 10) {
            HStack(alignment: .center) {
                LeafWordmark()
                Spacer(minLength: 12)
                if wide && !isEditMode { viewSelector(onBrand: true).frame(maxWidth: 440) }
                Spacer(minLength: wide ? 12 : 0)
                HStack(spacing: 4) {
                    UnreadFilterButton(isOn: $browse.unreadOnly, onBrand: true)
                        .accessibilityFocused($unreadButtonFocused)
                    Button {
                        withAnimation(.easeInOut(duration: 0.2)) {
                            isSearchOpen.toggle()
                            if !isSearchOpen {
                                searchQuery = ""
                                searchFieldFocused = false
                                searchButtonFocused = true
                            }
                        }
                    } label: {
                        Image(systemName: "magnifyingglass")
                            .font(.system(size: 18, weight: .bold))
                            .foregroundColor(isSearchOpen ? Leaf.ink : Leaf.paperWhite)
                            .frame(width: 36, height: 36)
                            .background(isSearchOpen ? Leaf.ochre : Leaf.paperWhite.opacity(0.14))
                            .clipShape(Circle())
                            .frame(minWidth: 44, minHeight: 44)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(isSearchOpen ? Text("Close") : Text("SEARCH COMICS, SERIES OR LISTS…"))
                    .accessibilityFocused($searchButtonFocused)

                    Menu {
                        Button("Select comics") { isEditMode = true; selectedComics = [] }
                            .disabled(library.comics.isEmpty || showsRemote || isEditMode)
                        Divider()
                        Button("Library appearance") { showLibraryAppearance = true }
                        Button("Settings and more") { showMenu = true }
                    } label: {
                        Image(systemName: "line.3.horizontal")
                            .font(.system(size: 18, weight: .bold))
                            .foregroundColor(Leaf.paperWhite)
                            .frame(width: 36, height: 36)
                            .background(Leaf.paperWhite.opacity(0.14))
                            .clipShape(Circle())
                            .frame(minWidth: 44, minHeight: 44)
                            .contentShape(Rectangle())
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
                        .font(.archivo(searchTextSize, weight: 600))
                        .foregroundColor(Leaf.text)
                        .tint(Leaf.ochre)
                        .textInputAutocapitalization(.characters)
                        .disableAutocorrection(true)
                        .focused($searchFieldFocused)
                        .accessibilityFocused($searchFieldAccessibilityFocused)
                        .onAppear {
                            searchFieldFocused = true
                            searchFieldAccessibilityFocused = true
                        }
                    if !searchQuery.isEmpty {
                        Button {
                            searchQuery = ""
                            searchFieldFocused = true
                            searchFieldAccessibilityFocused = true
                        } label: {
                            Image(systemName: "xmark.circle.fill")
                                .font(.system(size: 16))
                                .foregroundColor(Leaf.text.opacity(0.6))
                                .frame(minWidth: 44, minHeight: 44)
                                .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel(Text("CLEAR SEARCH"))
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

    private var filterBar: some View {
        viewSelector(onBrand: false)
            .padding(.horizontal, 18)
            .padding(.top, 4)
    }

    private func viewSelector(onBrand: Bool) -> some View {
        LibraryViewSelector(selection: Binding(get: { browse.view }, set: { view in
            guard view != browse.view else { return }
            pendingScrollTarget = scrollPositions[view] ?? (view.rawValue + ":top", .top)
            restoringView = view
            browse.select(view)
        }), onBrand: onBrand)
    }

    // Android leads the visual hierarchy; reflow preserves iOS accessibility.
    private func subheader(for width: CGFloat) -> some View {
        ViewThatFits(in: .horizontal) {
            HStack(spacing: 12) {
                libraryStatusBadge.fixedSize(horizontal: true, vertical: false)
                Spacer(minLength: 0)
                libraryActions(for: width)
            }
            VStack(alignment: .leading, spacing: 8) {
                libraryStatusBadge
                libraryActions(for: width)
            }
        }
        .padding(.horizontal, 18)
        .padding(.top, 10)
        .padding(.bottom, 6)
    }

    private func libraryActions(for width: CGFloat) -> some View {
        LibraryAdaptiveControls {
            libraryEditActions
        }
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
        LibraryAdaptiveControls {
            if !isEditMode && !library.comics.isEmpty && !showsRemote {
                Button {
                    isEditMode = true
                } label: {
                    Text("EDIT")
                        .font(.custom("Anton-Regular", size: 11, relativeTo: .caption))
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
                    if allSelected { selectedComics.removeAll() }
                    else { selectedComics = Set(library.comics) }
                } label: {
                    Text(allSelected ? String(localized: "DESELECT ALL") : String(localized: "SELECT ALL"))
                        .font(.custom("Anton-Regular", size: 11, relativeTo: .caption))
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
                    isEditMode = false
                    selectedComics.removeAll()
                } label: {
                    Text("DONE")
                        .font(.custom("Anton-Regular", size: 11, relativeTo: .caption))
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
    private func content(for size: CGSize) -> some View {
        let width = size.width
        let cols = actualColumns(for: width)
        let spacing: CGFloat = cols >= 4 ? 12 : 16
        let gridColumns = Array(repeating: GridItem(.flexible(), spacing: spacing, alignment: .top), count: cols)
        let items = currentItems

        return ScrollViewReader { proxy in
            ScrollView {
                VStack(spacing: 0) {
                    Color.clear.frame(height: 1).id(browse.view.rawValue + ":top")
                    if isEditMode { subheader(for: width) }
                    if downloadedOnly && checkingOffline { ProgressView("Checking local comics…").padding() }

                    if showsEmptyLibrary {
                        EmptyLibraryView(importing: library.importing, add: { showImportSource = true },
                            connect: { showJellyfinBrowser = true })
                    }
                    if !isEditMode && !isSearchOpen { continueSection(wide: UIDevice.current.userInterfaceIdiom == .pad && width >= 600) }
                    if linkingFile { ProgressView("Linking comic from Files…").padding() }
                    if jellyfin.connection != nil {
                        JellyfinCatalogueStatus(catalogue: catalogue, browser: jellyfin,
                            connect: { showJellyfinBrowser = true })
                    }
                    if showsRemote && selectedFilter != .readingLists {
                        JellyfinMainGrid(catalogue: catalogue, browser: jellyfin, filter: selectedFilter,
                            query: searchQuery, columns: cols, downloadedOnly: false,
                            unreadOnly: browse.unreadOnly, scrollScope: browse.view.rawValue,
                            select: { remoteBook = $0; showRemoteDetail = true },
                            selectGroup: { remoteGroup = $0 })
                            .environmentObject(library)
                    }
                    if !showsRemote || selectedFilter == .readingLists || !items.isEmpty {
                    if showsRemote && selectedFilter == .readingLists {
                        JellyfinCollectionsSection(catalogue: catalogue, browser: jellyfin,
                            unreadOnly: $browse.unreadOnly, searchQuery: searchQuery)
                            .environmentObject(library)
                    }
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
                    } else if items.isEmpty && !checkingOffline && !showsEmptyLibrary {
                        Text(emptyMessage)
                            .font(.archivo(13))
                            .foregroundColor(Leaf.secondaryText)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(.horizontal, 18)
                            .padding(.vertical, 14)
                    }

                    if browse.unreadOnly && items.isEmpty && !checkingOffline && (!downloadedOnly || !readableOfflineComics.isEmpty) {
                        Button("Clear Unread only") { browse.unreadOnly = false; unreadButtonFocused = true }
                            .frame(minHeight: 44)
                    }
                    if showsEmptyLists {
                        Button {
                            newListNameInput = ""
                            showCreateListSheet = true
                        } label: {
                            Label("Create Reading List", systemImage: "plus")
                                .leafControlFont(15, weight: 700)
                                .foregroundStyle(Leaf.ink)
                                .padding(.horizontal, 18)
                                .padding(.vertical, 12)
                                .frame(minHeight: 44)
                                .background(Leaf.ochre, in: RoundedRectangle(cornerRadius: 12))
                        }
                        .buttonStyle(.plain)
                        .padding(.horizontal, 18)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    // The empty state stands alone; reading lists stay reachable from the Lists view.
                    if !showsEmptyLibrary {
                    LazyVGrid(columns: gridColumns, spacing: cols >= 4 ? 14 : 18) {
                        ForEach(items) { item in
                            Group {
                            switch item {
                            case .singleComic(let url, let meta):
                                if let comic = linkedFiles.comic(for: url) {
                                    linkedShelfCard(comic, columns: cols)
                                } else {
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
                                    linkedFiles: linkedFiles,
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
                                        library.markComicsRead(urls: issues.filter(\.isFileURL), read: !allDone)
                                        for comic in issues.compactMap({ linkedFiles.comic(for: $0) }) {
                                            linkedFiles.markRead(comic, read: !allDone)
                                        }
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
                                    members: ReadingListMember.resolve(list.itemFilenames, local: library.comics,
                                        metadata: library.metadata,
                                        books: catalogue.isCurrent(for: jellyfin.connection) ? catalogue.knownBooks : [],
                                        connection: jellyfin.connection, linked: linkedFiles.comics).filter {
                                            (!downloadedOnly || $0.url.map { readableOfflineComics.contains($0) } == true) &&
                                            LibraryBrowseState.includes(completed: $0.completed, unreadOnly: browse.unreadOnly)
                                        },
                                    remoteClient: jellyfin.client, remoteProfileID: jellyfin.connection?.id ?? "",
                                    remoteCopies: jellyfin.downloaded, linkedFiles: linkedFiles,
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
                            .fixedSize(horizontal: false, vertical: true)
                            .libraryScrollAnchor(browse.view.rawValue + ":" + item.id)
                        }

                        // Comics are added from the bottom action; lists keep their in-grid tile.
                        if !isEditMode && !showsEmptyLists && searchQuery.isEmpty && selectedFilter == .readingLists {
                            AddTile(importing: false, columns: cols, title: "CREATE LIST") {
                                newListNameInput = ""
                                showCreateListSheet = true
                            }
                        }
                    }
                    .padding(.horizontal, 18)
                    .padding(.top, 10)
                    .padding(.bottom, isEditMode ? 96 : 16)
                    }
                    }
                }
                .padding(.bottom, isEditMode ? 0 : max(100, primaryActionHeight + 32))
            }
            .coordinateSpace(name: "library.scroll")
            .onPreferenceChange(LibraryScrollPositions.self) { positions in
                guard restoringView != browse.view else { return }
                let prefix = browse.view.rawValue + ":"
                if let first = positions.filter({ $0.key.hasPrefix(prefix) }).min(by: { abs($0.value.minY) < abs($1.value.minY) }) {
                    // Align the same points in the item and viewport to keep its partial offset.
                    let travel = size.height - first.value.height
                    let anchorY = abs(travel) > 1 ? first.value.minY / travel : 0
                    scrollPositions[browse.view] = (first.key, UnitPoint(x: 0.5, y: anchorY))
                }
            }
            .onChange(of: browse.view) { view in
                let target = pendingScrollTarget.flatMap { $0.id.hasPrefix(view.rawValue + ":") ? $0 : nil }
                ?? scrollPositions[view] ?? (view.rawValue + ":top", .top)
                Task { @MainActor in
                    await Task.yield()
                    guard browse.view == view else { return }
                    proxy.scrollTo(target.id, anchor: target.anchor)
                    await Task.yield()
                    if browse.view == view { restoringView = nil }
                }
            }
            .refreshable { await catalogue.refresh(browser: jellyfin) }
        }
    }

    private func linkedShelfCard(_ comic: LinkedComic, columns: Int) -> some View {
        Button { openedLinkedFile = comic } label: {
            LinkedFileCard(comic: comic, store: linkedFiles, columns: columns)
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("linked-file-\(comic.id.uuidString)")
        .contextMenu {
            Button("Set Series Name", systemImage: "square.stack.3d.up") {
                selectedComics = [comic.progressURL]
                seriesInputName = comic.shelfMetadata.series ?? ""
                seriesInputVolume = comic.shelfMetadata.volume ?? ""
                showSetSeriesSheet = true
            }
            Button("Add to Reading List", systemImage: "list.bullet") {
                selectedComics = [comic.progressURL]
                showAddToListSheet = true
            }
            Button("Remove Link", systemImage: "link.badge.minus", role: .destructive) {
                pendingUnlink = comic
            }
        }
        .accessibilityAction(named: Text("Remove Link")) { pendingUnlink = comic }
    }

    private func continueSection(wide: Bool) -> some View {
        let limit = wide ? 5 : 1
        let entries = Array(continueItems.prefix(limit))
        return VStack(alignment: .leading, spacing: 8) {
            if !entries.isEmpty {
                if wide {
                    Text("Continue reading").textCase(.uppercase)
                        .leafControlFont(11, weight: 700).tracking(0.8).foregroundColor(Leaf.goldText)
                        .accessibilityAddTraits(.isHeader)
                        .padding(.horizontal, 18)
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(alignment: .top, spacing: 12) {
                            ForEach(entries) { entry in continueCard(entry, wide: true).frame(width: 340) }
                        }.padding(.horizontal, 18)
                    }
                } else {
                    ForEach(entries) { entry in continueCard(entry, wide: false).padding(.horizontal, 18) }
                }
            }
        }
        .padding(.top, entries.isEmpty ? 0 : 12)
        .task(id: "\(limit):\(covers.revisions(for: library.comics)):\(localContinue.map { "\($0.url.lastPathComponent):\($0.openedAt):\($0.progress)" })") {
            let revisions = covers.revisions(for: library.comics)
            let sorted = LibraryContinueItem.ranked(local: localContinue, remote: [], downloadedIDs: [], remoteAvailable: false)
            var readable: Set<URL> = []
            for item in sorted {
                guard !Task.isCancelled, let url = item.url else { return }
                let loaded = await CoverLoader.load(for: url)
                guard !Task.isCancelled else { return }
                if loaded.pageCount > 0 && loaded.cover != nil { readable.insert(url) }
                if readable.count >= limit { break }
            }
            guard !Task.isCancelled, covers.revisions(for: library.comics) == revisions else { return }
            readableContinue = readable
            continueRevisions = revisions
        }
    }

    private func continueCard(_ item: LibraryContinueItem, wide: Bool) -> some View {
        ContinueReadingCard(item: item, client: jellyfin.client, profileID: jellyfin.connection?.id,
            wide: wide, linkedCover: item.linked.flatMap { linkedFiles.cover(for: $0) },
            open: { resume(item) })
    }

    @ViewBuilder
    private var libraryPrimaryAction: some View {
        if !isEditMode && !showsEmptyLibrary && !showsEmptyLists {
            HStack {
                Spacer()
                Button {
                    guard selectedFilter == .readingLists || !library.importing else { return }
                    if selectedFilter == .readingLists {
                        newListNameInput = ""
                        showCreateListSheet = true
                    } else { showImportSource = true }
                } label: {
                    HStack(spacing: 8) {
                        if library.importing && selectedFilter != .readingLists {
                            ProgressView().tint(Leaf.ink)
                        } else {
                            Image(systemName: "plus")
                                .font(.system(size: primaryActionTextSize, weight: .bold))
                        }
                        Text(selectedFilter == .readingLists ? "Create Reading List" : "Add comics")
                            .font(.archivo(primaryActionTextSize, weight: 700))
                    }
                    .foregroundColor(Leaf.ink)
                    .padding(.horizontal, 18)
                    .padding(.vertical, 12)
                    .frame(minHeight: 44)
                    .background(Leaf.ochre)
                    .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                    .comicShadow(offset: 3, color: .black.opacity(0.75), corner: 12)
                }
                .buttonStyle(.plain)
                .accessibilityLabel(selectedFilter == .readingLists ? Text("Create Reading List") : Text("Add comics"))
                .disabled(library.importing && selectedFilter != .readingLists)
                .background {
                    GeometryReader { geometry in
                        Color.clear.preference(key: LibraryPrimaryActionHeight.self, value: geometry.size.height)
                            .allowsHitTesting(false)
                    }
                }
            }
            .padding(.horizontal, 20)
            .padding(.bottom, 20)
        }
    }

    private var emptyMessage: String {
        if downloadedOnly && readableOfflineComics.isEmpty {
            return String(localized: "No readable local copies yet. Add comics from Files or SMB, or download a Jellyfin comic.")
        }
        if browse.unreadOnly { return String(localized: "No unread comics match this view.") }
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
                        let urls = Array(selectedComics)
                        do {
                            try linkedFiles.setSeries(urls: urls, name: seriesInputName, volume: seriesInputVolume)
                            library.setSeries(urls: urls.filter(\.isFileURL), series: seriesInputName, volume: seriesInputVolume)
                        } catch { linkedFileError = error.localizedDescription }
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

    private var hasListChoices: Bool {
        !library.comics.isEmpty || !linkedFiles.comics.isEmpty || (catalogue.isCurrent(for: jellyfin.connection) && !catalogue.books.isEmpty)
    }

    private var createListSheet: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("List Name", text: $newListNameInput)
                        .accessibilityIdentifier("library.newListName")
                } footer: {
                    Text("Group comics in any order to track a custom reading storyline.")
                }
            }
            .scrollContentBackground(.hidden)
            .background(settings.ground)
            .tint(Leaf.goldText)
            .navigationTitle("Create Reading List")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { showCreateListSheet = false }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button {
                        let name = newListNameInput.trimmingCharacters(in: .whitespacesAndNewlines)
                        guard !name.isEmpty else { return }
                        let preselected = Array(selectedComics)
                        let created = library.createReadingList(name: name, urls: preselected)
                        newListNameInput = ""
                        if isEditMode {
                            isEditMode = false
                            selectedComics.removeAll()
                        } else if preselected.isEmpty && hasListChoices {
                            pendingCreatedListID = created.id
                        }
                        showCreateListSheet = false
                    } label: {
                        Label(selectedComics.isEmpty && hasListChoices ? String(localized: "Create & Add Comics") : String(localized: "Create"), systemImage: "checkmark")
                            .labelStyle(.iconOnly)
                    }
                    .disabled(newListNameInput.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
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
                                createListAfterAddSheet = true
                                newListNameInput = ""
                                showAddToListSheet = false
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
    @ObservedObject private var covers = CoverLoader.shared
    @EnvironmentObject private var settings: LeafSettings
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    let seriesName: String
    let volume: String?
    let issues: [URL]
    var topCoverComic: URL? = nil
    let totalIssues: Int
    let unreadIssues: Int
    let progressFraction: Double
    var columns: Int = 2
    var isEditMode: Bool = false
    var isSelected: Bool = false
    var remoteBooks: [JellyfinBook] = []
    var remoteClient: JellyfinClient? = nil
    var remoteProfileID = ""
    var remoteCopies: [String: URL] = [:]
    var linkedFiles: LinkedFilesStore? = nil
    let onOpenSeries: () -> Void

    // A single owned series cover should show all its artwork, not a cropped stack preview.
    var keepsWholeCover: Bool { totalIssues == 1 && remoteBooks.isEmpty }
    private var frontAspect: CGFloat {
        guard keepsWholeCover, let size = topCoverImage?.size, size.width > 0, size.height > 0 else { return 0.7 }
        return size.width / size.height
    }

    @State private var topIndex: Int = 0
    @State private var didInitTopIndex = false
    @State private var peelOffsetY: CGFloat = 0
    @State private var cardHeight: CGFloat = 220

    @State private var stackCovers = StackCoverImages()
    @State private var coverPixels = 0
    @State private var loadedCoverPixels = 0
    private var topCoverImage: UIImage? { stackCovers.image(for: coverKeys.first) }
    private var layer2CoverImage: UIImage? { stackCovers.image(for: coverKeys.dropFirst().first) }
    private var layer3CoverImage: UIImage? { stackCovers.image(for: coverKeys.dropFirst(2).first) }

    private var count: Int { max(remoteBooks.isEmpty ? issues.count : remoteBooks.count, 1) }

    private var currentTopComic: URL? {
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

    private var coverKeys: [StackCoverKey] {
        if !remoteBooks.isEmpty {
            return (0..<min(3, remoteBooks.count)).map { offset in
                let book = remoteBooks[(topIndex + offset) % remoteBooks.count]
                if let url = remoteCopies[book.id] {
                    return .local(covers.revisions(for: [url])[0])
                }
                return .remote(profile: remoteProfileID, item: book.id, imageTag: book.imageTags?["Primary"] ?? "")
            }
        }
        return [currentTopComic, layer2Comic, layer3Comic].compactMap { $0 }.map { url in
            if let comic = linkedFiles?.comic(for: url) { return .linked(url, linkedFiles?.coverRevisions[comic.id] ?? 0) }
            return .local(covers.revisions(for: [url])[0])
        }
    }

    private var currentReadingState: LibraryReadingState {
        if !remoteBooks.isEmpty {
            let states = remoteBooks.map { book in
                remoteCopies[book.id].map { LibraryReadingState.local(ReadingProgress.get($0)) }
                    ?? .remote(book.userData)
            }
            return .remoteCollection(states)
        }
        return .collection(issues.map { .local(ReadingProgress.get($0)) }, fraction: progressFraction)
    }

    private var currentIssueNum: Int {
        guard !issues.isEmpty || !remoteBooks.isEmpty else { return 1 }
        return (topIndex % count + count) % count + 1
    }

    private var layer2IssueNum: Int {
        guard !issues.isEmpty || !remoteBooks.isEmpty else { return 2 }
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
                            LibraryCoverArtwork(image: layer3CoverImage)
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
                                LibraryCoverArtwork(image: layer2CoverImage)
                            } else {
                                GeneratedCover(title: seriesName, columns: columns)
                            }
                        }
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        .clipped()
                        .overlay(Color.black.opacity(peelProgress >= 0.35 ? 0 : 0.16)
                            .animation(.easeInOut(duration: 0.18), value: peelProgress >= 0.35))

                        stackBadge(issueNumber: layer2IssueNum)


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
                            LibraryCoverArtwork(image: topCoverImage, contentMode: keepsWholeCover ? .fit : .fill)
                        } else {
                            GeneratedCover(title: seriesName, columns: columns)
                        }
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .clipped()

                    stackBadge(issueNumber: currentIssueNum)
                    if !isEditMode { LibraryReadingIndicator(state: currentReadingState) }
                    if !remoteBooks.isEmpty, remoteCopies[remoteBooks[topIndex % count].id] != nil {
                        Image(systemName: "arrow.down.circle.fill")
                            .foregroundStyle(Leaf.paperWhite).padding(6)
                            .background(Leaf.ink.opacity(0.85), in: Circle()).padding(6)
                            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
                    }
                    if let url = currentTopComic, url.scheme == "leaf-files" {
                        Text("Files").leafControlFont(10, weight: 700)
                            .foregroundStyle(Leaf.cream).padding(4)
                            .background(Leaf.ink.opacity(0.85), in: RoundedRectangle(cornerRadius: 3))
                            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading).padding(6)
                    }


                }
                .aspectRatio(frontAspect, contentMode: .fit)
                .background(
                    GeometryReader { geo in
                        (keepsWholeCover ? settings.groundSoft : Leaf.deepInkSurface)
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
            .aspectRatio(frontAspect, contentMode: .fit)
            .padding(.top, totalIssues >= 3 ? 10 : (totalIssues >= 2 ? 5 : 0))
            .contentShape(Rectangle())
            .overlay(
                CoverPeelGesture(onChanged: { value in
                        guard totalIssues > 1, !reduceMotion else { return }
                        let dy = value.translation.height
                        let dx = value.translation.width
                        // Only peel on downward vertical drags so horizontal/upward scrolls pass through
                        if dy > 0 && abs(dy) > abs(dx) {
                            peelOffsetY = min(dy, cardHeight * 1.15)
                        } else if peelOffsetY > 0 {
                            peelOffsetY = max(0, min(dy, cardHeight * 1.15))
                        }
                    }, onEnded: { value in
                        if reduceMotion, value.translation.height > 50 {
                            topIndex = (topIndex + 1) % count
                            return
                        }
                        guard totalIssues > 1, peelOffsetY > 0 else { return }
                        let dy = value.translation.height
                        let predictedDy = value.predictedEndTranslation.height
                        let threshold = cardHeight * 0.35
                        if (dy > threshold && predictedDy >= dy - 120) || (dy >= cardHeight * 0.15 && predictedDy - dy > 120) {
                            withAnimation(.easeOut(duration: 0.18)) {
                                peelOffsetY = cardHeight * 1.15
                            }
                            DispatchQueue.main.asyncAfter(deadline: .now() + 0.18) {
                                topIndex = (topIndex + 1) % max(remoteBooks.isEmpty ? issues.count : remoteBooks.count, 1)
                                peelOffsetY = 0
                            }
                        } else {
                            withAnimation(.spring(response: 0.24, dampingFraction: 0.78)) {
                                peelOffsetY = 0
                            }
                        }
                    })
            )
            .onTapGesture { onOpenSeries() }

            if settings.libraryShowTitles {
            Text(seriesName)
                .leafControlFont(columns >= 4 ? 10.5 : 12, weight: 800)
                .foregroundColor(Leaf.text)
                .lineLimit(dynamicTypeSize.isAccessibilitySize ? nil : 2)
                .padding(.top, columns >= 4 ? 6 : 9)
            }
            if settings.libraryShowDetails {
                Text("\(totalIssues) comics")
                    .leafControlFont(columns >= 4 ? 8.5 : 9.5)
                    .foregroundColor(Leaf.secondaryText)
                    .padding(.top, 2)
            }
        }
        .onAppear {
            if !didInitTopIndex {
                didInitTopIndex = true
                if let topCoverComic, let idx = issues.firstIndex(of: topCoverComic) {
                    topIndex = idx
                }
            }
        }
        .onChange(of: topCoverComic) { newTop in
            if let newTop, let idx = issues.firstIndex(of: newTop) {
                topIndex = idx
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(seriesName)
        .accessibilityValue(Text("\(currentIssueNum) / \(count), \(currentReadingState.label)"))
        .accessibilityAddTraits(.isButton)
        .accessibilityAddTraits(isSelected ? .isSelected : [])
        .accessibilityAction { onOpenSeries() }
        .accessibilityAction(named: Text("Next cover")) { topIndex = (topIndex + 1) % count }
        .libraryCoverResolution($coverPixels)
        .task(id: StackCoverRequest(keys: coverKeys, pixels: coverPixels)) {
            guard coverPixels > 0 else { return }
            let pixels = coverPixels
            let keys = coverKeys
            stackCovers.retain(keys)
            for key in keys where stackCovers.image(for: key) == nil || loadedCoverPixels < pixels {
                let image = await loadCover(for: key)
                guard !Task.isCancelled else { return }
                stackCovers.set(image, for: key)
            }
            loadedCoverPixels = pixels
        }
    }

    private func loadCover(for key: StackCoverKey) async -> UIImage? {
        switch key {
        case .local(let revision): return await CoverLoader.load(for: revision.url, maxPixel: coverPixels).cover
        case .linked(let url, _): return linkedFiles?.comic(for: url).flatMap { linkedFiles?.cover(for: $0) }
        case .remote(let profile, let id, let tag):
            guard profile == remoteProfileID,
                  let book = remoteBooks.first(where: { $0.id == id && ($0.imageTags?["Primary"] ?? "") == tag }),
                  let remoteClient, let data = try? await remoteClient.cover(book, maxWidth: coverPixels) else { return nil }
            return await Task.detached(priority: .utility) { JellyfinBookRow.thumbnail(data, maxPixel: coverPixels) }.value
        }
    }

    @ViewBuilder
    private func stackBadge(issueNumber: Int) -> some View {
        HStack {
            Text(!remoteBooks.isEmpty ? String(localized: "COVER \(issueNumber) OF \(totalIssues)") : totalIssues > 1 ? String(localized: "ISSUE #\(issueNumber) OF \(totalIssues)") : (volume ?? String(localized: "1 ISSUE")))
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

}

/// Reading List Card
struct ReadingListCard: View {
    @ObservedObject private var covers = CoverLoader.shared
    @EnvironmentObject private var settings: LeafSettings
    let list: ReadingListRecord
    let previewUrls: [URL]
    let itemCount: Int
    var columns: Int = 2
    var members: [ReadingListMember]? = nil
    var remoteClient: JellyfinClient? = nil
    var remoteProfileID = ""
    var remoteCopies: [String: URL] = [:]
    var linkedFiles: LinkedFilesStore? = nil
    let onOpenList: () -> Void

    @State private var topIndex: Int = 0
    @State private var peelOffsetY: CGFloat = 0
    @State private var cardHeight: CGFloat = 220

    @State private var stackCovers = StackCoverImages()
    @State private var coverPixels = 0
    @State private var loadedCoverPixels = 0
    private var topCoverImage: UIImage? { stackCovers.image(for: coverSlots.first ?? nil) }
    private var layer2CoverImage: UIImage? { stackCovers.image(for: coverSlots.dropFirst().first ?? nil) }
    private var layer3CoverImage: UIImage? { stackCovers.image(for: coverSlots.dropFirst(2).first ?? nil) }

    private var previewMembers: [ReadingListMember] {
        members ?? previewUrls.map { ReadingListMember(id: $0.lastPathComponent, url: $0, book: nil,
            title: $0.deletingPathExtension().lastPathComponent) }
    }
    private var count: Int { max(previewMembers.count, 1) }
    private var coverSlots: [StackCoverKey?] {
        (0..<min(3, previewMembers.count)).map { offset in
            let member = previewMembers[(topIndex + offset) % count]
            if let url = member.url { return .local(covers.revisions(for: [url])[0]) }
            if let comic = member.linked { return .linked(comic.progressURL, linkedFiles?.coverRevisions[comic.id] ?? 0) }
            if let book = member.book {
                if let copy = remoteCopies[book.id] { return .local(covers.revisions(for: [copy])[0]) }
                return .remote(profile: remoteProfileID, item: book.id, imageTag: book.imageTags?["Primary"] ?? "")
            }
            return nil
        }
    }
    private var coverKeys: [StackCoverKey] { coverSlots.compactMap { $0 } }

    private var currentReadingState: LibraryReadingState {
        var states = previewMembers.map { member -> LibraryReadingState in
            if let url = member.url ?? member.linked?.progressURL { return .local(ReadingProgress.get(url)) }
            if let book = member.book, let copy = remoteCopies[book.id] { return .local(ReadingProgress.get(copy)) }
            return .remote(member.book?.userData)
        }
        if previewMembers.count < itemCount { states.append(.unknown) }
        return .collection(states)
    }

    private var currentItemNum: Int {
        guard !previewMembers.isEmpty else { return 1 }
        return (topIndex % count + count) % count + 1
    }

    private var layer2ItemNum: Int {
        guard !previewMembers.isEmpty else { return 2 }
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
                            LibraryCoverArtwork(image: layer3CoverImage)
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
                                LibraryCoverArtwork(image: layer2CoverImage)
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
                            LibraryCoverArtwork(image: topCoverImage)
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
                .overlay { LibraryReadingIndicator(state: currentReadingState) }
                .overlay(alignment: .topLeading) {
                    if !previewMembers.isEmpty {
                        let member = previewMembers[topIndex % count]
                        if member.linked != nil {
                            Text("Files").leafControlFont(10, weight: 700)
                                .foregroundStyle(Leaf.cream).padding(4)
                                .background(Leaf.ink.opacity(0.85), in: RoundedRectangle(cornerRadius: 3)).padding(6)
                        } else if let book = member.book, remoteCopies[book.id] != nil {
                            Image(systemName: "arrow.down.circle.fill").foregroundStyle(Leaf.paperWhite).padding(6)
                                .background(Leaf.ink.opacity(0.85), in: Circle()).padding(6)
                        } else if member.url == nil && member.book == nil {
                            Text("Unavailable comic").leafControlFont(11, weight: 700).foregroundStyle(Leaf.cream)
                                .padding(6).background(Leaf.ink.opacity(0.85)).padding(6)
                        }
                    }
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
            .overlay(
                CoverPeelGesture(onChanged: { value in
                        guard itemCount > 1 else { return }
                        let dy = value.translation.height
                        let dx = value.translation.width
                        if dy > 0 && abs(dy) > abs(dx) {
                            peelOffsetY = min(dy, cardHeight * 1.15)
                        } else if peelOffsetY > 0 {
                            peelOffsetY = max(0, min(dy, cardHeight * 1.15))
                        }
                    }, onEnded: { value in
                        guard itemCount > 1, peelOffsetY > 0 else { return }
                        let dy = value.translation.height
                        let predictedDy = value.predictedEndTranslation.height
                        let threshold = cardHeight * 0.35
                        if (dy > threshold && predictedDy >= dy - 120) || (dy >= cardHeight * 0.15 && predictedDy - dy > 120) {
                            withAnimation(.easeOut(duration: 0.18)) {
                                peelOffsetY = cardHeight * 1.15
                            }
                            DispatchQueue.main.asyncAfter(deadline: .now() + 0.18) {
                                topIndex = (topIndex + 1) % count
                                peelOffsetY = 0
                            }
                        } else {
                            withAnimation(.spring(response: 0.24, dampingFraction: 0.78)) {
                                peelOffsetY = 0
                            }
                        }
                    })
            )
            .onTapGesture { onOpenList() }

            if settings.libraryShowTitles {
            Text(list.name)
                .font(.archivo(columns >= 4 ? 10.5 : 12, weight: 800))
                .foregroundColor(Leaf.text)
                .lineLimit(2)
                .padding(.top, columns >= 4 ? 6 : 9)
            }

            if settings.libraryShowDetails {
            Text("\(itemCount) items")
                .font(.archivo(columns >= 4 ? 8.5 : 9.5, weight: 600))
                .foregroundColor(Leaf.secondaryText)
                .lineLimit(1)
                .padding(.top, 2)
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(list.name)
        .accessibilityValue(Text("\(currentItemNum) / \(count), \(currentReadingState.label)"))
        .accessibilityAddTraits(.isButton)
        .accessibilityAction { onOpenList() }
        .accessibilityAction(named: Text("Next cover")) { topIndex = (topIndex + 1) % count }
        .libraryCoverResolution($coverPixels)
        .task(id: StackCoverRequest(keys: coverKeys, pixels: coverPixels)) {
            guard coverPixels > 0 else { return }
            let pixels = coverPixels
            let keys = coverKeys
            stackCovers.retain(keys)
            for key in keys where stackCovers.image(for: key) == nil || loadedCoverPixels < pixels {
                let image: UIImage?
                switch key {
                case .local(let revision): image = await CoverLoader.load(for: revision.url, maxPixel: coverPixels).cover
                case .linked(let url, _): image = linkedFiles?.comic(for: url).flatMap { linkedFiles?.cover(for: $0) }
                case .remote(_, let id, let tag):
                    if let book = previewMembers.compactMap(\.book).first(where: {
                        $0.id == id && ($0.imageTags?["Primary"] ?? "") == tag
                    }), let remoteClient, let data = try? await remoteClient.cover(book, maxWidth: coverPixels) {
                        image = await Task.detached(priority: .utility) { JellyfinBookRow.thumbnail(data, maxPixel: coverPixels) }.value
                    } else { image = nil }
                }
                guard !Task.isCancelled else { return }
                stackCovers.set(image, for: key)
            }
            loadedCoverPixels = pixels
        }
    }
}

/// A provider-linked file is a separate shelf item, never a downloaded or imported comic.
struct LinkedFileCard: View {
    @EnvironmentObject private var settings: LeafSettings
    let comic: LinkedComic
    @ObservedObject var store: LinkedFilesStore
    var columns = 2
    @State private var cover: UIImage?

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            ZStack(alignment: .bottom) {
            Group {
                if let cover { LibraryCoverArtwork(image: cover) }
                else { GeneratedCover(title: comic.title, columns: columns) }
            }
            .aspectRatio(0.7, contentMode: .fit)
            .clipShape(RoundedCornerShape(cornerRadius: 4))
            LibraryReadingIndicator(state: .local(ReadingProgress.get(comic.progressURL)))
            Text("Files").leafControlFont(10, weight: 700)
                .foregroundStyle(Leaf.cream).padding(4)
                .background(Leaf.ink.opacity(0.85), in: RoundedRectangle(cornerRadius: 3))
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading).padding(6)
            }
            if settings.libraryShowTitles {
            Text(comic.title)
                .leafControlFont(12, weight: 800)
                .foregroundColor(Leaf.text)
                .lineLimit(2)
            }
            if settings.libraryShowDetails {
                Text("\(comic.pageCount) pages").leafControlFont(11).foregroundColor(Leaf.secondaryText)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(comic.title)
        .accessibilityValue(Text("\(String(localized: "Files")), \(LibraryReadingState.local(ReadingProgress.get(comic.progressURL)).label)"))
        .task(id: store.coverRevisions[comic.id, default: 0]) { cover = store.cover(for: comic) }
    }
}

/// A single comic in the grid
struct ComicCard: View {
    @ObservedObject private var covers = CoverLoader.shared
    @EnvironmentObject private var settings: LeafSettings
    let url: URL
    let progress: Progress?
    var columns: Int = 2
    var isEditMode: Bool = false
    var isSelected: Bool = false
    var coverTransition: Namespace.ID? = nil
    @State private var coverPixels = 0
    @State private var cover: UIImage?
    @State private var pages = 0

    private var title: String { ComicMetadataStore.displayFilename(for: url) }
    private var pageCount: Int { progress?.total ?? pages }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            ZStack(alignment: .bottom) {
                Group {
                    if let cover {
                        LibraryCoverArtwork(image: cover)
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

                if !isEditMode { LibraryReadingIndicator(state: .local(progress)) }
            }
            .aspectRatio(0.7, contentMode: .fit)
            .background(settings.groundSoft)
            .clipShape(RoundedCornerShape(cornerRadius: 4))
            .comicCoverSource(url: url, namespace: coverTransition)
            .overlay(
                RoundedCornerShape(cornerRadius: 4)
                    .stroke(isSelected ? Leaf.ochre : Color.clear, lineWidth: isSelected ? 3 : (columns >= 4 ? 2 : 3))
            )
            .shadow(color: .black.opacity(0.24), radius: columns >= 4 ? 3 : 5, y: 3)

            if settings.libraryShowTitles {
            Text(title)
                .font(.archivo(columns >= 4 ? 10 : 12, weight: 800)).foregroundColor(Leaf.text)
                .lineLimit(2).multilineTextAlignment(.leading)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.top, columns >= 4 ? 6 : 9)
            }
            if settings.libraryShowDetails {
            Text("\(pageCount) pages")
                .font(.archivo(columns >= 4 ? 8.5 : 9.5)).foregroundColor(Leaf.secondaryText)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.top, 2)
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(title)
        .accessibilityValue(LibraryReadingState.local(progress).label)
        .accessibilityAddTraits(isSelected ? .isSelected : [])
        .libraryCoverResolution($coverPixels)
        .task(id: StackCoverRequest(keys: covers.revisions(for: [url]).map { .local($0) }, pixels: coverPixels)) {
            guard coverPixels > 0 else { return }
            let loaded = await CoverLoader.load(for: url, maxPixel: coverPixels)
            guard !Task.isCancelled else { return }
            cover = loaded.cover
            pages = loaded.pageCount
        }
    }
}

/// The card proposes the artwork bounds; a wide image must not enlarge its layout or hit region.
struct LibraryCoverArtwork: View {
    let image: UIImage
    var contentMode: ContentMode = .fill

    var body: some View {
        Color.clear
            .overlay {
                Image(uiImage: image)
                    .resizable()
                    .aspectRatio(contentMode: image.size.width > image.size.height ? .fit : contentMode)
            }
            .clipped()
            .contentShape(Rectangle())
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
            .halftone(color: Leaf.ochre, alpha: 0.10)
            .overlay(
                RoundedCornerShape(cornerRadius: 4)
                    .stroke(Leaf.secondaryText, style: StrokeStyle(lineWidth: strokeWidth, dash: [14, 10]))
            )
            .clipShape(RoundedCornerShape(cornerRadius: 4))
            .contentShape(Rectangle())
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

/// Keep controls at their natural width when space permits, otherwise wrap text in a vertical stack.
private struct LibraryAdaptiveControls<Content: View>: View {
    @ViewBuilder let content: Content

    var body: some View {
        ViewThatFits(in: .horizontal) {
            HStack(spacing: 8) { content }
                .fixedSize(horizontal: true, vertical: false)
            VStack(alignment: .leading, spacing: 8) { content }
                .fixedSize(horizontal: false, vertical: true)
        }
    }
}
