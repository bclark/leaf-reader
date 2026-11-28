import Foundation
import Testing
import SwiftUI
@testable import Leaf

@Suite(.serialized)
@MainActor
struct JellyfinSyncTests {
    private let identity = JellyfinProgressStore.Identity(serverID: "server", userID: "user", itemID: "book")
    private let baseline = JellyfinProgressStore.Remote(positionTicks: 0, played: false)

    @Test func uploadAcknowledgesOnlyConfirmedStateAndDoesNotRepeat() async throws {
        let context = try Context()
        defer { context.clean() }
        let initial = try await seed(context, pending: true)
        let transport = SyncTransport(remote: baseline)
        #expect(isSynced(try await sync(context, transport)))
        #expect(transport.writes == 1)
        #expect(transport.remote == .init(positionTicks: 20_000, played: false))
        #expect(try await context.store.pending(for: context.connection).isEmpty)
        #expect(try await context.store.record(for: identity)?.sourceRevision == initial.sourceRevision)
        #expect(isSynced(try await sync(context, transport)))
        #expect(transport.writes == 1)
    }

    @Test func remoteResetUpdatesExistingDownloadWithoutEchoingAnEdit() async throws {
        let context = try Context()
        defer { context.clean() }
        _ = try await seed(context, pending: false)
        let transport = SyncTransport(remote: baseline)
        #expect(isSynced(try await sync(context, transport)))
        #expect(ReadingProgress.get(context.url)?.page == 0)
        #expect(ReadingProgress.get(context.url)?.isCompleted == false)
        ReadingProgress.flushSync()
        try await context.store.recoverLocalEdits(imports: [context.imported])
        #expect(try await context.store.pending(for: context.connection).isEmpty)
        #expect(transport.writes == 0)
    }

    @Test(arguments: [true, false])
    func conflictPreservesBothUntilExplicitChoice(useDevice: Bool) async throws {
        let context = try Context()
        defer { context.clean() }
        let pending = try await seed(context, pending: true)
        let remote = JellyfinProgressStore.Remote(positionTicks: 50_000, played: false)
        let transport = SyncTransport(remote: remote)
        let outcome = try await sync(context, transport)
        guard case .conflict(let conflict) = outcome else { Issue.record("Expected conflict"); return }
        #expect(ReadingProgress.get(context.url)?.page == 2)
        #expect(try await context.store.record(for: identity)?.pendingRevision == pending.pendingRevision)
        #expect(try await context.store.record(for: identity)?.observedRemote == remote)
        #expect(transport.writes == 0)
        #expect(isSynced(try await sync(context, transport, choice: .init(conflict: conflict, useDevice: useDevice))))
        #expect(ReadingProgress.get(context.url)?.page == (useDevice ? 2 : 5))
        #expect(transport.writes == (useDevice ? 1 : 0))
        #expect(try await context.store.pending(for: context.connection).isEmpty)
    }

    @Test func changedServerInvalidatesAnEarlierChoice() async throws {
        let context = try Context()
        defer { context.clean() }
        _ = try await seed(context, pending: true)
        let transport = SyncTransport(remote: .init(positionTicks: 50_000, played: false))
        guard case .conflict(let conflict) = try await sync(context, transport) else {
            Issue.record("Expected conflict"); return
        }
        transport.remote = .init(positionTicks: 60_000, played: false)
        guard case .conflict(let updated) = try await sync(context, transport,
            choice: .init(conflict: conflict, useDevice: true)) else {
            Issue.record("Expected refreshed choice"); return
        }
        #expect(updated.remote == transport.remote)
        #expect(transport.writes == 0)
    }

    @Test func accountChangeDuringFetchStopsBeforeAnyWrite() async throws {
        let context = try Context()
        defer { context.clean() }
        _ = try await seed(context, pending: true)
        let transport = SyncTransport(remote: baseline)
        var active = true
        transport.onFetch = { active = false }
        await #expect(throws: (any Error).self) {
            try await JellyfinSync.synchronize(connection: context.connection, itemID: "book",
                url: context.url, pageCount: 10, store: context.store, transport: transport) {
                if !active { throw CancellationError() }
            }
        }
        #expect(transport.writes == 0)
        #expect(try await context.store.pending(for: context.connection).count == 1)
    }

    @Test func newerReaderEditDuringUploadCannotBeAcknowledgedAway() async throws {
        let context = try Context()
        defer { context.clean() }
        let initial = try await seed(context, pending: true)
        let transport = SyncTransport(remote: baseline)
        transport.onUpdate = {
            ReadingProgress.set(context.url, page: 3, step: -1, total: 10, rtl: false)
        }
        await #expect(throws: (any Error).self) { try await sync(context, transport) }
        #expect(try await context.store.record(for: identity)?.pendingRevision == initial.pendingRevision)
        #expect(ReadingProgress.get(context.url)?.page == 3)
        ReadingProgress.flushSync()
        try await context.store.recoverLocalEdits(imports: [context.imported])
        #expect(try await context.store.record(for: identity)?.position.pageIndex == 3)
        #expect(try await context.store.record(for: identity)?.pendingRevision != initial.pendingRevision)
    }

    @Test func lostResponseRetriesByReadingBackWithoutAnotherWrite() async throws {
        let context = try Context()
        defer { context.clean() }
        _ = try await seed(context, pending: true)
        let transport = SyncTransport(remote: baseline)
        transport.loseResponse = true
        await #expect(throws: (any Error).self) { try await sync(context, transport) }
        #expect(try await context.store.pending(for: context.connection).count == 1)
        #expect(isSynced(try await sync(context, transport)))
        #expect(transport.writes == 1)
        #expect(try await context.store.pending(for: context.connection).isEmpty)
    }

    @Test func cancellationAfterServerCommitRetainsPendingAndRetriesWithoutAnotherWrite() async throws {
        let context = try Context()
        defer { context.clean() }
        let initial = try await seed(context, pending: true)
        let native = ReadingProgress.get(context.url)
        let transport = SyncTransport(remote: baseline)
        // Cancel the sync task after the transport commits, before its successful response
        // is acknowledged. The parent test task must remain usable for the retry.
        transport.onUpdate = { withUnsafeCurrentTask { $0?.cancel() } }
        let upload = Task { try await sync(context, transport) }
        switch await upload.result {
        case .failure(let error): #expect(error is CancellationError)
        case .success: Issue.record("A canceled sync must not acknowledge the pending edit")
        }
        #expect(!Task.isCancelled)
        #expect(transport.writes == 1)
        #expect(transport.remote == .init(positionTicks: 20_000, played: false))
        #expect(ReadingProgress.get(context.url) == native)
        let reopened = JellyfinProgressStore(file: context.root.appendingPathComponent("progress.json"))
        let pending = try #require(try await reopened.record(for: identity))
        #expect(pending.pendingRevision == initial.pendingRevision)
        #expect(pending.sourceRevision == initial.sourceRevision)
        #expect(pending.baseline == baseline)
        transport.onUpdate = nil
        #expect(isSynced(try await JellyfinSync.synchronize(connection: context.connection,
            itemID: "book", url: context.url, pageCount: 10, store: reopened,
            transport: transport, validateSession: {})))
        #expect(transport.writes == 1)
        #expect(try await reopened.pending(for: context.connection).isEmpty)
        #expect(ReadingProgress.get(context.url) == native)
    }

    @Test func missingAndOutOfRangeRemoteStateNeverResetsLocalProgress() async throws {
        let context = try Context()
        defer { context.clean() }
        _ = try await seed(context, pending: true)
        let transport = SyncTransport(remote: .init(positionTicks: nil, played: nil))
        guard case .unavailable = try await sync(context, transport) else { Issue.record("Expected unavailable"); return }
        transport.remote = .init(positionTicks: Int64.max, played: false)
        await #expect(throws: (any Error).self) { try await sync(context, transport) }
        #expect(transport.writes == 0)
        #expect(ReadingProgress.get(context.url)?.page == 2)
    }

    // DIG-20: an untouched first read is a local resume point, not a sync edit.
    @Test func firstOpenWithoutTurningAdoptsServerPosition() async throws {
        let context = try Context()
        defer { context.clean() }
        ReadingProgress.set(context.url, page: 0, step: -1, total: 10, rtl: false, isEdit: false)
        #expect(ReadingProgress.get(context.url)?.isUntouchedFirstRead == true)
        ReadingProgress.flushSync()
        try await context.store.recoverLocalEdits(imports: [context.imported])
        #expect(try await context.store.pending(for: context.connection).isEmpty)
        let transport = SyncTransport(remote: .init(positionTicks: 50_000, played: false))
        #expect(isSynced(try await sync(context, transport)))
        #expect(ReadingProgress.get(context.url)?.page == 5)
        #expect(transport.writes == 0)
        #expect(try await context.store.pending(for: context.connection).isEmpty)
    }

    @Test func neverOpenedDownloadsSilently() async throws {
        let context = try Context()
        defer { context.clean() }
        let transport = SyncTransport(remote: .init(positionTicks: 50_000, played: false))
        #expect(isSynced(try await sync(context, transport)))
        #expect(ReadingProgress.get(context.url)?.page == 5)
        #expect(transport.writes == 0)
    }

    @Test func returningToFirstPageAfterAdvancingIsAnEditThatNeverOverwritesSilently() async throws {
        let context = try Context()
        defer { context.clean() }
        ReadingProgress.set(context.url, page: 0, step: -1, total: 10, rtl: false, isEdit: false)
        ReadingProgress.set(context.url, page: 3, step: -1, total: 10, rtl: false)
        ReadingProgress.set(context.url, page: 0, step: -1, total: 10, rtl: false)
        #expect(ReadingProgress.get(context.url)?.isUntouchedFirstRead == false)
        ReadingProgress.flushSync()
        try await context.store.recoverLocalEdits(imports: [context.imported])
        #expect(try await context.store.pending(for: context.connection).count == 1)
        let transport = SyncTransport(remote: .init(positionTicks: 50_000, played: false))
        guard case .conflict = try await sync(context, transport) else { Issue.record("Expected conflict"); return }
        #expect(ReadingProgress.get(context.url)?.page == 0)
        #expect(transport.writes == 0)
    }

    @Test(arguments: [false, true])
    func realFirstPageSaveWithKnownBaselineUploadsOrConflicts(serverMoved: Bool) async throws {
        let context = try Context()
        defer { context.clean() }
        _ = try await seed(context, pending: false)
        let transport = SyncTransport(remote: .init(positionTicks: 20_000, played: false))
        #expect(isSynced(try await sync(context, transport)))
        ReadingProgress.set(context.url, page: 0, step: -1, total: 10, rtl: false)
        ReadingProgress.flushSync()
        try await context.store.recoverLocalEdits(imports: [context.imported])
        if serverMoved { transport.remote = .init(positionTicks: 60_000, played: false) }
        let outcome = try await sync(context, transport)
        if serverMoved {
            guard case .conflict = outcome else { Issue.record("Expected conflict"); return }
            #expect(transport.writes == 0)
            #expect(ReadingProgress.get(context.url)?.page == 0)
        } else {
            #expect(isSynced(outcome))
            #expect(transport.writes == 1)
            #expect(transport.remote == .init(positionTicks: 0, played: false))
        }
    }

    @Test func unknownServerStateLeavesFirstReadUntouched() async throws {
        let context = try Context()
        defer { context.clean() }
        ReadingProgress.set(context.url, page: 0, step: -1, total: 10, rtl: false, isEdit: false)
        let untouched = ReadingProgress.get(context.url)
        let transport = SyncTransport(remote: .init(positionTicks: nil, played: nil))
        for remote in [JellyfinProgressStore.Remote(positionTicks: nil, played: nil),
                       .init(positionTicks: 50_000, played: nil), .init(positionTicks: nil, played: false)] {
            transport.remote = remote
            guard case .unavailable = try await sync(context, transport) else { Issue.record("Expected unavailable"); return }
            #expect(ReadingProgress.get(context.url) == untouched)
        }
        #expect(transport.writes == 0)
        transport.remote = .init(positionTicks: 50_000, played: false)
        #expect(isSynced(try await sync(context, transport)))
        #expect(ReadingProgress.get(context.url)?.page == 5)
    }

    @Test func serverMismatchKeepsPendingUntilItCanBeConfirmed() async throws {
        let context = try Context()
        defer { context.clean() }
        _ = try await seed(context, pending: true)
        let transport = SyncTransport(remote: baseline)
        transport.ignoreWrite = true
        await #expect(throws: (any Error).self) { try await sync(context, transport) }
        #expect(try await context.store.pending(for: context.connection).count == 1)
    }

    @Test func remoteUnreadOnLastPageSurvivesReaderSaveAndExplicitMarkRead() async throws {
        let context = try Context()
        defer { context.clean() }
        _ = try await seed(context, pending: false)
        let transport = SyncTransport(remote: .init(positionTicks: 90_000, played: false))
        #expect(isSynced(try await sync(context, transport)))
        let applied = try #require(ReadingProgress.get(context.url))
        #expect(applied.page == 9)
        #expect(!applied.isCompleted)
        ReadingProgress.set(context.url, page: 9, step: -1, total: 10, rtl: true)
        #expect(ReadingProgress.get(context.url)?.localRevision == applied.localRevision)
        #expect(ReadingProgress.get(context.url)?.isCompleted == false)
        ReadingProgress.set(context.url, page: 9, step: -1, total: 10, rtl: true, completed: true)
        #expect(ReadingProgress.get(context.url)?.isCompleted == true)
        #expect(ReadingProgress.get(context.url)?.localRevision != applied.localRevision)
    }

    @Test func failedRemoteCheckpointDoesNotAlterNativeResume() async throws {
        let context = try Context()
        defer { context.clean() }
        ReadingProgress.set(context.url, page: 2, step: -1, total: 10, rtl: true)
        let original = ReadingProgress.get(context.url)
        let failing = JellyfinProgressStore(file: context.root.appendingPathComponent("failing.json"),
            persist: { _, _ in throw CocoaError(.fileWriteOutOfSpace) })
        let transport = SyncTransport(remote: .init(positionTicks: 20_000, played: false))
        await #expect(throws: (any Error).self) {
            try await JellyfinSync.synchronize(connection: context.connection, itemID: "book",
                url: context.url, pageCount: 10, store: failing, transport: transport, validateSession: {})
        }
        #expect(ReadingProgress.get(context.url) == original)
    }

    @Test func conflictChoicesRenderAtNarrowAndAccessibilitySizes() throws {
        let context = try Context()
        defer { context.clean() }
        let conflict = JellyfinSync.Conflict(connection: context.connection, itemID: "book",
            local: Progress(page: 1, step: -1, total: 1, rtl: false),
            remote: .init(positionTicks: 0, played: false), pendingRevision: UUID())
        for largeText in [false, true] {
            let content = JellyfinSyncConflictView(conflict: conflict, choose: { _ in }, deferChoice: {})
                .padding(16)
                .frame(width: 320, alignment: .leading)
                .background(Color.white)
                .foregroundStyle(Color.black)
                .environment(\.dynamicTypeSize, largeText ? .accessibility3 : .large)
                .environment(\.colorScheme, .light)
            let renderer = ImageRenderer(content: content)
            let image = try #require(renderer.uiImage)
            #expect(image.size.width == 320)
            #expect(image.size.height > 250)
            if #available(iOS 26.0, *) {
                Attachment.record(image, named: largeText ? "sync-conflict-large.png" : "sync-conflict.png", as: .png)
            }
        }
    }

    @Test func backupRetainsExplicitUnreadOnLastPage() throws {
        let context = try Context()
        defer { context.clean() }
        ReadingProgress.set(context.url, page: 9, step: -1, total: 10, rtl: true, completed: false)
        let json = BackupManager.exportToJson(comics: [context.url], metadata: [:], readingLists: [],
            settings: LeafSettings(), appVersion: "fixture")
        let backup = try BackupManager.parseAndValidate(jsonString: json)
        #expect(backup.comics.first?.lastPage == 9)
        #expect(backup.comics.first?.completed == false)
    }

    private func seed(_ context: Context, pending: Bool) async throws -> JellyfinProgressStore.Record {
        ReadingProgress.set(context.url, page: 2, step: -1, total: 10, rtl: false)
        let native = try #require(ReadingProgress.get(context.url))
        let record = try await context.store.recordLocal(identity: identity, filename: context.url.lastPathComponent,
            position: .init(pageIndex: 2, pageCount: 10, completed: false), baseline: baseline,
            sourceRevision: native.localRevision)
        if !pending {
            _ = try await context.store.acknowledge(identity: identity, revision: try #require(record.pendingRevision),
                remote: .init(positionTicks: 20_000, played: false))
        }
        return record
    }

    private func sync(_ context: Context, _ transport: SyncTransport,
                      choice: JellyfinSync.Choice? = nil) async throws -> JellyfinSync.Outcome {
        try await JellyfinSync.synchronize(connection: context.connection, itemID: "book",
            url: context.url, pageCount: 10, store: context.store, transport: transport,
            choice: choice, validateSession: {})
    }

    private func isSynced(_ outcome: JellyfinSync.Outcome) -> Bool {
        if case .synced = outcome { return true }
        return false
    }

    private struct Context {
        let root: URL
        let url: URL
        let store: JellyfinProgressStore
        let connection: JellyfinConnection
        var imported: JellyfinImports.Record {
            .init(profileID: "profile", serverID: "server", userID: "user", itemID: "book",
                  filename: url.lastPathComponent, checksum: "fixture", title: "Fixture", versionHint: nil, pending: false)
        }
        init() throws {
            root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
            try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
            url = root.appendingPathComponent(UUID().uuidString + ".cbz")
            store = JellyfinProgressStore(file: root.appendingPathComponent("progress.json"))
            connection = JellyfinConnection(id: "profile", baseURL: try #require(URL(string: "https://example.test/proxy")),
                userID: "user", serverID: "server", username: "fixture", allowsHTTP: false)
        }
        func clean() {
            ReadingProgress.clear(url)
            try? FileManager.default.removeItem(at: root)
        }
    }
}

@MainActor
private final class SyncTransport: JellyfinProgressTransport {
    var remote: JellyfinProgressStore.Remote
    var writes = 0
    var loseResponse = false
    var ignoreWrite = false
    var onFetch: (() -> Void)?
    var onUpdate: (() -> Void)?
    init(remote: JellyfinProgressStore.Remote) { self.remote = remote }

    func fetchProgress(connection: JellyfinConnection, itemID: String) async throws -> JellyfinProgressStore.Remote {
        onFetch?()
        return remote
    }
    func updateProgress(connection: JellyfinConnection, itemID: String,
                        remote: JellyfinProgressStore.Remote) async throws -> JellyfinProgressStore.Remote {
        writes += 1
        if !ignoreWrite { self.remote = remote }
        onUpdate?()
        if loseResponse { loseResponse = false; throw JellyfinError.network }
        return self.remote
    }
}
