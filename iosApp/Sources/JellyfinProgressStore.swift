import Foundation
import LeafShared

/// Native Codable storage for the shared server/user/item identity contract.
/// This store has no transport and never chooses between local and remote progress.
/// Remote reconciliation follows the shared conflict-policy decision.
actor JellyfinProgressStore {
    struct Identity: Codable, Hashable, Sendable {
        let serverID: String
        let userID: String
        let itemID: String

        func belongs(to connection: JellyfinConnection) -> Bool {
            serverID == connection.serverID && userID == connection.userID
        }
    }

    struct Position: Codable, Equatable, Sendable {
        /// Zero-based physical page, independent of reading direction or panel position.
        let pageIndex: Int
        let pageCount: Int
        /// Explicit state preserves rereading and mark-unread independently of page number.
        let completed: Bool
    }

    struct Remote: Codable, Equatable, Sendable {
        /// Nil means missing server data, not a reset or an unread declaration.
        let positionTicks: Int64?
        let played: Bool?
    }

    struct Record: Codable, Equatable, Sendable {
        let identity: Identity
        let filename: String
        var position: Position
        /// The remote state before this sequence of unsent edits.
        var baseline: Remote?
        /// Most recently fetched remote candidate, retained without applying precedence.
        var observedRemote: Remote?
        var pendingRevision: UUID?
        // Retained after acknowledgment so reopening does not enqueue the same local edit again.
        var sourceRevision: UUID? = nil
        var contentChecksum: String? = nil
    }

    /// Adapt the shared identity at the boundary; the persisted form contains no credentials.
    static func identity(_ shared: JellyfinItemIdentity) -> Identity {
        Identity(serverID: shared.serverId, userID: shared.userId, itemID: shared.itemId)
    }

    private struct State: Codable {
        let version: Int
        var records: [Record]
    }

    enum StoreError: Error {
        case unsupportedVersion
        case invalidRecord
        case duplicateIdentity
        case changedDuringSync
    }

    static let shared = JellyfinProgressStore(file: FileManager.default
        .urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        .appendingPathComponent("Jellyfin/progress-v1.json"))

    private let file: URL
    private let persist: @Sendable (Data, URL) throws -> Void

    init(file: URL, persist: @escaping @Sendable (Data, URL) throws -> Void = {
        try $0.write(to: $1, options: .atomic)
    }) {
        self.file = file
        self.persist = persist
    }

    /// Returning successfully means the local position and pending revision were saved together.
    @discardableResult
    func recordLocal(identity: Identity, filename: String, position: Position,
                     baseline: Remote?, sourceRevision: UUID? = nil) throws -> Record {
        var state = try read()
        let revision = UUID()
        let record: Record
        if let index = state.records.firstIndex(where: { $0.identity == identity }) {
            let previous = state.records[index]
            record = Record(identity: identity, filename: filename, position: position,
                baseline: previous.pendingRevision == nil ? baseline : previous.baseline,
                observedRemote: previous.observedRemote, pendingRevision: revision, sourceRevision: sourceRevision)
            state.records[index] = record
        } else {
            record = Record(identity: identity, filename: filename, position: position,
                baseline: baseline, observedRemote: nil, pendingRevision: revision, sourceRevision: sourceRevision)
            state.records.append(record)
        }
        try write(state)
        return record
    }

    /// Call only with checksum-validated import records. Read the saved snapshot inside this
    /// actor turn so overlapping recoveries cannot apply snapshots captured out of order.
    func recoverLocalEdits(imports: [JellyfinImports.Record],
                           snapshot: @Sendable () throws -> [String: Progress] = { try ReadingProgress.savedSnapshot() }) throws {
        let saved = try snapshot()
        var state = try read()
        let original = state.records
        var seen: Set<Identity> = []
        for imported in imports where !imported.pending {
            let identity = Identity(serverID: imported.serverID, userID: imported.userID, itemID: imported.itemID)
            guard seen.insert(identity).inserted else { throw StoreError.duplicateIdentity }
            guard let progress = saved[imported.filename], let revision = progress.localRevision else { continue }
            guard progress.total > 0, progress.page >= 0,
                  progress.page < progress.total || (progress.total == 1 && progress.page == 1) else {
                throw StoreError.invalidRecord
            }
            let position = Position(pageIndex: min(progress.page, progress.total - 1),
                                    pageCount: progress.total, completed: progress.isCompleted)
            if let index = state.records.firstIndex(where: { $0.identity == identity }) {
                if let checksum = state.records[index].contentChecksum, checksum != imported.checksum,
                   state.records[index].pendingRevision != nil {
                    throw StoreError.changedDuringSync
                }
                if state.records[index].sourceRevision == revision {
                    state.records[index].contentChecksum = imported.checksum
                    continue
                }
                let previous = state.records[index]
                // An unknown baseline stays unknown; a local edit never invents remote state.
                state.records[index] = Record(identity: identity, filename: imported.filename,
                    position: position, baseline: previous.baseline, observedRemote: previous.observedRemote,
                    pendingRevision: UUID(), sourceRevision: revision, contentChecksum: imported.checksum)
            } else {
                state.records.append(Record(identity: identity, filename: imported.filename, position: position,
                    baseline: nil, observedRemote: nil, pendingRevision: UUID(), sourceRevision: revision, contentChecksum: imported.checksum))
            }
        }
        if state.records != original { try write(state) }
    }

    /// Reuse retained progress only for the exact same archive bytes and page count.
    func rebindRetainedCopy(identity: Identity, filename: String, checksum: String, pageCount: Int) throws -> Record? {
        var state = try read()
        guard let index = state.records.firstIndex(where: { $0.identity == identity }),
              state.records[index].contentChecksum == checksum,
              state.records[index].position.pageCount == pageCount else { return nil }
        let previous = state.records[index]
        let rebound = Record(identity: identity, filename: filename, position: previous.position,
            baseline: previous.baseline, observedRemote: previous.observedRemote,
            pendingRevision: previous.pendingRevision, sourceRevision: previous.sourceRevision ?? UUID(),
            contentChecksum: checksum)
        state.records[index] = rebound
        try write(state)
        return rebound
    }

    func record(for identity: Identity) throws -> Record? {
        try read().records.first { $0.identity == identity }
    }

    /// Save the remote checkpoint before publishing it to native resume storage.
    /// A crash between stores conservatively leaves the older local edit recoverable.
    func acceptRemote(identity: Identity, filename: String, position: Position, remote: Remote,
                      expected: Record?, sourceRevision: UUID) throws {
        var state = try read()
        guard state.records.first(where: { $0.identity == identity }) == expected else {
            throw StoreError.changedDuringSync
        }
        state.records.removeAll { $0.identity == identity }
        state.records.append(Record(identity: identity, filename: filename, position: position,
            baseline: remote, observedRemote: remote, pendingRevision: nil, sourceRevision: sourceRevision))
        try write(state)
    }

    func records(for connection: JellyfinConnection) throws -> [Record] {
        try read().records.filter { $0.identity.belongs(to: connection) }
    }

    func pending(for connection: JellyfinConnection) throws -> [Record] {
        try records(for: connection).filter { $0.pendingRevision != nil }
    }

    /// Preserve a competing remote candidate; reconciliation belongs to shared policy.
    func observeRemote(_ remote: Remote?, for identity: Identity) throws {
        guard let remote, remote.positionTicks != nil || remote.played != nil else { return }
        var state = try read()
        guard let index = state.records.firstIndex(where: { $0.identity == identity }) else { return }
        state.records[index].observedRemote = remote
        try write(state)
    }

    /// A late acknowledgment must never clear a newer edit, even when its page is identical.
    /// Call only after the exact revision's server operations have been acknowledged.
    @discardableResult
    func acknowledge(identity: Identity, revision: UUID, remote: Remote) throws -> Bool {
        var state = try read()
        guard let index = state.records.firstIndex(where: {
            $0.identity == identity && $0.pendingRevision == revision
        }) else { return false }
        state.records[index].pendingRevision = nil
        state.records[index].baseline = remote
        // Retain observedRemote: it may have arrived while the acknowledged request was in flight.
        try write(state)
        return true
    }

    private func read() throws -> State {
        let data: Data
        do {
            data = try Data(contentsOf: file)
        } catch CocoaError.fileReadNoSuchFile {
            return State(version: 1, records: [])
        }
        let state = try JSONDecoder().decode(State.self, from: data)
        try validate(state)
        return state
    }

    private func write(_ state: State) throws {
        try validate(state)
        try FileManager.default.createDirectory(at: file.deletingLastPathComponent(), withIntermediateDirectories: true)
        // No mutable cache: failed writes cannot leak an unpersisted success through subsequent reads.
        try persist(JSONEncoder().encode(state), file)
    }

    private func validate(_ state: State) throws {
        guard state.version == 1 else { throw StoreError.unsupportedVersion }
        var identities: Set<Identity> = []
        for record in state.records {
            guard identities.insert(record.identity).inserted else { throw StoreError.duplicateIdentity }
            guard [record.identity.serverID, record.identity.userID, record.identity.itemID]
                    .allSatisfy(JellyfinConnection.validID),
                  !record.filename.isEmpty, record.filename != ".", record.filename != "..",
                  !record.filename.contains("/"), !record.filename.contains("\\"),
                  !record.filename.unicodeScalars.contains(where: CharacterSet.controlCharacters.contains),
                  record.position.pageCount > 0, record.position.pageIndex >= 0,
                  record.position.pageIndex < record.position.pageCount,
                  [record.baseline, record.observedRemote].allSatisfy({
                      $0?.positionTicks.map { $0 >= 0 } ?? true
                  }) else { throw StoreError.invalidRecord }
        }
    }
}
