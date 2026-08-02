import CoreDatastore
import CoreNetwork
import Foundation

/// Which sign-in paths this deployment actually offers.
public struct AuthProviders: Sendable, Equatable {
    public var mindlogId: Bool
    public var google: Bool
    public var passwordReset: Bool

    public init(mindlogId: Bool = false, google: Bool = false, passwordReset: Bool = false) {
        self.mindlogId = mindlogId
        self.google = google
        self.passwordReset = passwordReset
    }
}

@MainActor
public final class AuthRepository {

    private let authAPI: AuthAPI
    private let sessionStore: SessionStore
    private let serverStore: ServerStore

    public init(authAPI: AuthAPI, sessionStore: SessionStore, serverStore: ServerStore) {
        self.authAPI = authAPI
        self.sessionStore = sessionStore
        self.serverStore = serverStore
    }

    /// `nil` while the stored session is still being looked at.
    public var signedIn: Bool? { sessionStore.signedIn }

    /// Resumes a stored session, or reports that there is none.
    ///
    /// Deliberately a *refresh*, not a token read: the access token is never
    /// persisted, so the only proof a session is still alive is the server
    /// accepting the refresh token. A rejected one means it expired or was
    /// revoked, and the user goes to the sign-in screen.
    public func restoreSession() async {
        guard let refreshToken = sessionStore.refreshToken else {
            sessionStore.markRestored(hasSession: false)
            return
        }
        do {
            let result = try await authAPI.refresh(.init(refreshToken: refreshToken))
            sessionStore.save(
                accessToken: result.accessToken,
                refreshToken: result.refreshToken
            )
        } catch {
            // Only a refusal ends the session. An unreachable server means we do
            // not know yet — clearing on that would sign the user out every time
            // the app opened without a network, and the stored token is good for
            // thirty days.
            if error.statusCode == 401 {
                sessionStore.clear()
            } else {
                sessionStore.markRestored(hasSession: false)
            }
        }
    }

    public func login(
        email: String,
        password: String
    ) async -> Result<TodoAuthResult, APIError> {
        do {
            let result = try await authAPI.login(.init(email: email, password: password))
            sessionStore.save(
                accessToken: result.accessToken,
                refreshToken: result.refreshToken
            )
            return .success(result)
        } catch {
            return .failure(error)
        }
    }

    /// Finishes a mindlog id sign-in for an account with no email address —
    /// mindlog accounts are handle-based and the recovery email is optional.
    /// Without this the button is a dead end for those users.
    public func completeMindlogId(
        pendingToken: String,
        email: String
    ) async -> Result<TodoAuthResult, APIError> {
        do {
            let result = try await authAPI.completeMindlogId(
                .init(email: email, pendingToken: pendingToken)
            )
            sessionStore.save(
                accessToken: result.accessToken,
                refreshToken: result.refreshToken
            )
            return .success(result)
        } catch {
            return .failure(error)
        }
    }

    /// Called when the OAuth round trip hands the tokens back.
    public func adoptTokens(accessToken: String, refreshToken: String) {
        sessionStore.save(accessToken: accessToken, refreshToken: refreshToken)
    }

    public func logout() async {
        // Clear locally whatever the server says: a network failure must not
        // leave the user apparently signed in.
        if let refreshToken = sessionStore.refreshToken {
            try? await authAPI.logout(.init(refreshToken: refreshToken))
        }
        sessionStore.clear()
    }

    public func authProviders() async -> AuthProviders {
        do {
            let info = try await authAPI.version()
            return AuthProviders(
                mindlogId: info.authProviders.mindlogId,
                google: info.authProviders.google,
                passwordReset: info.authProviders.passwordReset
            )
        } catch {
            // Unreachable server: offer only the path that cannot be
            // misconfigured, rather than buttons that answer 503.
            return AuthProviders()
        }
    }

    /// Where the mindlog id round trip starts; opened in an authentication
    /// session.
    public func mindlogIdAuthURL(create: Bool) -> URL? {
        var query = "?client=native"
        if create { query += "&create=1" }
        return URL(string: "\(serverStore.baseURL.absoluteString)api/v1/auth/mindlog-id\(query)")
    }

    /// The custom scheme the callback comes back on — the app's bundle
    /// identifier, exactly as the Android manifest uses its applicationId. A
    /// prod and a testing install therefore never contend for it.
    public var callbackScheme: String {
        Bundle.main.bundleIdentifier ?? "today.mindlog.todo.native"
    }
}
