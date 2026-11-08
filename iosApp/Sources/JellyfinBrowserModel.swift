import SwiftUI

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
    private(set) var client: JellyfinClient?
    private var task: Task<Void, Never>?
    private var offset = 0
    private let defaults: UserDefaults
    private let credentials = SmbKeychainCredentials(service: "net.digitalbc.leafreader.jellyfin")
    private let imports = JellyfinImports.shared
    private let deviceID: String

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
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

    func signIn(url: String, username: String, password: String, allowHTTP: Bool) {
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
            try await self.load(reset: true)
        }
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
                if let url = library.comics.first(where: { $0.lastPathComponent == record.filename }),
                   ComicMetadataStore.get(url)?.userModified != true {
                    var metadata = ComicMetadataStore.get(url) ?? ComicMetadataRecord()
                    metadata.title = record.title
                    ComicMetadataStore.set(url, metadata: metadata)
                }
            }
            guard let connection else { return }
            let urls = Dictionary(library.comics.map { ($0.lastPathComponent, $0) }, uniquingKeysWith: { first, _ in first })
            downloaded = Dictionary(records.filter {
                $0.profileID == connection.id && $0.serverID == connection.serverID && $0.userID == connection.userID
            }.compactMap { record in urls[record.filename].map { (record.itemID, $0) } }, uniquingKeysWith: { _, last in last })
        } catch { message = String(localized: "Could not recover Jellyfin download records. Your local comics are still available.") }
    }

    func importBook(_ item: JellyfinBook, library: LibraryStore) {
        guard item.supported, downloaded[item.id] == nil, let connection, let client, !needsSignIn else { return }
        run(itemID: item.id) {
            await self.restoreDownloads(library: library)
            if self.downloaded[item.id] != nil { return }
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
            self.downloaded[item.id] = published
        }
    }
    func cancel() { task?.cancel() }
    func waitForIdle() async { await task?.value }
    func disconnect() async {
        cancel(); await waitForIdle()
        do {
            if let connection { try credentials.delete(profileID: connection.id) }
            defaults.removeObject(forKey: "leaf.jellyfin.profile")
            connection = nil; client = nil; items = []; folders = []; downloaded = [:]; needsSignIn = false; message = nil
        } catch { message = JellyfinError.credentials.localizedDescription }
    }
}
