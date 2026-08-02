import Foundation
import Security

/// The refresh token's store.
///
/// This is where the Android client's `KeystoreCipher` went. There, sixty lines
/// of AES-GCM against a Keystore key were needed because DataStore is a plain
/// file and `androidx.security:security-crypto` is deprecated. Here the
/// platform already provides the encrypted store, so the port is an accessor,
/// not an algorithm.
///
/// One decision carries over unchanged. The Android class deliberately does not
/// set `setUnlockedDeviceRequired`, because the token has to be readable while
/// the screen is locked — that is exactly when the background event stream
/// reconnects. ``kSecAttrAccessibleAfterFirstUnlock`` is the same call:
/// unavailable until the user has unlocked once after boot, available from then
/// on, and never synced to another device.
///
/// The Keystore's `KeyPermanentlyUnusable` has no counterpart: a keychain item
/// cannot outlive its key, so the only failure mode left is "not there", which
/// reads as no session.
public struct Keychain: Sendable {

    private let service: String

    public init(service: String = "today.mindlog.todo.native") {
        self.service = service
    }

    private func query(_ account: String) -> [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ]
    }

    public func string(forKey account: String) -> String? {
        var query = query(account)
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne

        var item: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &item) == errSecSuccess,
              let data = item as? Data
        else { return nil }
        return String(data: data, encoding: .utf8)
    }

    /// Upsert. `SecItemAdd` fails with `errSecDuplicateItem` rather than
    /// replacing, so an existing item is deleted first — the alternative,
    /// branching on an update, is the same two syscalls with more ways to be
    /// wrong.
    public func set(_ value: String, forKey account: String) {
        remove(forKey: account)
        var attributes = query(account)
        attributes[kSecValueData as String] = Data(value.utf8)
        attributes[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlock
        SecItemAdd(attributes as CFDictionary, nil)
    }

    public func remove(forKey account: String) {
        SecItemDelete(query(account) as CFDictionary)
    }
}
