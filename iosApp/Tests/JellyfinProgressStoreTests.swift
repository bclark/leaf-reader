import Foundation
import Testing
@testable import Leaf

struct JellyfinProgressStoreTests {
    private let identity = JellyfinProgressStore.Identity(serverID: "server", userID: "user", itemID: "book")
    private let baseline = JellyfinProgressStore.Remote(positionTicks: 20_000, played: false)

    @Test func legacyProgressDoesNotInventAnOfflineEdit() async throws {
        let root = try directory()
        defer { try? FileManager.default.removeItem(at: root) }
        let legacy = try JSONDecoder().decode(Progress.self,
            from: Data(#"{"page":2,"step":-1,"total":10,"rtl":false}"#.utf8))
        #expect(legacy.localRevision == nil)
        let store = JellyfinProgressStore(file: root.appendingPathComponent("progress.json"))
        try await store.recoverLocalEdits(imports: [imported()], snapshot: { ["Book.cbz": legacy] })
        #expect(try await store.pending(for: connection()).isEmpty)
    }

    @Test func recoveredEditsSurviveAcknowledgmentWithoutReenqueueing() async throws {
        let root = try directory()
        defer { try? FileManager.default.removeItem(at: root) }
        let file = root.appendingPathComponent("progress.json")
        let store = JellyfinProgressStore(file: file)
        let first = Progress(page: 1, step: -1, total: 1, rtl: true, localRevision: UUID())
        try await store.recoverLocalEdits(imports: [imported()], snapshot: { ["Book.cbz": first] })
        let pending = try #require(try await store.pending(for: connection()).first)
        #expect(pending.position == .init(pageIndex: 0, pageCount: 1, completed: true))
        #expect(pending.sourceRevision == first.localRevision)
        #expect(try await store.rebindRetainedCopy(identity: identity, filename: "New.cbz",
            checksum: "different bytes", pageCount: 1) == nil)
        #expect(try await store.record(for: identity) == pending)
        let revision = try #require(pending.pendingRevision)
        #expect(try await store.acknowledge(identity: identity, revision: revision, remote: baseline))
        let reopened = JellyfinProgressStore(file: file)
        try await reopened.recoverLocalEdits(imports: [imported()], snapshot: { ["Book.cbz": first] })
        #expect(try await reopened.pending(for: connection()).isEmpty)
        let reread = Progress(page: 0, step: -1, total: 1, rtl: true, localRevision: UUID())
        try await reopened.recoverLocalEdits(imports: [imported()], snapshot: { ["Book.cbz": reread] })
        let next = try #require(try await reopened.pending(for: connection()).first)
        #expect(next.position == .init(pageIndex: 0, pageCount: 1, completed: false))
        #expect(next.baseline == baseline)
        #expect(next.pendingRevision != revision)
    }

    @Test func failedRecoveryCanReplayTheSamePersistedEdit() async throws {
        let root = try directory()
        defer { try? FileManager.default.removeItem(at: root) }
        let file = root.appendingPathComponent("progress.json")
        let progress = Progress(page: 2, step: -1, total: 10, rtl: false, localRevision: UUID())
        let failing = JellyfinProgressStore(file: file, persist: { _, _ in
            throw CocoaError(.fileWriteOutOfSpace)
        })
        await #expect(throws: (any Error).self) {
            try await failing.recoverLocalEdits(imports: [imported()], snapshot: { ["Book.cbz": progress] })
        }
        let retry = JellyfinProgressStore(file: file)
        try await retry.recoverLocalEdits(imports: [imported()], snapshot: { ["Book.cbz": progress] })
        #expect(try await retry.pending(for: connection()).first?.sourceRevision == progress.localRevision)
    }

    @Test func recoveryRejectsAmbiguousImportsWithoutPartialPublication() async throws {
        let root = try directory()
        defer { try? FileManager.default.removeItem(at: root) }
        let file = root.appendingPathComponent("progress.json")
        let store = JellyfinProgressStore(file: file)
        let progress = Progress(page: 2, step: -1, total: 10, rtl: false, localRevision: UUID())
        await #expect(throws: (any Error).self) {
            try await store.recoverLocalEdits(imports: [imported(), imported()],
                snapshot: { ["Book.cbz": progress] })
        }
        #expect(try await store.pending(for: connection()).isEmpty)
        var unpublished = imported()
        unpublished.pending = true
        try await store.recoverLocalEdits(imports: [unpublished], snapshot: { ["Book.cbz": progress] })
        #expect(try await store.pending(for: connection()).isEmpty)
    }

    @Test func readerMutationsPersistRevisionWithResumeAndPreserveItForDirectionChanges() throws {
        let url = URL(fileURLWithPath: "/Comics/" + UUID().uuidString + ".cbz")
        defer { ReadingProgress.clear(url) }
        ReadingProgress.set(url, page: 1, step: -1, total: 1, rtl: false)
        ReadingProgress.flushSync()
        let first = try #require(try ReadingProgress.savedSnapshot()[url.lastPathComponent])
        #expect(first.isCompleted)
        #expect(first.localRevision != nil)
        ReadingProgress.set(url, page: 1, step: 3, total: 1, rtl: true)
        ReadingProgress.flushSync()
        let direction = try #require(try ReadingProgress.savedSnapshot()[url.lastPathComponent])
        #expect(direction.localRevision == first.localRevision)
        #expect(direction.rtl == true)
        ReadingProgress.set(url, page: 0, step: -1, total: 1, rtl: true)
        ReadingProgress.flushSync()
        let unread = try #require(try ReadingProgress.savedSnapshot()[url.lastPathComponent])
        #expect(!unread.isCompleted)
        #expect(unread.localRevision != first.localRevision)
        ReadingProgress.resyncTotalIfNeeded(url, actualTotal: 3)
        ReadingProgress.flushSync()
        let corrected = try #require(try ReadingProgress.savedSnapshot()[url.lastPathComponent])
        #expect(corrected.total == 3)
        #expect(corrected.localRevision != unread.localRevision)
    }

    private func imported() -> JellyfinImports.Record {
        .init(profileID: "profile", serverID: identity.serverID, userID: identity.userID,
              itemID: identity.itemID, filename: "Book.cbz", checksum: "validated",
              title: "Book", versionHint: nil, pending: false)
    }

    @Test func positionAndPendingRevisionSurviveReopeningTogether() async throws {
        let root = try directory()
        defer { try? FileManager.default.removeItem(at: root) }
        let file = root.appendingPathComponent("progress.json")
        let store = JellyfinProgressStore(file: file)
        let saved = try await store.recordLocal(identity: identity, filename: "Book.cbz",
            position: .init(pageIndex: 5, pageCount: 20, completed: false), baseline: baseline)
        #expect(saved.pendingRevision != nil)
        let reopened = JellyfinProgressStore(file: file)
        #expect(try await reopened.pending(for: connection()) == [saved])
    }

    @Test func lateAcknowledgmentPreservesNewerRevisionAndOriginalBaseline() async throws {
        let root = try directory()
        defer { try? FileManager.default.removeItem(at: root) }
        let file = root.appendingPathComponent("progress.json")
        let store = JellyfinProgressStore(file: file)
        let first = try await store.recordLocal(identity: identity, filename: "Book.cbz",
            position: .init(pageIndex: 5, pageCount: 20, completed: false), baseline: baseline)
        let remote = JellyfinProgressStore.Remote(positionTicks: 110_000, played: false)
        try await store.observeRemote(remote, for: identity)
        // Identical page is still a newer intent/revision; equality of page values cannot clear it.
        let next = try await store.recordLocal(identity: identity, filename: "Book.cbz",
            position: first.position, baseline: remote)
        #expect(next.baseline == baseline)
        #expect(next.observedRemote == remote)
        #expect(next.pendingRevision != first.pendingRevision)
        let oldRevision = try #require(first.pendingRevision)
        #expect(try await store.acknowledge(identity: identity, revision: oldRevision, remote: remote) == false)
        #expect(try await JellyfinProgressStore(file: file).pending(for: connection()) == [next])
    }

    @Test func matchingAcknowledgmentKeepsPositionAndConcurrentRemoteCandidate() async throws {
        let root = try directory()
        defer { try? FileManager.default.removeItem(at: root) }
        let file = root.appendingPathComponent("progress.json")
        let store = JellyfinProgressStore(file: file)
        let saved = try await store.recordLocal(identity: identity, filename: "Book.cbz",
            position: .init(pageIndex: 0, pageCount: 20, completed: false), baseline: baseline)
        let candidate = JellyfinProgressStore.Remote(positionTicks: 150_000, played: false)
        try await store.observeRemote(candidate, for: identity)
        let revision = try #require(saved.pendingRevision)
        let acknowledged = JellyfinProgressStore.Remote(positionTicks: 0, played: false)
        #expect(try await store.acknowledge(identity: identity, revision: revision, remote: acknowledged))
        let reopened = JellyfinProgressStore(file: file)
        #expect(try await reopened.pending(for: connection()).isEmpty)
        let records = try await reopened.records(for: connection())
        #expect(records.first?.position == saved.position)
        #expect(records.first?.baseline == acknowledged)
        #expect(records.first?.observedRemote == candidate)
    }

    @Test func accountsAndServersCannotSeeOrAcknowledgeEachOthersPendingEdits() async throws {
        let root = try directory()
        defer { try? FileManager.default.removeItem(at: root) }
        let store = JellyfinProgressStore(file: root.appendingPathComponent("progress.json"))
        for (server, user) in [("server", "user"), ("other-server", "user"), ("server", "other-user")] {
            try await store.recordLocal(identity: .init(serverID: server, userID: user, itemID: "book"),
                filename: "Same title.cbz", position: .init(pageIndex: 2, pageCount: 10, completed: false), baseline: nil)
        }
        let mine = try await store.pending(for: connection())
        #expect(mine.count == 1)
        #expect(mine.first?.identity == identity)
        let revision = try #require(mine.first?.pendingRevision)
        let foreign = JellyfinProgressStore.Identity(serverID: "server", userID: "other-user", itemID: "book")
        #expect(try await store.acknowledge(identity: foreign, revision: revision, remote: baseline) == false)
        #expect(try await store.pending(for: connection(user: "other-user")).count == 1)
        #expect(try await store.pending(for: connection(server: "other-server")).count == 1)
    }

    @Test func remoteResetAndMissingFieldsNeverOverwriteUnsentRereading() async throws {
        let root = try directory()
        defer { try? FileManager.default.removeItem(at: root) }
        let store = JellyfinProgressStore(file: root.appendingPathComponent("progress.json"))
        let reread = JellyfinProgressStore.Position(pageIndex: 0, pageCount: 1, completed: false)
        let saved = try await store.recordLocal(identity: identity, filename: "One page.cbz", position: reread,
            baseline: .init(positionTicks: 0, played: true))
        let reset = JellyfinProgressStore.Remote(positionTicks: 0, played: false)
        try await store.observeRemote(reset, for: identity)
        #expect(try await store.pending(for: connection()).first?.position == reread)
        #expect(try await store.pending(for: connection()).first?.observedRemote == reset)
        try await store.observeRemote(.init(positionTicks: nil, played: nil), for: identity)
        let current = try await store.pending(for: connection()).first
        #expect(current?.pendingRevision == saved.pendingRevision)
        #expect(current?.position.completed == false)
        #expect(current?.observedRemote == reset)
        try await store.observeRemote(nil, for: identity)
        #expect(try await store.pending(for: connection()).first?.observedRemote == reset)
    }

    @Test func corruptOrFutureStoreIsPreservedAndRejectsMutation() async throws {
        let root = try directory()
        defer { try? FileManager.default.removeItem(at: root) }
        let file = root.appendingPathComponent("progress.json")
        for bytes in [Data("broken json".utf8), Data(#"{"version":99,"records":[]}"#.utf8)] {
            try bytes.write(to: file)
            let store = JellyfinProgressStore(file: file)
            await #expect(throws: (any Error).self) {
                try await store.recordLocal(identity: identity, filename: "Book.cbz",
                    position: .init(pageIndex: 0, pageCount: 1, completed: false), baseline: nil)
            }
            #expect(try Data(contentsOf: file) == bytes)
        }
    }

    @Test func invalidRecordDoesNotReplacePreviouslySavedProgress() async throws {
        let root = try directory()
        defer { try? FileManager.default.removeItem(at: root) }
        let file = root.appendingPathComponent("progress.json")
        let store = JellyfinProgressStore(file: file)
        let saved = try await store.recordLocal(identity: identity, filename: "Book.cbz",
            position: .init(pageIndex: 3, pageCount: 10, completed: false), baseline: baseline)
        for bad in [
            JellyfinProgressStore.Position(pageIndex: -1, pageCount: 10, completed: false),
            .init(pageIndex: 10, pageCount: 10, completed: true),
            .init(pageIndex: 0, pageCount: 0, completed: false)
        ] {
            await #expect(throws: JellyfinProgressStore.StoreError.self) {
                try await store.recordLocal(identity: identity, filename: "Book.cbz", position: bad, baseline: baseline)
            }
        }
        await #expect(throws: JellyfinProgressStore.StoreError.self) {
            try await store.recordLocal(identity: identity, filename: "../Book.cbz", position: saved.position, baseline: nil)
        }
        #expect(try await JellyfinProgressStore(file: file).pending(for: connection()) == [saved])
    }

    @Test func unreadableStoreDoesNotReportSuccessOrLosePreviouslySavedBytes() async throws {
        let root = try directory()
        defer { try? FileManager.default.removeItem(at: root) }
        let file = root.appendingPathComponent("progress.json")
        let store = JellyfinProgressStore(file: file)
        let saved = try await store.recordLocal(identity: identity, filename: "Book.cbz",
            position: .init(pageIndex: 2, pageCount: 10, completed: false), baseline: nil)
        let bytes = try Data(contentsOf: file)
        // A directory at the destination deterministically fails, including when running as root.
        try FileManager.default.removeItem(at: file)
        try FileManager.default.createDirectory(at: file, withIntermediateDirectories: false)
        await #expect(throws: (any Error).self) {
            try await store.recordLocal(identity: identity, filename: "Book.cbz",
                position: .init(pageIndex: 5, pageCount: 10, completed: false), baseline: nil)
        }
        try FileManager.default.removeItem(at: file)
        try bytes.write(to: file)
        #expect(try await store.pending(for: connection()) == [saved])
    }

    @Test func failedPersistencePreservesPendingRevisionAndPropagatesError() async throws {
        let root = try directory()
        defer { try? FileManager.default.removeItem(at: root) }
        let file = root.appendingPathComponent("progress.json")
        let store = JellyfinProgressStore(file: file)
        let saved = try await store.recordLocal(identity: identity, filename: "Book.cbz",
            position: .init(pageIndex: 2, pageCount: 10, completed: false), baseline: baseline)
        let bytes = try Data(contentsOf: file)
        // Inject a disk-full write failure without exhausting the user's device storage.
        let failing = JellyfinProgressStore(file: file, persist: { _, _ in
            throw CocoaError(.fileWriteOutOfSpace)
        })
        await #expect(throws: CocoaError.self) {
            try await failing.recordLocal(identity: identity, filename: "Book.cbz",
                position: .init(pageIndex: 7, pageCount: 10, completed: false), baseline: nil)
        }
        let revision = try #require(saved.pendingRevision)
        await #expect(throws: CocoaError.self) {
            try await failing.acknowledge(identity: identity, revision: revision, remote: baseline)
        }
        #expect(try Data(contentsOf: file) == bytes)
        #expect(try await failing.pending(for: connection()) == [saved])
    }

    private func directory() throws -> URL {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: url, withIntermediateDirectories: true)
        return url
    }

    private func connection(server: String = "server", user: String = "user") throws -> JellyfinConnection {
        JellyfinConnection(id: "fixture-profile", baseURL: try #require(URL(string: "https://example.test")),
            userID: user, serverID: server, username: "Fixture", allowsHTTP: false)
    }
}
