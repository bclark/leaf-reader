import Foundation
import AMSMB2

/// Operation-owned native session. Never encode, log or expose the native manager.
final class NativeSmbSession: SmbSession, @unchecked Sendable {
    private let manager: SMB2Manager
    private let connection: SmbWireConnection

    init(connection: SmbWireConnection, password: String) throws {
        let url = connection.server
        guard url.scheme == "smb", url.host != nil, url.user == nil, url.password == nil,
              url.query == nil, url.fragment == nil, url.path.isEmpty || url.path == "/",
              let manager = SMB2Manager(url: url, domain: connection.domain,
                credential: URLCredential(user: connection.username, password: password, persistence: .none)) else {
            throw SmbTransportError.invalidEndpoint
        }
        // Blank username is sent explicitly, rather than AMSMB2's nil-credential guest default.
        manager.timeout = 15
        self.manager = manager
        self.connection = connection
    }

    func connect() async throws {
        try await manager.connectShare(name: connection.share, encrypted: connection.requireEncryption)
    }

    func list(path: String) async throws -> [SmbWireEntry] {
        let entries = try await manager.contentsOfDirectory(atPath: path, recursive: false)
        return entries.compactMap { item -> SmbWireEntry? in
            guard let name = item[.nameKey] as? String, let path = item[.pathKey] as? String else { return nil }
            let kind: SmbWireEntry.Kind
            switch item[.fileResourceTypeKey] as? URLFileResourceType {
            case .regular: kind = .file
            case .directory: kind = .directory
            case .symbolicLink: kind = .symbolicLink
            default: kind = .unknown
            }
            return SmbWireEntry(name: name, path: path, kind: kind, size: (item[.fileSizeKey] as? NSNumber)?.int64Value)
        }
    }

    func download(path: String, to destination: URL,
                  continueTransfer: @escaping @Sendable (Int64, Int64?) -> Bool) async throws {
        let attributes = try await manager.attributesOfItem(atPath: path)
        guard attributes[.fileResourceTypeKey] as? URLFileResourceType == .regular else {
            throw SmbTransportError.io
        }
        let expected = (attributes[.fileSizeKey] as? NSNumber)?.int64Value
        let stopped = OperationCancellation()
        // AMSMB2 dispatches blocking IO to its own queue. Its callback's false result
        // exits the read loop; await completion before disconnecting or cleaning up.
        try await manager.downloadItem(atPath: path, to: destination) { bytes, total in
            let keepReading = continueTransfer(bytes, total >= 0 ? total : nil)
            if !keepReading { stopped.cancel() }
            return keepReading
        }
        try stopped.check()
        // The upstream loop can return success on early EOF or callback cancellation.
        // The owner maps cancellation separately; an uncancelled short read is an IO failure.
        if let expected {
            let actual = try destination.resourceValues(forKeys: [.fileSizeKey]).fileSize ?? 0
            guard Int64(actual) == expected else { throw SmbTransportError.io }
        }
    }

    func disconnect() async {
        try? await manager.disconnectShare(gracefully: true)
    }
}
