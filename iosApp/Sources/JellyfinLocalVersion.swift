import Foundation

/// Detect replacement/deletion while a remote request is suspended without rehashing a comic
/// on the main thread. Full ownership checksum validation precedes each sync operation.
struct JellyfinLocalVersion: Equatable {
    let size: UInt64
    let inode: UInt64
    let modified: Date?

    init(_ url: URL) throws {
        let values = try FileManager.default.attributesOfItem(atPath: url.path)
        size = (values[.size] as? NSNumber)?.uint64Value ?? 0
        inode = (values[.systemFileNumber] as? NSNumber)?.uint64Value ?? 0
        modified = values[.modificationDate] as? Date
    }
}
