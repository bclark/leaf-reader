import Foundation

/// A durable list member, independent of an archive's filename or availability.
/// The reserved prefix cannot be a local filename because it contains a slash.
/// Existing filename entries and their ordering remain unchanged.
struct JellyfinListReference: Codable, Equatable, Sendable {
    let serverID: String
    let userID: String
    let itemID: String

    init(connection: JellyfinConnection, itemID: String) {
        serverID = connection.serverID; userID = connection.userID; self.itemID = itemID
    }

    init?(serverID: String, userID: String, itemID: String) {
        guard [serverID, userID, itemID].allSatisfy(JellyfinConnection.validID) else { return nil }
        self.serverID = serverID; self.userID = userID; self.itemID = itemID
    }

    var token: String {
        // Values are ASCII validated identifiers and cannot contain the separator.
        "jellyfin/" + [serverID, userID, itemID].joined(separator: "/")
    }

    init?(token: String) {
        let fields = token.split(separator: "/", omittingEmptySubsequences: false).map(String.init)
        guard fields.count == 4, fields[0] == "jellyfin" else { return nil }
        self.init(serverID: fields[1], userID: fields[2], itemID: fields[3])
    }

    func belongs(to connection: JellyfinConnection?) -> Bool {
        connection?.serverID == serverID && connection?.userID == userID
    }
}
