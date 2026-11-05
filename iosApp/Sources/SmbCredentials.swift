import Foundation
import Security

protocol SmbCredentialStore: Sendable {
    func password(profileID: String) throws -> String?
    func save(password: String, profileID: String) throws
    func delete(profileID: String) throws
}

/// Passwords never enter preferences or the shared connection model.
struct SmbKeychainCredentials: SmbCredentialStore {
    let service: String

    init(service: String = "net.digitalbc.leafreader.smb") {
        self.service = service
    }

    struct KeychainError: Error, LocalizedError {
        let status: OSStatus
        var errorDescription: String? { "Could not access the saved SMB password (\(status))." }
    }

    private func query(_ profileID: String) -> [String: Any] {
        [kSecClass as String: kSecClassGenericPassword,
         kSecAttrService as String: service,
         kSecAttrAccount as String: profileID,
         kSecAttrSynchronizable as String: false]
    }

    func password(profileID: String) throws -> String? {
        var query = query(profileID)
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne
        var result: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        if status == errSecItemNotFound { return nil }
        guard status == errSecSuccess, let data = result as? Data,
              let password = String(data: data, encoding: .utf8) else {
            throw KeychainError(status: status == errSecSuccess ? errSecDecode : status)
        }
        return password
    }

    func save(password: String, profileID: String) throws {
        let attributes: [String: Any] = [
            kSecValueData as String: Data(password.utf8),
            kSecAttrAccessible as String: kSecAttrAccessibleWhenUnlockedThisDeviceOnly
        ]
        let query = query(profileID)
        var status = SecItemUpdate(query as CFDictionary, attributes as CFDictionary)
        if status == errSecItemNotFound {
            status = SecItemAdd(query.merging(attributes) { _, new in new } as CFDictionary, nil)
        }
        guard status == errSecSuccess else { throw KeychainError(status: status) }
    }

    func delete(profileID: String) throws {
        let status = SecItemDelete(query(profileID) as CFDictionary)
        guard status == errSecSuccess || status == errSecItemNotFound else {
            throw KeychainError(status: status)
        }
    }
}
