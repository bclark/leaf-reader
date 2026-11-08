import Foundation

/// Native wire values. SmbSharedAdapter bridges these to the shared browser contract.
struct SmbWireEntry: Sendable {
    enum Kind: Sendable { case file, directory, symbolicLink, unknown }
    let name: String
    let path: String
    let kind: Kind
    let size: Int64?
}

struct SmbWireConnection: Sendable {
    let server: URL // Host/port only; never contains credentials or a share path.
    let share: String
    let username: String
    let domain: String
    let profileID: String
    var requireEncryption = false
}

enum SmbTransportError: Error, LocalizedError {
    case busy, invalidEndpoint, authentication, unavailable, timeout, io

    var errorDescription: String? {
        switch self {
        case .busy: return String(localized: "Another SMB operation is still finishing.")
        case .invalidEndpoint: return String(localized: "The SMB server address is invalid.")
        case .authentication: return String(localized: "The server rejected access. Check the account and share permissions.")
        case .unavailable: return String(localized: "Could not reach the SMB server. Check Wi-Fi, the server address, and Leaf’s Local Network access in Settings, then retry.")
        case .timeout: return String(localized: "The SMB server did not respond in time. Check the connection and Local Network access in Settings, then retry.")
        case .io: return String(localized: "The SMB operation could not be completed.")
        }
    }

    static func map(_ error: Error) -> SmbTransportError {
        if let error = error as? SmbTransportError { return error }
        let error = error as NSError
        guard error.domain == NSPOSIXErrorDomain else { return .io }
        switch Int32(error.code) {
        case EACCES, EPERM: return .authentication
        case ETIMEDOUT: return .timeout
        case ENETDOWN, ENETUNREACH, EHOSTUNREACH, ECONNREFUSED, ECONNRESET, ENOTCONN: return .unavailable
        default: return .io
        }
    }
}

/// One session belongs to exactly one operation. Returning from download means
/// all native writes have ended, even on cancellation or failure.
protocol SmbSession: Sendable {
    func connect() async throws
    func list(path: String) async throws -> [SmbWireEntry]
    func download(path: String, to: URL, continueTransfer: @escaping @Sendable (Int64, Int64?) -> Bool) async throws
    func disconnect() async
}

actor SmbTransport {
    typealias Factory = @Sendable (SmbWireConnection, String) throws -> any SmbSession
    private let credentials: any SmbCredentialStore
    private let factory: Factory
    private var active: OperationCancellation?

    init(credentials: any SmbCredentialStore = SmbKeychainCredentials(), factory: @escaping Factory) {
        self.credentials = credentials
        self.factory = factory
    }

    func cancel() { active?.cancel() }

    /// A batch keeps the credential value it started with, even if storage changes.
    func snapshot(for connection: SmbWireConnection) throws -> SmbTransport {
        let password = try credentials.password(profileID: connection.profileID) ?? ""
        return SmbTransport(credentials: SmbCredentialSnapshot(password: password), factory: factory)
    }

    func test(_ connection: SmbWireConnection) async throws {
        try await perform(connection) { _, cancellation in try cancellation.check() }
    }

    func list(_ connection: SmbWireConnection, path: String) async throws -> [SmbWireEntry] {
        try await perform(connection) { session, _ in try await session.list(path: path) }
    }

    func download(_ connection: SmbWireConnection, path: String, to destination: URL,
                  progress: @escaping @Sendable (Int64, Int64?) -> Void = { _, _ in }) async throws {
        try await perform(connection) { session, cancellation in
            let reporter = SmbProgressReporter(progress)
            try await session.download(path: path, to: destination) { bytes, total in
                guard !cancellation.isCancelled else { return false }
                reporter.report(bytes, total)
                return !cancellation.isCancelled
            }
        }
    }

    private func perform<T: Sendable>(_ connection: SmbWireConnection,
        operation: @escaping @Sendable (any SmbSession, OperationCancellation) async throws -> T
    ) async throws -> T {
        guard active == nil else { throw SmbTransportError.busy }
        let cancellation = OperationCancellation()
        active = cancellation
        defer { active = nil }
        return try await withTaskCancellationHandler {
            try Task.checkCancellation()
            let session: any SmbSession
            do {
                let password = try credentials.password(profileID: connection.profileID) ?? ""
                session = try factory(connection, password)
            } catch { throw SmbTransportError.map(error) }
            let result: Result<T, Error>
            do {
                try await session.connect()
                try cancellation.check()
                result = .success(try await operation(session, cancellation))
            } catch {
                result = .failure(error)
            }
            await session.disconnect()
            try cancellation.check()
            do { return try result.get() }
            catch is CancellationError { throw CancellationError() }
            catch { throw SmbTransportError.map(error) }

        } onCancel: {
            cancellation.cancel()
        }
    }
}

private final class SmbProgressReporter: @unchecked Sendable {
    private let lock = NSLock()
    private var lastUpdate: TimeInterval = -.infinity
    private let callback: @Sendable (Int64, Int64?) -> Void

    init(_ callback: @escaping @Sendable (Int64, Int64?) -> Void) { self.callback = callback }

    func report(_ bytes: Int64, _ total: Int64?) {
        lock.lock()
        let now = ProcessInfo.processInfo.systemUptime
        let publish = now - lastUpdate >= 0.15 || total == bytes
        if publish { lastUpdate = now }
        lock.unlock()
        if publish { callback(bytes, total) }
    }
}

private struct SmbCredentialSnapshot: SmbCredentialStore {
    let password: String
    func password(profileID: String) throws -> String? { password }
    func save(password: String, profileID: String) throws { throw SmbTransportError.io }
    func delete(profileID: String) throws { throw SmbTransportError.io }
}
