import Foundation

enum SmbImportProgress: Sendable {
    case downloading(bytes: Int64, total: Int64?)
    case preparing
    case publishing
}

/// One foreground transaction awaited by the shared-policy browser batch owner.
/// No remote filename is ever used as a staging path.
enum SmbComicImport {
    @MainActor
    static func run(transport: SmbTransport, connection: SmbWireConnection, remotePath: String,
                    originalName: String, expectedBytes: Int64?, library: LibraryStore,
                    collision: ComicImportCollision = .keepBoth,
                    stagingRoot: URL = ImportStaging.downloadRoot,
                    progress: @escaping @Sendable (SmbImportProgress) -> Void = { _ in }) async throws -> URL {
        try Task.checkCancellation()
        let staging = stagingRoot.appendingPathComponent(UUID().uuidString, isDirectory: true)
        try FileManager.default.createDirectory(at: staging, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: staging) }
        let download = staging.appendingPathComponent("download")
        progress(.downloading(bytes: 0, total: expectedBytes))
        try await transport.download(connection, path: remotePath, to: download) { bytes, total in
            progress(.downloading(bytes: bytes, total: total ?? expectedBytes))
        }
        try Task.checkCancellation()
        return try await library.importComicAwaitingCompletion(from: download, originalName: originalName,
            expectedBytes: expectedBytes, collision: collision) { phase in
                switch phase {
                case .preparing: progress(.preparing)
                case .publishing: progress(.publishing)
                }
            }
    }
}
