import Foundation
import Observation

/// Holds the tokens, and only the tokens.
///
/// The access token lives **in memory only**. It expires in fifteen minutes, so
/// persisting it saves nothing — on the next launch the refresh token produces
/// a new one anyway — while widening the window in which it could be read off
/// disk.
///
/// The refresh token is good for thirty days, so it goes to the ``Keychain``.
///
/// Main-actor isolated rather than an actor, because ``signedIn`` is what the
/// root view switches on: an `@Observable` on the main actor is read by SwiftUI
/// directly, where an actor would need a published mirror. The network layer
/// pays one actor hop per request to read the token, which is nothing next to
/// the request itself.
@MainActor
@Observable
public final class SessionStore {

    private let keychain: Keychain

    /// `nil` until the stored session has been looked at — the root view waits
    /// on it rather than flashing the sign-in screen at every launch.
    public private(set) var signedIn: Bool?

    public private(set) var accessToken: String?

    private var observers: [UUID: AsyncStream<Bool?>.Continuation] = [:]

    public init(keychain: Keychain = Keychain()) {
        self.keychain = keychain
    }

    /// ``signedIn`` as a sequence, for the callers that are not a view.
    ///
    /// `@Observable` covers SwiftUI, but the repositories need to *react* to
    /// signing in — they subscribe to the change stream only once a session
    /// exists, and that guard is load-bearing (see `TaskRepository`). This is
    /// the other half of what a `StateFlow` gives on the Android side: the
    /// current value on subscription, then every change, to each subscriber
    /// independently.
    public func signedInChanges() -> AsyncStream<Bool?> {
        let id = UUID()
        let (stream, continuation) = AsyncStream<Bool?>.makeStream(
            bufferingPolicy: .bufferingNewest(1)
        )
        continuation.onTermination = { [weak self] _ in
            Task { @MainActor in self?.observers[id] = nil }
        }
        observers[id] = continuation
        continuation.yield(signedIn)
        return stream
    }

    private func publish() {
        for continuation in observers.values { continuation.yield(signedIn) }
    }

    public var refreshToken: String? {
        keychain.string(forKey: Self.refreshTokenKey)
    }

    public func save(accessToken: String, refreshToken: String) {
        self.accessToken = accessToken
        keychain.set(refreshToken, forKey: Self.refreshTokenKey)
        signedIn = true
        publish()
    }

    /// The access token was rotated but the session is unchanged.
    public func updateAccessToken(_ accessToken: String) {
        self.accessToken = accessToken
    }

    public func clear() {
        accessToken = nil
        keychain.remove(forKey: Self.refreshTokenKey)
        signedIn = false
        publish()
    }

    /// Called once at startup: is there anything to resume from?
    public func markRestored(hasSession: Bool) {
        signedIn = hasSession
        publish()
    }

    private static let refreshTokenKey = "refresh_token"
}
