import CoreDatastore
import Foundation

/// Renews the access token, once, however many callers ask at the same time.
///
/// The port of `TokenAuthenticator`. The three details that were not stylistic
/// there are not stylistic here either:
///
///  - **Single flight.** `POST /auth/refresh` *rotates* the refresh token: the
///    one sent is invalidated. Two refreshes racing with the same token
///    therefore destroy each other, and the user is signed out for no reason.
///    Kotlin used a `Mutex`; an actor holding the in-flight `Task` is the same
///    guarantee, and callers that arrive late get the *result* rather than a
///    turn to redo the work.
///
///  - **Comparing against the stale token.** Once one caller has refreshed, the
///    others must notice and reuse the result instead of each burning another
///    rotation.
///
///  - **A failed refresh ends the session.** Not merely marks it stale. This
///    matches `TokenAuthenticator` exactly, including the part where an
///    unreachable server counts as a failure — unlike `AuthRepository`'s
///    startup restore, which deliberately does not. Making only one of the two
///    clients kinder here would be a behavioural fork, which is the one thing
///    two implementations of the same product cannot afford.
public actor TokenRefresher {

    private let sessionStore: SessionStore
    private let authAPI: AuthAPI
    private var inFlight: Task<String?, Never>?

    /// - Parameter authAPI: must be built on the **bare** client, the one with
    ///   no refresher. Otherwise a failing refresh triggers its own refresh.
    public init(sessionStore: SessionStore, authAPI: AuthAPI) {
        self.sessionStore = sessionStore
        self.authAPI = authAPI
    }

    /// - Parameter stale: the token the failed request carried, or nil if it
    ///   carried none.
    /// - Returns: a usable access token, or nil if the session is over.
    public func freshToken(replacing stale: String?) async -> String? {
        // Someone else may have refreshed while this caller was getting its
        // 401 back.
        if let current = await sessionStore.accessToken, current != stale {
            return current
        }
        // Between this read and the assignment below there is no suspension
        // point, so two callers cannot both start a refresh.
        if let inFlight {
            return await inFlight.value
        }

        let task = Task<String?, Never> { [sessionStore, authAPI] in
            guard let refreshToken = await sessionStore.refreshToken else { return nil }
            do {
                let result = try await authAPI.refresh(.init(refreshToken: refreshToken))
                await sessionStore.save(
                    accessToken: result.accessToken,
                    refreshToken: result.refreshToken
                )
                return result.accessToken
            } catch {
                await sessionStore.clear()
                return nil
            }
        }
        inFlight = task
        let token = await task.value
        inFlight = nil
        return token
    }
}
