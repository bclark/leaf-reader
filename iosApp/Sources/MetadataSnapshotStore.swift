import Foundation
import CryptoKit
import os

/// Serializes metadata work away from UI isolation. Names reveal no server/account identifiers.
actor MetadataSnapshotStore {
    private let directory: URL
    private let decoder = JSONDecoder()

    init(directory: URL) {
        self.directory = directory
    }

    @MainActor
    static func scoped(defaults: UserDefaults, directory: URL? = nil) -> MetadataSnapshotStore {
        let namespaceKey = "leaf.jellyfin.snapshotNamespace"
        let namespace = defaults.string(forKey: namespaceKey).flatMap(UUID.init(uuidString:)) ?? UUID()
        defaults.set(namespace.uuidString, forKey: namespaceKey)
        let root = directory ?? FileManager.default.urls(for: .applicationSupportDirectory,
            in: .userDomainMask)[0].appendingPathComponent("JellyfinMetadata", isDirectory: true)
            .appendingPathComponent(namespace.uuidString, isDirectory: true)
        return MetadataSnapshotStore(directory: root)
    }

    @MainActor
    func restore<T: Decodable & Sendable>(_ type: T.Type, key: String, defaults: UserDefaults) async -> T? {
        let legacy = defaults.data(forKey: key)
        do {
            let value = try await read(type, key: key, legacy: legacy)
            if value != nil { defaults.removeObject(forKey: key) }
            return value
        } catch {
            // Low storage must not hide a valid legacy offline catalogue or delete its only copy.
            guard let legacy else { return nil }
            return try? await decode(type, data: legacy)
        }
    }

    private func decode<T: Decodable & Sendable>(_ type: T.Type, data: Data) throws -> T {
        try decoder.decode(type, from: data)
    }

    private func file(for key: String) -> URL {
        let digest = SHA256.hash(data: Data(key.utf8)).map { String(format: "%02x", $0) }.joined()
        return directory.appendingPathComponent(digest).appendingPathExtension("json")
    }

    func read<T: Decodable & Sendable>(_ type: T.Type, key: String, legacy: Data?) throws -> T? {
        let interval = LeafPerformance.signposter.beginInterval("Snapshot read", id: LeafPerformance.signposter.makeSignpostID())
        defer { LeafPerformance.signposter.endInterval("Snapshot read", interval) }
        let url = file(for: key)
        if let data = try? Data(contentsOf: url), let value = try? decoder.decode(type, from: data) {
            return value
        }
        guard let legacy else { return nil }
        // Decode before publishing the migration; failed writes leave the legacy data intact.
        let value = try decoder.decode(type, from: legacy)
        try writeData(legacy, keys: [key])
        return value
    }

    func write(keys: [String], encode: @Sendable () throws -> Data) throws {
        let interval = LeafPerformance.signposter.beginInterval("Snapshot write", id: LeafPerformance.signposter.makeSignpostID())
        defer { LeafPerformance.signposter.endInterval("Snapshot write", interval) }
        try writeData(encode(), keys: keys)
    }

    private func writeData(_ data: Data, keys: [String]) throws {
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        for key in keys {
            try data.write(to: file(for: key), options: .atomic)
        }
    }

    func data(for key: String) throws -> Data {
        try Data(contentsOf: file(for: key))
    }
}

/// Local Instruments intervals only. Never include titles, paths, hosts or account identifiers.
enum LeafPerformance {
    static let signposter = OSSignposter(subsystem: "net.digitalbc.leafreader", category: "Performance")
}
