import Foundation

private struct LiveCredentials: SmbCredentialStore {
    let secret: String
    func password(profileID: String) throws -> String? { secret }
    func save(password: String, profileID: String) throws {}
    func delete(profileID: String) throws {}
}

@main struct LiveSpike {
    static func main() async {
        do { try await run() }
        catch { print("LIVE CHECK FAILED: \(SmbTransportError.map(error).localizedDescription)"); exit(1) }
    }
    static func run() async throws {
        let raw = try String(contentsOfFile: CommandLine.arguments[1], encoding: .utf8)
        var values: [String: String] = [:]
        for line in raw.components(separatedBy: .newlines) where !line.hasPrefix("#") {
            guard let index = line.firstIndex(of: "=") else { continue }
            let key = String(line[..<index]).trimmingCharacters(in: .whitespaces)
            var value = String(line[line.index(after: index)...]).trimmingCharacters(in: .whitespaces)
            if (value.hasPrefix("\"") && value.hasSuffix("\"")) || (value.hasPrefix("'") && value.hasSuffix("'")) {
                value.removeFirst(); value.removeLast()
            }
            values[key] = value
        }
        let host = values["SMB_SERVER"] ?? ""
        let sharePath = (values["SMB_PATH"] ?? "").split(separator: "/").map(String.init)
        guard let url = URL(string: "smb://" + host), let share = sharePath.first else { throw SmbTransportError.invalidEndpoint }
        let base = sharePath.dropFirst().joined(separator: "/")
        let username = values["SMB_USERNAME"] ?? ""
        let credentials = LiveCredentials(secret: values["SMB_PASSWORD"] ?? "")
        let connection = SmbWireConnection(server: url, share: share, username: username, domain: "", profileID: "live-spike")
        let transport = SmbTransport(credentials: credentials) { connection, password in
            try NativeSmbSession(connection: connection, password: password)
        }
        try await transport.test(connection)
        print("PASS authenticated connection and disconnect")
        let root = try await transport.list(connection, path: "")
        print("PASS root listing: \(root.count) raw entries")
        var entries = try await transport.list(connection, path: base)
        print("Entry types: files=\(entries.filter { $0.kind == .file }.count), directories=\(entries.filter { $0.kind == .directory }.count)")
        let folders = entries.filter { $0.kind == .directory && !$0.name.hasPrefix(".") }
        for folder in folders.prefix(30) {
            entries += try await transport.list(connection, path: folder.path)
        }
        print("PASS nested listing: \(entries.count) raw entries")
        for key in values.keys.sorted() where key.hasPrefix("FILE") {
            if let entry = entries.first(where: { $0.name == values[key] }) {
                print("Fixture \(key): type=\(entry.kind), bytes=\(entry.size ?? -1)")
            }
        }
        let candidates = entries.filter { entry in values.filter { $0.key.hasPrefix("FILE") }.values.contains(entry.name) }
        guard let entry = candidates.first(where: { $0.name == values["FILE5"] }) else { throw SmbTransportError.io }
        let staging = FileManager.default.temporaryDirectory.appendingPathComponent("leaf-live-\(UUID().uuidString)")
        try FileManager.default.createDirectory(at: staging, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: staging) }
        let output = staging.appendingPathComponent("download")
        let path = entry.path
        try await transport.download(connection, path: path, to: output)
        let size = try output.resourceValues(forKeys: [.fileSizeKey]).fileSize ?? 0
        guard entry.size == Int64(size) else { throw SmbTransportError.io }
        print("PASS full streamed download: \(size) bytes, reconnect after prior operations")
        let session = try NativeSmbSession(connection: connection, password: credentials.secret)
        try await session.connect()
        let partial = staging.appendingPathComponent("partial")
        let large = candidates.max(by: { ($0.size ?? 0) < ($1.size ?? 0) })!
        do {
            try await session.download(path: large.path, to: partial) { _, _ in false }
            throw SmbTransportError.io
        } catch is CancellationError {
            // The adapter waits for native IO to stop before surfacing cancellation.
        }
        await session.disconnect()
        let partialSize = try partial.resourceValues(forKeys: [.fileSizeKey]).fileSize ?? 0
        guard partialSize > 0, Int64(partialSize) < (large.size ?? 0) else { throw SmbTransportError.io }
        try await Task.sleep(nanoseconds: 500_000_000)
        let laterSize = try partial.resourceValues(forKeys: [.fileSizeKey]).fileSize ?? 0
        guard laterSize == partialSize else { throw SmbTransportError.io }
        print("PASS callback cancellation stopped native IO at \(partialSize) bytes; no writes after completion")
        try await transport.test(connection)
        print("PASS reconnect after cancelled download")
    }
}
