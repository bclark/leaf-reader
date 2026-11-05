import Foundation
import LeafShared

/// Only nonsecret fields are encoded. Every load and save goes through shared validation.
struct SmbProfile: Codable, Equatable {
    var host = ""
    var share = ""
    var username = ""
    static let credentialID = "primary-smb"
    static let preferencesKey = "smb.connection.v1"

    func configuration() throws -> SmbConnectionConfig {
        try SmbConnectionConfig.companion.fromInputs(hostInput: host, shareInput: share, usernameInput: username)
    }

    func connection(profileID: String = Self.credentialID) throws -> SmbWireConnection {
        let config = try configuration()
        var url = URLComponents()
        url.scheme = "smb"
        url.host = config.host.contains(":") ? "[\(config.host)]" : config.host
        url.port = Int(config.port)
        guard let server = url.url else { throw SmbTransportError.invalidEndpoint }
        return SmbWireConnection(server: server, share: config.share, username: config.username,
                                 domain: config.domain, profileID: profileID)
    }

    static func load(defaults: UserDefaults = .standard) -> SmbProfile? {
        guard let data = defaults.data(forKey: preferencesKey),
              let profile = try? JSONDecoder().decode(Self.self, from: data),
              (try? profile.configuration()) != nil else { return nil }
        return profile
    }

    func save(password: String, defaults: UserDefaults = .standard,
              credentials: any SmbCredentialStore = SmbKeychainCredentials()) throws {
        _ = try configuration()
        let data = try JSONEncoder().encode(self)
        try credentials.save(password: password, profileID: Self.credentialID)
        defaults.set(data, forKey: Self.preferencesKey)
    }

    static func forget(defaults: UserDefaults = .standard,
                       credentials: any SmbCredentialStore = SmbKeychainCredentials()) throws {
        try credentials.delete(profileID: credentialID)
        defaults.removeObject(forKey: preferencesKey)
    }
}

extension SmbTransportError {
    var sharedFailure: SmbFailureKind {
        switch self {
        case .invalidEndpoint: return .invalidConfiguration
        case .authentication: return .authentication
        case .unavailable: return .unavailable
        case .timeout: return .timeout
        case .busy, .io: return .io
        }
    }
}

/// Translate wire values only; shared code owns path validity, filtering and ordering.
enum SmbSharedAdapter {
    static func entries(_ wire: [SmbWireEntry], parent: String) -> [SmbEntry] {
        wire.compactMap { entry in
            guard entry.kind == .file || entry.kind == .directory,
                  let path = try? SmbPaths.shared.child(parent: parent, name: entry.name) else { return nil }
            return SmbEntry(name: entry.name, path: path, isDirectory: entry.kind == .directory,
                            sizeBytes: max(0, entry.size ?? 0))
        }
    }
}
