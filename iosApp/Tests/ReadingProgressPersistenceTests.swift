import Foundation
import Testing
@testable import Leaf

@Suite(.serialized)
struct ReadingProgressPersistenceTests {
    @Test func preferenceCallbacksCanReadProgressDuringFlush() throws {
        let url = URL(fileURLWithPath: "/Comics/" + UUID().uuidString + ".cbz")
        defer { ReadingProgress.clear(url) }
        ReadingProgress.set(url, page: 1, step: -1, total: 4, rtl: false)
        ReadingProgress.flushSync()
        ReadingProgress.set(url, page: 2, step: -1, total: 4, rtl: false)
        let readbacks = PreferenceReadbacks(url: url)
        let token = NotificationCenter.default.addObserver(forName: UserDefaults.didChangeNotification,
            object: UserDefaults.standard, queue: nil) { _ in readbacks.readWhileNotifying() }
        defer { NotificationCenter.default.removeObserver(token) }
        ReadingProgress.flushSync()
        let results = readbacks.results
        #expect(!results.isEmpty, "The real defaults write must notify the observer")
        #expect(results.allSatisfy { $0 }, "Preference observers must be able to read progress before the write returns")
        #expect(try ReadingProgress.savedSnapshot()[url.lastPathComponent] == ReadingProgress.get(url))
    }

    @Test func flushedProgressAndRecencyCannotBeOverwrittenByPendingSaves() async throws {
        let url = URL(fileURLWithPath: "/Comics/" + UUID().uuidString + ".cbz")
        defer { ReadingProgress.clear(url) }
        for page in 0..<20 {
            ReadingProgress.set(url, page: page % 6, step: -1, total: 6, rtl: false)
            ReadingProgress.setOpenedAt(url, timestamp: Double(page))
        }
        ReadingProgress.set(url, page: 3, step: -1, total: 6, rtl: true, completed: false)
        ReadingProgress.setOpenedAt(url, timestamp: 999)
        let expected = try #require(ReadingProgress.get(url))
        ReadingProgress.flushSync()
        // Allow a superseded 80ms debounced write to run if cancellation/order is incorrect.
        try await Task.sleep(nanoseconds: 150_000_000)
        #expect(try ReadingProgress.savedSnapshot()[url.lastPathComponent] == expected)
        let data = try #require(UserDefaults.standard.data(forKey: "leaf.lastOpened.v1"))
        let opened = try JSONDecoder().decode([String: Double].self, from: data)
        #expect(opened[url.lastPathComponent] == 999)
        #expect(ReadingProgress.get(url)?.localRevision == expected.localRevision)
    }
}

private final class PreferenceReadbacks: @unchecked Sendable {
    private let url: URL
    private let queue = DispatchQueue(label: "leaf.tests.progress.readback")
    private let lock = NSLock()
    private var recorded: [Bool] = []
    init(url: URL) { self.url = url }
    var results: [Bool] { lock.lock(); defer { lock.unlock() }; return recorded }

    func readWhileNotifying() {
        let finished = DispatchSemaphore(value: 0)
        queue.async { [url] in
            _ = ReadingProgress.get(url)
            finished.signal()
        }
        // Bound the before-fix failure instead of deadlocking the test process itself.
        let canRead = finished.wait(timeout: .now() + 1) == .success
        lock.lock(); recorded.append(canRead); lock.unlock()
    }
}
