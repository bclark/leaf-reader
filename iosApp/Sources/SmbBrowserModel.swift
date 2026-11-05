import SwiftUI
import LeafShared

/// ObservableObject supports Leaf's iOS 16 minimum. All operations use async tasks.
@MainActor
final class SmbBrowserModel: ObservableObject {
    @Published private(set) var state = SmbBrowserState.companion.empty()
    @Published private(set) var busy = false
    @Published private(set) var cancelling = false
    @Published private(set) var message: String?
    @Published private(set) var fraction: Double?
    @Published private(set) var activity = ""
    @Published private(set) var progressDetail = ""
    @Published private(set) var summary: SmbBatchSummary?
    private var task: Task<Void, Never>?
    private let transport: SmbTransport
    private var profile: SmbProfile?
    private var retryPath = ""

    init(transport: SmbTransport = SmbTransport(factory: { try NativeSmbSession(connection: $0, password: $1) })) {
        self.transport = transport
    }

    func configure(_ profile: SmbProfile?) {
        guard !busy else { return }
        if self.profile != profile {
            state = SmbBrowserLogic.shared.reset(state: state)
            self.profile = profile
            summary = nil
            message = nil
        }
        if profile != nil { navigate("") }
    }

    func navigate(_ path: String) {
        guard !busy, let profile else { return }
        do { state = try SmbBrowserLogic.shared.begin(state: state, path: path) }
        catch { message = "This folder path is invalid."; return }
        retryPath = path
        let generation = state.generation
        let pending = state.pendingPath ?? ""
        busy = true
        message = nil
        task = Task {
            defer { finish() }
            do {
                let config = try profile.configuration()
                let remote = try SmbPaths.shared.join(base: config.basePath, relative: pending)
                let wire = try await transport.list(profile.connection(), path: remote)
                try Task.checkCancellation()
                state = SmbBrowserLogic.shared.complete(state: state, generation: generation,
                    entries: SmbSharedAdapter.entries(wire, parent: pending))
            } catch is CancellationError {
                // cancel() has already invalidated the generation.
            } catch {
                let failure = SmbTransportError.map(error)
                state = SmbBrowserLogic.shared.fail(state: state, generation: generation, failure: failure.sharedFailure)
                if state.generation == generation { message = failure.localizedDescription }
            }
        }
    }

    func retry() { navigate(retryPath) }

    func up() {
        guard let parent = try? SmbPaths.shared.parent(path: state.path) else { return }
        navigate(parent)
    }

    func toggle(_ entry: SmbEntry) {
        guard !busy else { return }
        state = SmbBrowserLogic.shared.toggle(state: state, path: entry.path)
    }

    func selectAll() {
        guard !busy else { return }
        state = SmbBrowserLogic.shared.selectAll(state: state)
    }

    func clearSelection() {
        guard !busy else { return }
        state = SmbBrowserLogic.shared.clearSelection(state: state)
    }

    func cancel() {
        state = SmbBrowserLogic.shared.cancel(state: state)
        guard busy else { return }
        cancelling = true
        task?.cancel()
        // The task remains owned until actual IO and local publication have finished.
    }

    func waitForIdle() async { await task?.value }

    func importSelected(library: LibraryStore, collision: ComicImportCollision) {
        guard !busy, let profile else { return }
        let entries = state.entries.filter { state.selectedPaths.contains($0.path) }
        guard !entries.isEmpty else { return }
        busy = true
        summary = nil
        message = nil
        let operationID = UUID()
        batchID = operationID
        task = Task {
            defer { batchID = nil; finish() }
            var result = SmbBatchSummary(total: entries.count)
            do {
                let config = try profile.configuration()
                let connection = try profile.connection()
                let batchTransport = try await transport.snapshot(for: connection)
                for (index, entry) in entries.enumerated() {
                    try Task.checkCancellation()
                    activity = "\(index + 1) of \(entries.count): \(entry.name)"
                    fraction = nil
                    let fileID = UUID()
                    transferID = fileID
                    transferStarted = Date()
                    do {
                        let remote = try SmbPaths.shared.join(base: config.basePath, relative: entry.path)
                        let completed = result.imported
                        _ = try await SmbComicImport.run(transport: batchTransport, connection: connection,
                            remotePath: remote, originalName: entry.name,
                            expectedBytes: entry.sizeBytes > 0 ? entry.sizeBytes : nil,
                            library: library, collision: collision) { progress in
                                Task { @MainActor in
                                    guard self.batchID == operationID, self.transferID == fileID, !self.cancelling else { return }
                                    self.updateProgress(progress, completed: completed, count: entries.count)
                                }
                            }
                        result.imported += 1
                    } catch is CancellationError { throw CancellationError() }
                    catch {
                        // Native exception text may include addresses or account information.
                        let detail = (error as? ComicImportError)?.localizedDescription
                            ?? "Could not download or prepare this comic."
                        result.failures.append(SmbBatchSummary.Failure(name: entry.name, message: detail))
                    }
                }
            } catch is CancellationError { }
            catch { message = "Check the saved SMB connection and try again." }
            summary = result
            state = SmbBrowserLogic.shared.clearSelection(state: state)
        }
    }

    private var batchID: UUID?
    private var transferID: UUID?
    private var transferStarted = Date()

    private func updateProgress(_ progress: SmbImportProgress, completed: Int, count: Int) {
        let phase: SmbImportPhase
        let transfer: SmbTransferProgress
        switch progress {
        case .downloading(let bytes, let total):
            let elapsed = max(0.001, Date().timeIntervalSince(transferStarted))
            let rate = Int64(Double(bytes) / elapsed)
            let downloaded = ByteCountFormatter.string(fromByteCount: bytes, countStyle: .file)
            let speed = ByteCountFormatter.string(fromByteCount: rate, countStyle: .file)
            progressDetail = "Downloading \(downloaded) · \(speed)/s"
            phase = .downloading
            transfer = SmbTransferProgress(bytesTransferred: bytes, totalBytes: total ?? 0, bytesPerSec: rate)
        case .preparing:
            progressDetail = "Preparing comic…"
            phase = .preparing
            transfer = SmbTransferProgress(bytesTransferred: 1, totalBytes: 1, bytesPerSec: 0)
        case .publishing:
            progressDetail = "Adding to library…"
            phase = .importing
            transfer = SmbTransferProgress(bytesTransferred: 1, totalBytes: 1, bytesPerSec: 0)
        }
        fraction = LeafShared.SmbImportProgress.shared.fraction(completed: Int32(completed), count: Int32(count),
                                                              transfer: transfer, phase: phase)?.doubleValue
    }

    private func finish() {
        busy = false
        cancelling = false
        task = nil
        activity = ""
        progressDetail = ""
        transferID = nil
        fraction = nil
    }
}

struct SmbBatchSummary {
    struct Failure: Identifiable {
        let id = UUID()
        let name: String
        let message: String
    }
    let total: Int
    var imported = 0
    var failures: [Failure] = []
    var unprocessed: Int { total - imported - failures.count }
}
