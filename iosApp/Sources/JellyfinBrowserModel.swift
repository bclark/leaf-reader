import SwiftUI
import LeafShared

@MainActor
final class JellyfinBrowserModel: ObservableObject {
    @Published var connection: JellyfinConnection?
    @Published var items: [JellyfinBook] = []
    @Published var folders: [JellyfinBook] = []
    @Published var message: String?
    @Published var busy = false
    @Published var activity = ""
    @Published private(set) var activeItemID: String?
    @Published var transferred: Int64 = 0
    @Published var total: Int64 = -1
    @Published var hasMore = false
    @Published var downloaded: [String: URL] = [:]
    @Published var needsSignIn = false
    @Published private(set) var syncConflict: JellyfinSync.Conflict?
    @Published private(set) var syncedItemID: String?
    private(set) var client: JellyfinClient?
    private var task: Task<Void, Never>?
    private var offset = 0
    private let defaults: UserDefaults
    private let credentials: any SmbCredentialStore
    private let imports: JellyfinImports
    private let progressStore: JellyfinProgressStore
    private let deviceID: String
    private let snapshots: MetadataSnapshotStore

    init(defaults: UserDefaults = .standard,
         credentials: any SmbCredentialStore = SmbKeychainCredentials(service: "net.digitalbc.leafreader.jellyfin"),
         imports: JellyfinImports = .shared,
         progressStore: JellyfinProgressStore = .shared,
         snapshotDirectory: URL? = nil) {
        self.defaults = defaults
        snapshots = .scoped(defaults: defaults, directory: snapshotDirectory)
        self.credentials = credentials
        self.imports = imports
        self.progressStore = progressStore
        let device = defaults.string(forKey: "leaf.jellyfin.device") ?? UUID().uuidString
        defaults.set(device, forKey: "leaf.jellyfin.device")
        deviceID = device
        if let data = defaults.data(forKey: "leaf.jellyfin.profile") {
            connection = try? JSONDecoder().decode(JellyfinConnection.self, from: data)
        }
        if let saved = connection {
            do {
                let validated = try JellyfinConnection.baseURL(saved.baseURL.absoluteString, allowHTTP: saved.allowsHTTP)
                if let token = try credentials.password(profileID: saved.id) {
                    client = JellyfinClient(baseURL: validated, token: token, deviceID: device)
                } else { needsSignIn = true }
            } catch { needsSignIn = true; message = JellyfinError.credentials.localizedDescription }
        }
    }
    deinit { task?.cancel() }

    private func run(itemID: String? = nil, _ action: @escaping @MainActor () async throws -> Void) {
        guard !busy else { return }
        activeItemID = itemID
        busy = true; message = nil; transferred = 0; total = -1
        task = Task {
            defer { busy = false; task = nil; activity = ""; activeItemID = nil }
            do { try await action() }
            catch is CancellationError { }
            catch {
                if case JellyfinError.authentication = error { needsSignIn = true; client = nil }
                message = (error as? JellyfinError)?.localizedDescription ??
                    (error as? ComicImportError)?.localizedDescription ?? String(localized: "Could not save this comic. Check free space and try again.")
            }
        }
    }

    func signIn(url: String, username: String, password: String, allowHTTP: Bool,
                onSuccess: (@MainActor () -> Void)? = nil) {
        run {
            self.activity = String(localized: "Signing in…")
            let base = try JellyfinConnection.baseURL(url, allowHTTP: allowHTTP)
            let unauthenticated = JellyfinClient(baseURL: base, deviceID: self.deviceID)
            let result = try await unauthenticated.login(username: username, password: password)
            try Task.checkCancellation()
            let old = self.connection
            let same = old?.baseURL == base && old?.userID == result.User.Id && old?.serverID == result.ServerId
            let profile = JellyfinConnection(id: same ? (old?.id ?? UUID().uuidString) : UUID().uuidString, baseURL: base,
                userID: result.User.Id, serverID: result.ServerId, username: result.User.Name ?? username, allowsHTTP: allowHTTP)
            do { try self.credentials.save(password: result.AccessToken, profileID: profile.id) }
            catch { throw JellyfinError.credentials }
            if let old, old.id != profile.id { try self.credentials.delete(profileID: old.id) }
            self.defaults.set(try JSONEncoder().encode(profile), forKey: "leaf.jellyfin.profile")
            self.connection = profile
            self.client = JellyfinClient(baseURL: base, token: result.AccessToken, deviceID: self.deviceID)
            self.needsSignIn = false
            self.items = []; self.folders = []; self.downloaded = [:]
            // The authenticated connection is committed; finish its UI outcome before starting cancellable browsing.
            onSuccess?()
            try await self.load(reset: true)
        }
    }

    /// A detail response belongs to the exact session that requested it, even after suspension.
    func details(_ item: JellyfinBook) async throws -> (JellyfinBook, JellyfinItemDetails) {
        guard let connection, let client, !needsSignIn else { throw JellyfinError.authentication }
        let result = try await client.details(connection: connection, itemID: item.id)
        try Task.checkCancellation()
        guard self.connection == connection, self.client === client, !needsSignIn else {
            throw CancellationError()
        }
        let details = try result.shared(connection: connection)
        let key = detailKey(connection, itemID: item.id)
        do {
            try await snapshots.write(keys: [key]) { try JSONEncoder().encode(result) }
            defaults.removeObject(forKey: key)
        } catch { /* Metadata caching must not prevent reading a successful live response. */ }
        try Task.checkCancellation()
        guard self.connection == connection, self.client === client, !needsSignIn else { throw CancellationError() }
        return (result.book, details)
    }

    private func detailKey(_ connection: JellyfinConnection, itemID: String) -> String {
        "leaf.jellyfin.details." + [connection.id, connection.serverID, connection.userID, itemID]
            .map { "\($0.count):\($0)" }.joined()
    }

    func cachedDetails(_ item: JellyfinBook) async -> (JellyfinBook, JellyfinItemDetails)? {
        guard let connection else { return nil }
        let saved = await snapshots.restore(JellyfinDetails.self,
            key: detailKey(connection, itemID: item.id), defaults: defaults)
        guard self.connection == connection, !Task.isCancelled, let saved,
              saved.book.id == item.id,
              let details = try? saved.shared(connection: connection, fromCache: true) else { return nil }
        return (saved.book, details)
    }

    func refresh() { run { try await self.load(reset: true) } }
    func more() { run { try await self.load(reset: false) } }
    func enter(_ item: JellyfinBook) { run {
        let old = self.folders
        self.folders.append(item)
        do { try await self.load(reset: true) } catch { self.folders = old; throw error }
    } }
    func up() { run {
        let old = self.folders
        _ = self.folders.popLast()
        do { try await self.load(reset: true) } catch { self.folders = old; throw error }
    } }
    private func load(reset: Bool) async throws {
        guard let connection, let client, !needsSignIn else { return }
        activity = String(localized: "Loading Jellyfin…")
        let start = reset ? 0 : offset
        let page = try await client.page(userID: connection.userID, parentID: folders.last?.id, start: start)
        try Task.checkCancellation()
        var seen = Set(reset ? [] : items.map(\.id))
        let unique = page.items.filter { seen.insert($0.id).inserted }
        if !reset, unique.isEmpty, !page.items.isEmpty { throw JellyfinError.response }
        items = (reset ? [] : items) + unique
        offset = start + page.items.count
        hasMore = !folders.isEmpty && !page.items.isEmpty && (page.total.map { offset < $0 } ?? (page.items.count == 100))
    }
    func restoreDownloads(library: LibraryStore) async {
        do {
            let records = try await imports.reconcile(localFiles: library.comics)
            for record in records {
                if let reference = JellyfinListReference(serverID: record.serverID, userID: record.userID, itemID: record.itemID) {
                    ReadingListStore.linkRemote(reference, filename: record.filename)
                }
                if let url = library.comics.first(where: { $0.lastPathComponent == record.filename }),
                   ComicMetadataStore.get(url)?.userModified != true {
                    var metadata = ComicMetadataStore.get(url) ?? ComicMetadataRecord()
                    metadata.title = record.title
                    ComicMetadataStore.set(url, metadata: metadata)
                }
            }
            library.refresh()
            ReadingProgress.flushSync()
            do {
                try await progressStore.recoverLocalEdits(imports: records)
            } catch {
                message = String(localized: "Could not prepare Jellyfin progress. Your local comics are still available. Try opening Jellyfin again.")
            }
            guard let connection else { return }
            let urls = Dictionary(library.comics.map { ($0.lastPathComponent, $0) }, uniquingKeysWith: { first, _ in first })
            downloaded = Dictionary(records.filter {
                $0.belongs(to: connection)
            }.compactMap { record in urls[record.filename].map { (record.itemID, $0) } }, uniquingKeysWith: { _, last in last })
        } catch { message = String(localized: "Could not recover Jellyfin download records. Your local comics are still available.") }
    }

    func importBook(_ item: JellyfinBook, library: LibraryStore, onReady: ((URL) -> Void)? = nil) {
        guard item.supported, downloaded[item.id] == nil, let connection, let client, !needsSignIn else { return }
        run(itemID: item.id) {
            await self.restoreDownloads(library: library)
            if let existing = self.downloaded[item.id] { onReady?(existing); return }
            self.activity = String(localized: "Downloading comic…")
            let temporary = FileManager.default.temporaryDirectory
            // Unknown length: reserve room for the maximum download before starting.
            try JellyfinImportLimits.checkSpace(at: temporary, required: JellyfinImportLimits.compressed * 2)
            let file = try await client.download(client.bookRequest(item.id)) { bytes, total in
                Task { @MainActor [weak self] in self?.transferred = bytes; self?.total = total }
            }
            defer { try? FileManager.default.removeItem(at: file) }
            self.activity = String(localized: "Validating comic…")
            self.total = -1
            self.transferred = 0
            let validation = Task.detached { try JellyfinImportLimits.validate(file, fileExtension: item.fileExtension) }
            let checksum = try await withTaskCancellationHandler { try await validation.value } onCancel: { validation.cancel() }
            try Task.checkCancellation()
            let filename = item.importFilename
            let title = (filename as NSString).deletingPathExtension
            let journal = await self.imports.publicationJournal()
            let published: URL
            do {
                self.activity = String(localized: "Importing comic…")
                published = try await library.importComicAwaitingCompletion(
                    from: file, originalName: filename, collision: .keepBoth,
                    beforePublish: { destination in
                        let record = JellyfinImports.Record(profileID: connection.id, serverID: connection.serverID,
                            userID: connection.userID, itemID: item.id, filename: destination.lastPathComponent,
                            checksum: checksum, title: title, versionHint: item.dateModified, pending: true)
                        try JellyfinImports.stage(record, at: journal)
                    })
            } catch {
                try? FileManager.default.removeItem(at: journal)
                throw error
            }
            // No cancellation check after atomic publication. Complete the journal and metadata.
            var metadata = ComicMetadataStore.get(published) ?? ComicMetadataRecord()
            metadata.title = title
            ComicMetadataStore.set(published, metadata: metadata)
            try await self.imports.finishPublication(at: journal)
            let identity = JellyfinProgressStore.Identity(serverID: connection.serverID,
                userID: connection.userID, itemID: item.id)
            let total = try await Task.detached(priority: .utility) { try CbzArchive(url: published).pageCount }.value
            if let retained = try await self.progressStore.rebindRetainedCopy(identity: identity,
                    filename: published.lastPathComponent, checksum: checksum, pageCount: total),
               let revision = retained.sourceRevision {
                _ = ReadingProgress.applyRemote(published, expected: ReadingProgress.get(published),
                    position: retained.position, revision: revision)
            }
            guard self.connection == connection, self.client === client else { return }
            self.downloaded[item.id] = published
            ReadingListStore.linkRemote(JellyfinListReference(connection: connection, itemID: item.id),
                filename: published.lastPathComponent)
            library.refresh()
            if !Task.isCancelled { onReady?(published) }
        }
    }
    /// Explicit foreground sync; offline reading never starts this network operation.
    func syncProgress(_ item: JellyfinBook, library: LibraryStore, choice: JellyfinSync.Choice? = nil) {
        guard !busy, let connection, let client, !needsSignIn else { return }
        run(itemID: item.id) {
            self.syncedItemID = nil
            self.syncConflict = nil
            self.activity = String(localized: "Syncing reading position…")
            do {
                let validated = try await self.imports.reconcile(localFiles: library.comics)
                guard self.connection == connection, self.client === client else { throw CancellationError() }
                let matches = validated.filter { $0.belongs(to: connection) && $0.itemID == item.id }
                guard matches.count == 1, let imported = matches.first,
                      let url = library.comics.first(where: { $0.lastPathComponent == imported.filename }) else {
                    throw JellyfinSync.SyncError.changed
                }
                let fileVersion = try JellyfinLocalVersion(url)
                let pageCount = try await Task.detached(priority: .utility) { try CbzArchive(url: url).pageCount }.value
                ReadingProgress.resyncTotalIfNeeded(url, actualTotal: pageCount)
                ReadingProgress.flushSync()
                try await self.progressStore.recoverLocalEdits(imports: validated)
                let outcome = try await JellyfinSync.synchronize(connection: connection, itemID: item.id,
                    url: url, pageCount: pageCount, store: self.progressStore, transport: client, choice: choice) {
                    guard self.connection == connection, self.client === client, !self.needsSignIn,
                          library.comics.contains(url), try JellyfinLocalVersion(url) == fileVersion else {
                        throw JellyfinSync.SyncError.changed
                    }
                }
                switch outcome {
                case .synced:
                    self.syncedItemID = item.id
                    self.message = String(localized: "Reading position synced.")
                    library.refresh()
                case .conflict(let conflict):
                    self.syncConflict = conflict
                    self.message = nil
                case .unavailable:
                    self.message = String(localized: "Jellyfin did not return a complete reading position. Your local position is unchanged.")
                }
            } catch is CancellationError {
                self.message = String(localized: "Sync stopped. Unconfirmed reading changes remain pending.")
            } catch {
                if case JellyfinError.authentication = error { self.needsSignIn = true; self.client = nil }
                self.message = (error as? LocalizedError)?.errorDescription ??
                    String(localized: "Could not sync reading progress. Your local reading position is still available. Try again.")
            }
        }
    }

    func clearSyncStatus() {
        if syncedItemID != nil { message = nil }
        syncedItemID = nil
        syncConflict = nil
    }
    func dismissSyncConflict() { syncConflict = nil }
    func cancel() { task?.cancel() }
    func waitForIdle() async { await task?.value }
    func disconnect() async {
        cancel(); await waitForIdle()
        do {
            if let connection { try credentials.delete(profileID: connection.id) }
            defaults.removeObject(forKey: "leaf.jellyfin.profile")
            connection = nil; client = nil; items = []; folders = []; downloaded = [:]; needsSignIn = false; message = nil
            syncConflict = nil; syncedItemID = nil
        } catch { message = JellyfinError.credentials.localizedDescription }
    }
}
