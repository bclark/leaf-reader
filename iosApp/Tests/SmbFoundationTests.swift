import Foundation
import LeafShared
import Testing
import UIKit
import PDFKit
import ZIPFoundation
@testable import Leaf

private struct MemoryCredentials: SmbCredentialStore {
    func password(profileID: String) throws -> String? { "  secret unchanged  " }
    func save(password: String, profileID: String) throws {}
    func delete(profileID: String) throws {}
}

private actor Gate {
    private var open = false
    private var waiters: [CheckedContinuation<Void, Never>] = []
    func wait() async {
        if open { return }
        await withCheckedContinuation { waiters.append($0) }
    }
    func release() {
        open = true
        let pending = waiters
        waiters.removeAll()
        pending.forEach { $0.resume() }
    }
}

private final class FakeSmbSession: SmbSession, @unchecked Sendable {
    enum Behavior { case success, partial, fail, blocked }
    var listing: [SmbWireEntry]?
    var failListing = false
    let data: Data
    let behavior: Behavior
    let started = Gate()
    let release = Gate()
    private let lock = NSLock()
    private var events: [String] = []
    var recordedEvents: [String] { lock.withLock { events } }
    private func record(_ event: String) { lock.withLock { events.append(event) } }

    init(data: Data = Data(), behavior: Behavior = .success) {
        self.data = data
        self.behavior = behavior
    }
    func connect() async throws { record("connect") }
    func list(path: String) async throws -> [SmbWireEntry] {
        record(path)
        if failListing { throw POSIXError(.ECONNREFUSED) }
        if let listing { return listing }
        return [SmbWireEntry(name: "本 2.cbz", path: path + "/本 2.cbz", kind: .file, size: nil)]
    }
    func download(path: String, to destination: URL,
                  continueTransfer: @escaping @Sendable (Int64, Int64?) -> Bool) async throws {
        record("download")
        try Data(data.prefix(4)).write(to: destination)
        await started.release()
        if behavior == .blocked { await release.wait() }
        if !continueTransfer(4, nil) {
            record("cancelled-io")
            return // Deliberately mimic AMSMB2 returning success when its loop is cancelled.
        }
        if behavior == .fail { throw POSIXError(.ECONNRESET) }
        if behavior != .partial { try data.write(to: destination) }
        record("io-finished")
    }
    func disconnect() async { record("disconnect") }
}

@Suite(.serialized)
@MainActor
struct SmbFoundationTests {
    private func connection() -> SmbWireConnection {
        SmbWireConnection(server: URL(string: "smb://localhost:1445")!, share: "comics",
                          username: "", domain: "", profileID: "test-profile")
    }
    private func directory() throws -> URL {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: url, withIntermediateDirectories: true)
        return url
    }
    private func comic(in directory: URL, name: String = "Source.cbz") throws -> URL {
        let folder = directory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: folder) }
        let image = UIGraphicsImageRenderer(size: CGSize(width: 20, height: 30)).image { context in
            UIColor.red.setFill()
            context.fill(CGRect(x: 0, y: 0, width: 20, height: 30))
        }
        try #require(image.pngData()).write(to: folder.appendingPathComponent("page1.png"))
        let output = directory.appendingPathComponent(name)
        try FileManager.default.zipItem(at: folder, to: output, shouldKeepParent: false)
        return output
    }
    private func transport(_ session: FakeSmbSession) -> SmbTransport {
        SmbTransport(credentials: MemoryCredentials()) { _, password in
            #expect(password == "  secret unchanged  ")
            return session
        }
    }
    private func children(_ url: URL) throws -> [URL] {
        try FileManager.default.contentsOfDirectory(at: url, includingPropertiesForKeys: nil)
    }

    @Test func sharedBrowserRetainsListingOnFailureAndResetsProfile() async throws {
        let session = FakeSmbSession()
        let model = SmbBrowserModel(transport: transport(session))
        model.configure(SmbProfile(host: "server", share: "Comics/base", username: ""))
        await model.waitForIdle()
        #expect(model.state.entries.count == 1)
        #expect(session.recordedEvents.contains("base"))
        model.selectAll()
        session.failListing = true
        model.navigate("missing")
        await model.waitForIdle()
        #expect(model.state.path == "")
        #expect(model.state.entries.count == 1)
        #expect(model.state.selectedPaths.count == 1)
        #expect(model.message != nil)
        let generation = model.state.generation
        model.configure(nil)
        #expect(model.state.entries.isEmpty)
        #expect(model.state.selectedPaths.isEmpty)
        #expect(model.state.generation > generation)
    }

    @Test func sharedAdapterRejectsLinksAndUsesSharedListingRules() throws {
        let wire = [
            SmbWireEntry(name: "page10.cbz", path: "ignored", kind: .file, size: -1),
            SmbWireEntry(name: "page2.cbz", path: "ignored", kind: .file, size: 1),
            SmbWireEntry(name: "link.cbz", path: "ignored", kind: .symbolicLink, size: 1),
            SmbWireEntry(name: "../escape.cbz", path: "ignored", kind: .file, size: 1),
            SmbWireEntry(name: "hidden.txt", path: "ignored", kind: .file, size: 1)
        ]
        let state = try SmbBrowserLogic.shared.begin(state: SmbBrowserState.companion.empty(), path: "folder")
        let completed = SmbBrowserLogic.shared.complete(state: state, generation: state.generation,
            entries: SmbSharedAdapter.entries(wire, parent: "folder"))
        #expect(completed.entries.map(\.name) == ["page2.cbz", "page10.cbz"])
        #expect(completed.entries.last?.sizeBytes == 0)
    }

    @Test func batchContinuesAfterInvalidComicAndReportsSuccess() async throws {
        let root = try directory()
        defer { try? FileManager.default.removeItem(at: root) }
        let source = try comic(in: root)
        let valid = FakeSmbSession(data: try Data(contentsOf: source))
        valid.listing = [SmbWireEntry(name: "1.cbz", path: "1.cbz", kind: .file, size: nil),
                         SmbWireEntry(name: "2.cbz", path: "2.cbz", kind: .file, size: nil)]
        let invalid = FakeSmbSession(data: Data("not a comic".utf8))
        let factory = SessionSequence([valid, invalid, valid])
        let model = SmbBrowserModel(transport: SmbTransport(credentials: MemoryCredentials(), factory: { _, _ in factory.next() }))
        model.configure(SmbProfile(host: "server", share: "Comics", username: ""))
        await model.waitForIdle()
        model.selectAll()
        let store = LibraryStore(directory: root.appendingPathComponent("Comics"))
        model.importSelected(library: store, collision: .keepBoth)
        await model.waitForIdle()
        #expect(model.summary?.imported == 1)
        #expect(model.summary?.failures.count == 1)
        #expect(model.summary?.unprocessed == 0)
        #expect(store.comics.count == 1)
    }

    @Test func batchCancellationWaitsForCleanupAndCountsUnprocessed() async throws {
        let root = try directory()
        defer { try? FileManager.default.removeItem(at: root) }
        let source = try comic(in: root)
        let session = FakeSmbSession(data: try Data(contentsOf: source), behavior: .blocked)
        let model = SmbBrowserModel(transport: transport(session))
        model.configure(SmbProfile(host: "server", share: "Comics", username: ""))
        await model.waitForIdle()
        model.selectAll()
        let store = LibraryStore(directory: root.appendingPathComponent("Comics"))
        model.importSelected(library: store, collision: .keepBoth)
        await session.started.wait()
        model.cancel()
        #expect(model.busy)
        #expect(model.cancelling)
        await session.release.release()
        await model.waitForIdle()
        #expect(!model.busy)
        #expect(model.summary?.unprocessed == 1)
        #expect(model.summary?.imported == 0)
        #expect(store.comics.isEmpty)
    }

    @Test func preferencesContainNoSecretAndInvalidSavePreservesProfile() throws {
        let suite = "smb-test-" + UUID().uuidString
        let defaults = try #require(UserDefaults(suiteName: suite))
        defer { defaults.removePersistentDomain(forName: suite) }
        let profile = SmbProfile(host: "[2001:db8::1]:445", share: "Comics/本", username: "DOMAIN\\reader")
        try profile.save(password: "private-password", defaults: defaults, credentials: MemoryCredentials())
        #expect(SmbProfile.load(defaults: defaults) == profile)
        let encoded = try #require(defaults.data(forKey: SmbProfile.preferencesKey))
        #expect(!String(decoding: encoded, as: UTF8.self).contains("private-password"))
        #expect(try profile.connection().domain == "DOMAIN")
        do {
            try SmbProfile(host: "", share: "../bad", username: "").save(password: "", defaults: defaults, credentials: MemoryCredentials())
            Issue.record("Invalid configuration saved")
        } catch { }
        #expect(SmbProfile.load(defaults: defaults) == profile)
        try SmbProfile.forget(defaults: defaults, credentials: MemoryCredentials())
        #expect(SmbProfile.load(defaults: defaults) == nil)
    }

    @Test func listingIsRawAndReconnects() async throws {
        let session = FakeSmbSession()
        let service = transport(session)
        let entries = try await service.list(connection(), path: "nested/漫画")
        #expect(entries.first?.name == "本 2.cbz")
        #expect(entries.first?.size == nil)
        try await service.test(connection())
        #expect(session.recordedEvents == ["connect", "nested/漫画", "disconnect", "connect", "disconnect"])
    }

    @Test func downloadImportCompletesBeforeSuccess() async throws {
        let root = try directory()
        defer { try? FileManager.default.removeItem(at: root) }
        let source = try comic(in: root)
        let data = try Data(contentsOf: source)
        let session = FakeSmbSession(data: data)
        let store = LibraryStore(directory: root.appendingPathComponent("Comics"))
        let staging = root.appendingPathComponent("Downloads")
        let result = try await SmbComicImport.run(transport: transport(session), connection: connection(),
            remotePath: "nested/issue.cbz", originalName: "Downloaded.cbz", expectedBytes: Int64(data.count),
            library: store, stagingRoot: staging)
        #expect(store.comics.contains(result))
        #expect(ComicMetadataStore.get(result) != nil)
        #expect(try CbzArchive(url: result).pageCount == 1)
        #expect(try children(staging).isEmpty)
        #expect(session.recordedEvents.suffix(2) == ["io-finished", "disconnect"])
        store.delete(result)
    }

    @Test func shortDownloadIsNotPublished() async throws {
        let root = try directory()
        defer { try? FileManager.default.removeItem(at: root) }
        let data = try Data(contentsOf: comic(in: root))
        let store = LibraryStore(directory: root.appendingPathComponent("Comics"))
        let staging = root.appendingPathComponent("Downloads")
        await #expect(throws: ComicImportError.self) {
            try await SmbComicImport.run(transport: transport(FakeSmbSession(data: data, behavior: .partial)),
                connection: connection(), remotePath: "file", originalName: "Incomplete.cbz",
                expectedBytes: Int64(data.count), library: store, stagingRoot: staging)
        }
        #expect(store.comics.isEmpty)
        #expect(try children(staging).isEmpty)
    }

    @Test func disconnectFailureCleansPartialDownload() async throws {
        let root = try directory()
        defer { try? FileManager.default.removeItem(at: root) }
        let store = LibraryStore(directory: root.appendingPathComponent("Comics"))
        let staging = root.appendingPathComponent("Downloads")
        let session = FakeSmbSession(data: Data(repeating: 1, count: 64), behavior: .fail)
        await #expect(throws: SmbTransportError.self) {
            try await SmbComicImport.run(transport: transport(session), connection: connection(),
                remotePath: "file", originalName: "Failed.cbz", expectedBytes: nil, library: store, stagingRoot: staging)
        }
        #expect(try children(staging).isEmpty)
        #expect(session.recordedEvents.last == "disconnect")
    }

    @Test func cancellationWaitsForIOAndPreventsOverlappingOperations() async throws {
        let root = try directory()
        defer { try? FileManager.default.removeItem(at: root) }
        let session = FakeSmbSession(data: Data(repeating: 1, count: 64), behavior: .blocked)
        let service = transport(session)
        let store = LibraryStore(directory: root.appendingPathComponent("Comics"))
        let staging = root.appendingPathComponent("Downloads")
        let task = Task {
            try await SmbComicImport.run(transport: service, connection: connection(), remotePath: "file",
                originalName: "Cancelled.cbz", expectedBytes: nil, library: store, stagingRoot: staging)
        }
        await session.started.wait()
        task.cancel()
        #expect(try children(staging).count == 1) // Cleanup cannot race the still-open transfer.
        await #expect(throws: SmbTransportError.self) { try await service.test(connection()) }
        await session.release.release()
        await #expect(throws: CancellationError.self) { try await task.value }
        #expect(try children(staging).isEmpty)
        #expect(store.comics.isEmpty)
        #expect(session.recordedEvents.suffix(2) == ["cancelled-io", "disconnect"])
    }

    @Test func failedReplacementPreservesComicAndProgress() async throws {
        let root = try directory()
        defer { try? FileManager.default.removeItem(at: root) }
        let source = try comic(in: root)
        let store = LibraryStore(directory: root.appendingPathComponent("Comics"))
        let destination = try await store.importComicAwaitingCompletion(from: source)
        defer { store.delete(destination) }
        let original = try Data(contentsOf: destination)
        ReadingProgress.set(destination, page: 3, step: -1, total: 8, rtl: false)
        let broken = root.appendingPathComponent("broken.cbz")
        try Data("PK\u{03}\u{04}truncated".utf8).write(to: broken)
        await #expect(throws: (any Error).self) {
            try await store.importComicAwaitingCompletion(from: broken, originalName: destination.lastPathComponent, collision: .replace)
        }
        #expect(try Data(contentsOf: destination) == original)
        #expect(ReadingProgress.get(destination)?.page == 3)
        #expect(!store.importing)
    }

    @Test func replacementAndKeepBothPreserveMetadataAndLists() async throws {
        let root = try directory()
        defer { try? FileManager.default.removeItem(at: root) }
        let source = try comic(in: root)
        let store = LibraryStore(directory: root.appendingPathComponent("Comics"))
        let first = try await store.importComicAwaitingCompletion(from: source)
        ReadingProgress.set(first, page: 2, step: -1, total: 5, rtl: true)
        ComicMetadataStore.set(first, metadata: ComicMetadataRecord(series: "Custom", userModified: true))
        let list = store.createReadingList(name: UUID().uuidString, urls: [first])
        defer { store.deleteReadingList(id: list.id); store.comics.forEach(store.delete) }
        let replaced = try await store.importComicAwaitingCompletion(from: source, collision: .replace)
        #expect(replaced == first)
        #expect(ReadingProgress.get(first)?.page == 2)
        #expect(ComicMetadataStore.get(first)?.series == "Custom")
        #expect(store.readingLists.first(where: { $0.id == list.id })?.itemFilenames == [first.lastPathComponent])
        let second = try await store.importComicAwaitingCompletion(from: source, collision: .keepBoth)
        #expect(second != first)
        #expect(store.comics.count == 2)
        await #expect(throws: ComicImportError.self) {
            try await store.importComicAwaitingCompletion(from: source, collision: .cancel)
        }
    }

    @Test func pdfAndMisnamedZipImport() async throws {
        let root = try directory()
        defer { try? FileManager.default.removeItem(at: root) }
        let store = LibraryStore(directory: root.appendingPathComponent("Comics"))
        defer { store.comics.forEach(store.delete) }
        let zip = try comic(in: root, name: "Misnamed.rar")
        let imported = try await store.importComicAwaitingCompletion(from: zip)
        #expect(imported.pathExtension == "cbz")
        let pdf = root.appendingPathComponent("Book.pdf")
        let renderer = UIGraphicsPDFRenderer(bounds: CGRect(x: 0, y: 0, width: 100, height: 100))
        try renderer.writePDF(to: pdf) { context in context.beginPage() }
        let importedPDF = try await store.importComicAwaitingCompletion(from: pdf)
        #expect(try CbzArchive(url: importedPDF).pageCount == 1)
    }

    @Test func preCancelledImportDoesNotPublish() async throws {
        let root = try directory()
        defer { try? FileManager.default.removeItem(at: root) }
        let source = try comic(in: root)
        let store = LibraryStore(directory: root.appendingPathComponent("Comics"))
        let task = Task {
            withUnsafeCurrentTask { $0?.cancel() }
            return try await store.importComicAwaitingCompletion(from: source)
        }
        await #expect(throws: CancellationError.self) { try await task.value }
        #expect(store.comics.isEmpty)
    }

    @Test func rarConversionIsAwaitedAndReadableOffline() async throws {
        let root = try directory()
        defer { try? FileManager.default.removeItem(at: root) }
        let image = UIGraphicsImageRenderer(size: CGSize(width: 20, height: 30)).image { context in
            UIColor.blue.setFill()
            context.fill(CGRect(x: 0, y: 0, width: 20, height: 30))
        }
        let png = try #require(image.pngData())
        // Synthetic uncompressed RAR5 fixture, built from the public RAR format specification.
        // No private comic or upstream fixture is bundled into the test target.
        func vint(_ value: Int) -> Data {
            var n = value
            var result = Data()
            repeat {
                let next = n >> 7
                result.append(UInt8(n & 127) | (next > 0 ? 128 : 0))
                n = next
            } while n > 0
            return result
        }
        func crc(_ bytes: Data) -> Data {
            var value: UInt32 = 0xffffffff
            for byte in bytes {
                value ^= UInt32(byte)
                for _ in 0..<8 { value = (value >> 1) ^ (value & 1 == 1 ? 0xedb88320 : 0) }
            }
            var result = (value ^ 0xffffffff).littleEndian
            return withUnsafeBytes(of: &result) { Data($0) }
        }
        func block(_ body: Data) -> Data {
            let data = vint(body.count) + body
            return crc(data) + data
        }
        let filename = Data("page1.png".utf8)
        var file = Data([2, 2]) + vint(png.count) // file block, data area present
        file += Data([4]) + vint(png.count) + vint(0o100644) // CRC present, unpacked size, attributes
        file += crc(png) + Data([0, 1]) + vint(filename.count) + filename // store, Unix host
        let archive = Data([0x52, 0x61, 0x72, 0x21, 0x1a, 0x07, 0x01, 0])
            + block(Data([1, 0, 0])) + block(file) + png + block(Data([5, 0, 0]))
        let source = root.appendingPathComponent("Synthetic.cbr")
        try archive.write(to: source)
        let store = LibraryStore(directory: root.appendingPathComponent("Comics"))
        let result = try await store.importComicAwaitingCompletion(from: source)
        defer { store.delete(result) }
        #expect(result.pathExtension == "cbz")
        #expect(try CbzArchive(url: result).pageCount == 1)
        #expect(!store.importing)
        #expect(try children(root.appendingPathComponent("Comics/.leaf-import-staging")).isEmpty)
    }

    @Test func publicationFailurePreservesDestinationAndCleansStaging() async throws {
        let root = try directory()
        defer { try? FileManager.default.removeItem(at: root) }
        let source = try comic(in: root)
        let comics = root.appendingPathComponent("Comics")
        let destination = comics.appendingPathComponent(source.lastPathComponent)
        try FileManager.default.createDirectory(at: destination, withIntermediateDirectories: true)
        let sentinel = destination.appendingPathComponent("keep")
        try Data([1, 2, 3]).write(to: sentinel)
        let pipeline = ComicImportPipeline(directory: comics)
        await #expect(throws: POSIXError.self) {
            try await pipeline.importFile(from: source, originalName: source.lastPathComponent,
                expectedBytes: nil, collision: .replace, phase: { _ in })
        }
        #expect(try Data(contentsOf: sentinel) == Data([1, 2, 3]))
        #expect(try children(comics.appendingPathComponent(".leaf-import-staging")).isEmpty)
    }

    @Test func staleCleanupOnlyRemovesOwnedOldDirectories() throws {
        let root = try directory()
        defer { try? FileManager.default.removeItem(at: root) }
        let old = root.appendingPathComponent(UUID().uuidString)
        let fresh = root.appendingPathComponent(UUID().uuidString)
        let unrelated = root.appendingPathComponent("keep-me")
        for url in [old, fresh, unrelated] {
            try FileManager.default.createDirectory(at: url, withIntermediateDirectories: true)
        }
        let past = Date().addingTimeInterval(-48 * 60 * 60)
        for url in [old, unrelated] {
            try FileManager.default.setAttributes([.modificationDate: past], ofItemAtPath: url.path)
        }
        ImportStaging.removeStaleDirectories(in: root)
        #expect(!FileManager.default.fileExists(atPath: old.path))
        #expect(FileManager.default.fileExists(atPath: fresh.path))
        #expect(FileManager.default.fileExists(atPath: unrelated.path))
    }

    @Test func keychainRoundTripAndForget() throws {
        let keychain = SmbKeychainCredentials(service: "leaf.tests.\(UUID().uuidString)")
        let profile = "stable-profile"
        defer { try? keychain.delete(profileID: profile) }
        #expect(try keychain.password(profileID: profile) == nil)
        try keychain.save(password: "  密碼  ", profileID: profile)
        #expect(try keychain.password(profileID: profile) == "  密碼  ")
        try keychain.save(password: "replacement", profileID: profile)
        #expect(try keychain.password(profileID: profile) == "replacement")
        try keychain.delete(profileID: profile)
        try keychain.delete(profileID: profile)
        #expect(try keychain.password(profileID: profile) == nil)
    }

    @Test func errorMappingDoesNotEchoServerDetails() {
        let error = NSError(domain: NSPOSIXErrorDomain, code: Int(ETIMEDOUT),
                            userInfo: [NSLocalizedDescriptionKey: "password=secret"])
        #expect(SmbTransportError.map(error).localizedDescription.contains("time"))
        #expect(!SmbTransportError.map(error).localizedDescription.contains("secret"))
    }
}

private final class SessionSequence: @unchecked Sendable {
    private let lock = NSLock()
    private var sessions: [FakeSmbSession]
    init(_ sessions: [FakeSmbSession]) { self.sessions = sessions }
    func next() -> FakeSmbSession { lock.withLock { sessions.removeFirst() } }
}
