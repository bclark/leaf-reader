import SwiftUI
import LeafShared

struct JellyfinDetailView: View {
    let item: JellyfinBook
    @ObservedObject var model: JellyfinBrowserModel
    var creatorLibraryID: String? = nil
    var creatorCachedBooks: [JellyfinBook] = []
    @EnvironmentObject private var library: LibraryStore
    @EnvironmentObject private var settings: LeafSettings
    @State private var book: JellyfinBook?
    @State private var details: JellyfinItemDetails?
    @State private var error: String?
    @State private var loading = true
    @State private var retry = 0
    @State private var confirmRemoval = false
    @State private var openComic: OpenComic?
    @State private var readTask: Task<Void, Never>?
    @Namespace private var readerNamespace
    @State private var sourceConnection: JellyfinConnection?

    init(item: JellyfinBook, model: JellyfinBrowserModel, creatorLibraryID: String? = nil,
         creatorCachedBooks: [JellyfinBook] = []) {
        self.item = item
        self.model = model
        self.creatorLibraryID = creatorLibraryID
        self.creatorCachedBooks = creatorCachedBooks
        self._sourceConnection = State(initialValue: model.connection)
    }

    var body: some View {
        if model.connection != sourceConnection {
            Text("This comic belongs to another Jellyfin session. Return to your library.")
        } else {
            detailContent
        }
    }

    private var detailContent: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 28) {
                JellyfinDetailHero(item: book ?? item, details: details, client: model.client,
                    profileID: model.connection?.id ?? "", localURL: model.downloaded[item.id]) {
                    credits
                    readingControls
                }
                VStack(alignment: .leading, spacing: 24) {
                    if let details {
                        if details.fromCache {
                            Text("Saved details. Connect to refresh.")
                                .font(.footnote).foregroundStyle(Leaf.secondaryText)
                        }
                        if let overview = details.overview, !overview.isEmpty {
                            VStack(alignment: .leading, spacing: 10) {
                                Text("Synopsis").leafControlFont(20, weight: 800)
                                    .accessibilityAddTraits(.isHeader)
                                Text(overview).leafControlFont(16, weight: 400)
                                    .textSelection(.enabled)
                            }
                        }
                        if !details.genres.isEmpty {
                            Text(details.genres.formatted(.list(type: .and)))
                                .leafControlFont(14).foregroundStyle(Leaf.secondaryText)
                        }
                    }
                    if loading { ProgressView("Loading Jellyfin…") }
                    else if let error {
                        VStack(alignment: .leading, spacing: 8) {
                            Text(error)
                            Button("Retry") { retry += 1 }.frame(minHeight: 44)
                        }
                    }
                    Divider()
                    managementControls
                }
                .padding(.horizontal, 24)
                .padding(.bottom, 28)
            }
            .frame(maxWidth: 960, alignment: .leading)
            .frame(maxWidth: .infinity)
        }
        .foregroundStyle(Leaf.text)
        .alert("Remove comic?", isPresented: $confirmRemoval) {
            Button("Cancel", role: .cancel) {}
            Button("Remove", role: .destructive) {
                if let url = model.downloaded[item.id] {
                    Task {
                        await library.deletePreservingJellyfinProgress([url])
                        await model.restoreDownloads(library: library)
                    }
                }
            }
        } message: {
            Text("This removes the imported copy. The original file is untouched.")
        }
        .background(settings.ground)
        .tint(Leaf.goldText)
        .navigationTitle(item.name)
        .navigationBarTitleDisplayMode(.inline)
        .task(id: [String(retry), model.connection?.id ?? ""]) {
            model.clearSyncStatus()
            loading = true
            error = nil
            let saved = await model.cachedDetails(item)
            guard !Task.isCancelled else { return }
            book = saved?.0
            details = saved?.1
            await model.restoreDownloads(library: library)
            do {
                let (book, details) = try await model.details(item)
                try Task.checkCancellation()
                self.book = book
                self.details = details
            } catch is CancellationError {
                return
            } catch {
                guard !Task.isCancelled else { return }
                self.error = (error as? JellyfinError)?.localizedDescription ?? JellyfinError.network.localizedDescription
            }
            loading = false
        }
        .onDisappear {
            readTask?.cancel()
            if model.activeItemID == item.id { model.cancel() }
        }
        .comicReaderPresentation(item: $openComic, namespace: readerNamespace) { library.refresh() }
    }


    @ViewBuilder private var credits: some View {
        if let details, !details.people.isEmpty {
            VStack(alignment: .leading, spacing: 4) {
                ForEach(Array(details.people.enumerated()), id: \.offset) { _, person in
                    let role = [person.type, person.role].compactMap { $0 }
                        .filter { !$0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
                        .reduce(into: [String]()) { if !$0.contains($1) { $0.append($1) } }
                        .joined(separator: " · ")
                    NavigationLink {
                        JellyfinCreatorView(person: person,
                            libraryID: creatorLibraryID ?? model.folders.first?.id,
                            cachedBooks: creatorCachedBooks, sourceConnection: sourceConnection, browser: model)
                    } label: {
                        VStack(alignment: .leading, spacing: 3) {
                            Text(role.isEmpty ? String(localized: "Creator") : role)
                                .leafControlFont(12).foregroundStyle(Leaf.secondaryText)
                            HStack(alignment: .firstTextBaseline, spacing: 8) {
                                Text(person.name).underline().leafControlFont(17, weight: 700)
                                    .fixedSize(horizontal: false, vertical: true)
                                Image(systemName: "chevron.right").font(.caption)
                            }
                        }
                        .frame(maxWidth: .infinity, minHeight: 44, alignment: .leading)
                        .padding(.vertical, 4)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(Text("Find comics by \(person.name), \(role.isEmpty ? String(localized: "Creator") : role)"))
                }
            }
        }
    }

    @ViewBuilder private var readingControls: some View {
        if model.activeItemID == item.id {
            JellyfinActivityView(activity: model.activity, transferred: model.transferred,
                total: model.total, cancel: { readTask?.cancel(); model.cancel() })
        } else if let local = model.downloaded[item.id] {
            Button(action: read) {
                Label(ReadingProgress.get(local).map { $0.page > 0 && !$0.isCompleted } == true
                    ? String(localized: "Continue") : String(localized: "Read"), systemImage: "book")
                    .frame(maxWidth: .infinity, minHeight: 44)
            }
            .buttonStyle(JellyfinDetailReadStyle())
            Text("Read offline").leafControlFont(13).foregroundStyle(Leaf.secondaryText)
            if let progress = ReadingProgress.get(local), progress.total > 0 {
                Text("Page \(min(progress.total, max(1, progress.page + 1))) of \(progress.total)")
                    .leafControlFont(14)
                ProgressView(value: min(1, max(0, Double(progress.page + 1) / Double(progress.total))))
                    .tint(Leaf.goldText)
            }
        } else if (book ?? item).supported {
            Button {
                model.importBook(book ?? item, library: library) { openComic = OpenComic(url: $0) }
            } label: {
                Label("Read", systemImage: "book").frame(maxWidth: .infinity, minHeight: 44)
            }
            .buttonStyle(JellyfinDetailReadStyle())
            .disabled(model.busy || model.needsSignIn)
            Text("Downloads the complete comic and keeps it available offline.")
                .leafControlFont(13).foregroundStyle(Leaf.secondaryText)
        } else {
            Text("Unsupported format").foregroundStyle(Leaf.secondaryText)
        }
    }

    @ViewBuilder private var managementControls: some View {
        VStack(alignment: .leading, spacing: 12) {
            if let connection = model.connection {
                JellyfinAddToListMenu(book: book ?? item, connection: connection, library: library)
                    .frame(minHeight: 44)
            }
            if model.downloaded[item.id] != nil {
                Button("Remove", role: .destructive) { confirmRemoval = true }
                    .frame(minHeight: 44).disabled(model.busy)
                Button("Sync reading position") { model.syncProgress(book ?? item, library: library) }
                    .frame(minHeight: 44).disabled(model.busy || model.needsSignIn)
                Text("Sync exchanges this comic’s reading position with your Jellyfin account.")
                    .font(.footnote).foregroundStyle(Leaf.secondaryText)
            } else if (book ?? item).supported {
                Button("Download") { model.importBook(book ?? item, library: library) }
                    .frame(minHeight: 44).disabled(model.busy || model.needsSignIn)
            }
            if let conflict = model.syncConflict, conflict.itemID == item.id {
                JellyfinSyncConflictView(conflict: conflict, choose: { useDevice in
                    model.syncProgress(book ?? item, library: library,
                        choice: .init(conflict: conflict, useDevice: useDevice))
                }, deferChoice: { model.dismissSyncConflict() }).disabled(model.busy)
            }
            if let message = model.message { Text(message).foregroundStyle(Leaf.secondaryText) }
        }
    }

    private func read() {
        model.clearSyncStatus()
        readTask?.cancel()
        readTask = Task {
            await model.restoreDownloads(library: library)
            guard !Task.isCancelled, let url = model.downloaded[item.id] else { return }
            openComic = OpenComic(url: url)
        }
    }
}

struct JellyfinDetailReadStyle: ButtonStyle {
    @Environment(\.isEnabled) private var isEnabled

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .leafControlFont(18, weight: 800)
            .foregroundStyle(Leaf.ink)
            .padding(.horizontal, 16).padding(.vertical, 6)
            .background(Leaf.ochre.opacity(configuration.isPressed ? 0.8 : 1),
                        in: RoundedRectangle(cornerRadius: 8))
            .opacity(isEnabled ? 1 : 0.5)
    }
}

/// One bounded cover decode supplies both the artwork and the static hero atmosphere.
struct JellyfinDetailHero<Actions: View>: View {
    let item: JellyfinBook
    let details: JellyfinItemDetails?
    let client: JellyfinClient?
    let profileID: String
    let localURL: URL?
    @ViewBuilder let actions: Actions
    @State private var cover: UIImage?
    var body: some View {
        JellyfinCinematicHero(item: item, details: details, cover: cover) { actions }
        .task(id: [profileID, item.id, item.imageTags?["Primary"] ?? "", localURL?.path ?? ""]) {
            cover = nil
            var image: UIImage?
            if let localURL { image = await CoverLoader.load(for: localURL).cover }
            if image == nil, let client, let data = try? await client.cover(item, maxWidth: 640) {
                image = await Task.detached(priority: .utility) {
                    JellyfinBookRow.thumbnail(data, maxPixel: 640)
                }.value
            }
            guard !Task.isCancelled else { return }
            cover = image
        }
    }
}

struct JellyfinCinematicHero<Actions: View>: View {
    let item: JellyfinBook
    let details: JellyfinItemDetails?
    let cover: UIImage?
    @ViewBuilder let actions: Actions
    @EnvironmentObject private var settings: LeafSettings
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @Environment(\.accessibilityReduceTransparency) private var reduceTransparency

    var body: some View {
        Group {
            if dynamicTypeSize.isAccessibilitySize {
                VStack(alignment: .leading, spacing: 24) { artwork; information }
            } else {
                ViewThatFits(in: .horizontal) {
                    HStack(alignment: .top, spacing: 32) {
                        artwork.frame(width: 220)
                        information.frame(width: 360)
                        Spacer(minLength: 0)
                    }
                    VStack(alignment: .leading, spacing: 24) { artwork; information }
                }
            }
        }
        .padding(24)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background {
            GeometryReader { geometry in
                ZStack {
                    settings.ground
                    if let cover, !reduceTransparency {
                        Image(uiImage: cover).resizable().scaledToFill()
                            .frame(width: geometry.size.width, height: geometry.size.height)
                            .blur(radius: 24)
                            .opacity(0.18)
                        LinearGradient(colors: [settings.ground.opacity(0.65), settings.ground],
                            startPoint: .topTrailing, endPoint: .bottomLeading)
                    }
                }
                .clipped()
            }
        }
    }

    private var artwork: some View {
        Group {
            if let cover {
                Image(uiImage: cover).resizable().scaledToFit()
            } else {
                GeneratedCover(title: item.name, columns: 2).aspectRatio(0.7, contentMode: .fit)
            }
        }
        .frame(maxWidth: cover == nil ? 140 : 220, maxHeight: cover == nil ? 200 : 330)
        .clipShape(RoundedRectangle(cornerRadius: 4))
        .shadow(color: .black.opacity(0.25), radius: 10, y: 6)
        .accessibilityHidden(true)
    }

    private var information: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text(item.name).font(.custom("Anton-Regular", size: 30, relativeTo: .title))
                .fixedSize(horizontal: false, vertical: true).accessibilityAddTraits(.isHeader)
            if let series = details?.item.seriesName ?? item.seriesName, !series.isEmpty {
                Text(series).leafControlFont(16, weight: 700)
                    .fixedSize(horizontal: false, vertical: true)
            }
            VStack(alignment: .leading, spacing: 4) {
                if let number = item.indexNumber, number.isFinite {
                    Text("Issue \(number.formatted())").leafControlFont(14)
                }
                if let year = details?.item.productionYear {
                    Text(String(year.intValue)).leafControlFont(14)
                }
            }
            actions
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .fixedSize(horizontal: false, vertical: true)
        .padding(16)
        .background(settings.ground, in: RoundedRectangle(cornerRadius: 8))
    }
}

struct JellyfinSyncConflictView: View {
    let conflict: JellyfinSync.Conflict
    let choose: (Bool) -> Void
    let deferChoice: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Choose a reading position").font(.headline)
            VStack(alignment: .leading, spacing: 4) {
                Text("This device: page \(min(conflict.local?.page ?? 0, max(0, (conflict.local?.total ?? 1) - 1)) + 1)")
                if conflict.local?.isCompleted == true { Text("Finished") } else { Text("Not finished") }
            }
            .accessibilityElement(children: .combine)
            VStack(alignment: .leading, spacing: 4) {
                Text("Jellyfin: page \((conflict.remote.positionTicks ?? 0) / JellyfinProgress.shared.pageToTicks(pageIndex: 1) + 1)")
                if conflict.remote.played == true { Text("Finished") } else { Text("Not finished") }
            }
            .accessibilityElement(children: .combine)
            Button("Use this device’s position") { choose(true) }
                .frame(minHeight: 44)
            Button("Use Jellyfin’s position") { choose(false) }
                .frame(minHeight: 44)
            Button("Keep both for now", action: deferChoice)
                .frame(minHeight: 44)
        }
        .buttonStyle(.borderless)
        .fixedSize(horizontal: false, vertical: true)
    }
}
