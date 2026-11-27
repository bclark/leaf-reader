import Foundation
import LeafShared

protocol JellyfinProgressTransport: Sendable {
    func fetchProgress(connection: JellyfinConnection, itemID: String) async throws -> JellyfinProgressStore.Remote
    func updateProgress(connection: JellyfinConnection, itemID: String,
                        remote: JellyfinProgressStore.Remote) async throws -> JellyfinProgressStore.Remote
}

/// Sync runs only from an explicit foreground action. Each suspension rechecks the session
/// and native resume point; a new reader edit always remains pending.
@MainActor
enum JellyfinSync {
    struct Conflict: Equatable {
        let connection: JellyfinConnection
        let itemID: String
        let local: Progress?
        let remote: JellyfinProgressStore.Remote
        let pendingRevision: UUID?
    }

    struct Choice {
        let conflict: Conflict
        let useDevice: Bool
    }

    enum Outcome {
        case synced
        case conflict(Conflict)
        case unavailable
    }

    enum SyncError: Error, LocalizedError {
        case changed, unsupportedPosition, unacknowledged, alreadyRunning
        var errorDescription: String? {
            switch self {
            case .changed: return String(localized: "The comic or reading position changed during sync. Try again.")
            case .unsupportedPosition: return String(localized: "Jellyfin’s position does not match this downloaded comic. Your local position is unchanged.")
            case .unacknowledged: return String(localized: "Jellyfin has not confirmed this reading position. It is still pending.")
            case .alreadyRunning: return String(localized: "This comic is already syncing.")
            }
        }
    }

    private static var inFlight: Set<JellyfinProgressStore.Identity> = []

    static func synchronize(connection: JellyfinConnection, itemID: String, url: URL, pageCount: Int,
                            store: JellyfinProgressStore, transport: any JellyfinProgressTransport,
                            choice: Choice? = nil, validateSession: () throws -> Void) async throws -> Outcome {
        let identity = JellyfinProgressStore.Identity(serverID: connection.serverID, userID: connection.userID, itemID: itemID)
        guard inFlight.insert(identity).inserted else { throw SyncError.alreadyRunning }
        defer { inFlight.remove(identity) }
        guard pageCount > 0, pageCount <= Int(Int32.max) else { throw SyncError.unsupportedPosition }
        let local = ReadingProgress.get(url)
        func validate() throws {
            try Task.checkCancellation()
            try validateSession()
            guard ReadingProgress.get(url) == local else { throw SyncError.changed }
        }
        try validate()
        var record = try await store.record(for: identity)
        try validate()
        if let record {
            guard (local == nil && record.pendingRevision == nil) || record.sourceRevision == local?.localRevision else {
                throw SyncError.changed
            }
            guard local != nil || record.pendingRevision == nil else { throw SyncError.changed }
        }
        let remote = try await transport.fetchProgress(connection: connection, itemID: itemID)
        try validate()
        guard let ticks = remote.positionTicks, let played = remote.played else { return .unavailable }
        let ticksPerPage = JellyfinProgress.shared.pageToTicks(pageIndex: 1)
        guard ticks >= 0, ticks % ticksPerPage == 0, ticks / ticksPerPage < Int64(pageCount) else {
            throw SyncError.unsupportedPosition
        }
        let remotePosition = JellyfinProgressStore.Position(
            pageIndex: Int(ticks / ticksPerPage), pageCount: pageCount, completed: played)
        let localPage = min(max(local?.page ?? 0, 0), pageCount - 1)
        let desired = JellyfinProgressStore.Remote(
            positionTicks: JellyfinProgress.shared.pageToTicks(pageIndex: Int32(localPage)),
            played: local?.isCompleted ?? false)
        try await store.observeRemote(remote, for: identity)
        try validate()
        record = try await store.record(for: identity)
        try validate()
        let conflict = Conflict(connection: connection, itemID: itemID, local: local,
                                remote: remote, pendingRevision: record?.pendingRevision)
        var action = JellyfinSyncPolicy.shared.decide(
            localTicks: desired.positionTicks ?? 0, localPlayed: desired.played ?? false,
            remoteTicks: KotlinLong(longLong: ticks), remotePlayed: KotlinBoolean(bool: played),
            baselineTicks: record?.baseline?.positionTicks.map { KotlinLong(longLong: $0) },
            baselinePlayed: record?.baseline?.played.map { KotlinBoolean(bool: $0) },
            pending: record?.pendingRevision != nil,
            untrackedLocal: record == nil && local != nil && local?.isUntouchedFirstRead != true)
        if let choice {
            // A choice never applies to a new account, new local edit or newly changed server state.
            guard choice.conflict == conflict else { return .conflict(conflict) }
            action = choice.useDevice ? .upload : .download
        }
        if action == .conflict { return .conflict(conflict) }
        if action == .unavailable { return .unavailable }
        if action == .upload {
            if record?.pendingRevision == nil {
                record = try await store.recordLocal(identity: identity, filename: url.lastPathComponent,
                    position: .init(pageIndex: localPage, pageCount: pageCount, completed: local?.isCompleted ?? false),
                    baseline: remote, sourceRevision: local?.localRevision)
                try validate()
            }
            guard let revision = record?.pendingRevision else { throw SyncError.changed }
            let acknowledgment = try await transport.updateProgress(connection: connection, itemID: itemID, remote: desired)
            try validate()
            guard acknowledgment == desired else {
                try await store.observeRemote(acknowledgment, for: identity)
                throw SyncError.unacknowledged
            }
            // Verify readback as well: a successful HTTP status alone is not a sync acknowledgment.
            let confirmed = try await transport.fetchProgress(connection: connection, itemID: itemID)
            try validate()
            guard confirmed == desired else {
                try await store.observeRemote(confirmed, for: identity)
                throw SyncError.unacknowledged
            }
            guard try await store.acknowledge(identity: identity, revision: revision, remote: confirmed) else {
                throw SyncError.changed
            }
            try validate()
            return .synced
        }
        // Equal states can also clear a retry whose previous response was lost.
        if action == .equal, let revision = record?.pendingRevision {
            guard try await store.acknowledge(identity: identity, revision: revision, remote: remote) else {
                throw SyncError.changed
            }
            try validate()
            return .synced
        }
        let revision = UUID()
        try await store.acceptRemote(identity: identity, filename: url.lastPathComponent,
            position: remotePosition, remote: remote, expected: record, sourceRevision: revision)
        try validate()
        guard ReadingProgress.applyRemote(url, expected: local, position: remotePosition, revision: revision) else {
            throw SyncError.changed
        }
        return .synced
    }
}
