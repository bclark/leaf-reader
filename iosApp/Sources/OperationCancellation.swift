import Foundation

/// Crosses native callback/IO queues. Cancellation never completes an operation early:
/// its owner must await IO termination before deleting staging files.
final class OperationCancellation: @unchecked Sendable {
    private let lock = NSLock()
    private var cancelled = false

    func cancel() {
        lock.lock()
        cancelled = true
        lock.unlock()
    }

    var isCancelled: Bool {
        lock.lock()
        defer { lock.unlock() }
        return cancelled
    }

    func check() throws {
        if isCancelled { throw CancellationError() }
    }
}
