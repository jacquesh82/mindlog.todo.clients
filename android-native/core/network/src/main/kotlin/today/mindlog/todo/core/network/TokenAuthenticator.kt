package today.mindlog.todo.core.network

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import today.mindlog.todo.core.datastore.SessionStore
import today.mindlog.todo.core.network.api.AuthApi
import today.mindlog.todo.core.network.model.RefreshRequest
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Renews the access token on a 401 and replays the request once.
 *
 * Three details here are not stylistic:
 *
 *  - **The mutex.** `POST /auth/refresh` *rotates* the refresh token: the one
 *    sent is invalidated. Two refreshes racing with the same token therefore
 *    destroy each other, and the user is signed out for no reason.
 *
 *  - **Comparing against the stale token.** Once one caller has refreshed, the
 *    others queued behind the mutex must notice and reuse the result instead of
 *    each burning another rotation.
 *
 *  - **Bailing out on `priorResponse`.** One replay, never a loop. A 401 that
 *    survives a fresh token is not an expiry problem.
 *
 * [authApi] arrives through a [Provider] backed by the *bare* client — the one
 * without this authenticator. Giving it the full client would mean a failing
 * refresh triggering its own refresh, forever.
 */
@Singleton
class TokenAuthenticator @Inject constructor(
    private val sessionStore: SessionStore,
    private val authApi: Provider<AuthApi>,
) : Authenticator {

    private val mutex = Mutex()

    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.priorResponse != null) return null

        val staleToken = response.request.header("Authorization")?.removePrefix("Bearer ")

        val freshToken = runBlocking {
            mutex.withLock {
                // Someone else may have refreshed while we waited.
                sessionStore.accessToken()
                    ?.takeIf { it != staleToken }
                    ?.let { return@withLock it }

                val refreshToken = sessionStore.refreshToken() ?: return@withLock null
                runCatching { authApi.get().refresh(RefreshRequest(refreshToken)) }
                    .onSuccess { sessionStore.save(it.accessToken, it.refreshToken) }
                    .onFailure { sessionStore.clear() } // the session is gone, not merely stale
                    .getOrNull()
                    ?.accessToken
            }
        } ?: return null

        return response.request.newBuilder()
            .header("Authorization", "Bearer $freshToken")
            .build()
    }
}
