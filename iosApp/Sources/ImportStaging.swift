import Foundation

enum ImportStaging {
    static var downloadRoot: URL {
        FileManager.default.temporaryDirectory.appendingPathComponent("LeafSMBDownloads", isDirectory: true)
    }

    /// Recover abandoned work without touching fresh operations or following symlinks.
    /// Call only with an app-owned staging root, never a server-supplied path.
    static func removeStaleDirectories(in root: URL, now: Date = Date()) {
        let keys: Set<URLResourceKey> = [.isDirectoryKey, .isSymbolicLinkKey, .contentModificationDateKey]
        guard let children = try? FileManager.default.contentsOfDirectory(at: root,
            includingPropertiesForKeys: Array(keys)) else { return }
        for child in children {
            guard UUID(uuidString: child.lastPathComponent) != nil,
                  let values = try? child.resourceValues(forKeys: keys),
                  values.isDirectory == true, values.isSymbolicLink != true,
                  let modified = values.contentModificationDate,
                  now.timeIntervalSince(modified) > 24 * 60 * 60 else { continue }
            try? FileManager.default.removeItem(at: child)
        }
    }
}
