package today.mindlog.todo.core.data

import kotlinx.coroutines.flow.StateFlow
import retrofit2.HttpException
import today.mindlog.todo.core.datastore.ServerStore
import today.mindlog.todo.core.datastore.SessionStore
import today.mindlog.todo.core.network.api.AuthApi
import today.mindlog.todo.core.network.model.AuthResult
import today.mindlog.todo.core.network.model.CompleteMindlogIdRequest
import today.mindlog.todo.core.network.model.LoginRequest
import today.mindlog.todo.core.network.model.RefreshRequest
import javax.inject.Inject
import javax.inject.Singleton

/** Which sign-in paths this deployment actually offers. */
data class AuthProviders(
    val mindlogId: Boolean = false,
    val google: Boolean = false,
    val passwordReset: Boolean = false,
)

@Singleton
class AuthRepository @Inject constructor(
    private val authApi: AuthApi,
    private val sessionStore: SessionStore,
    private val serverStore: ServerStore,
) {
    /** null while the stored session is still being looked at. */
    val signedIn: StateFlow<Boolean?> = sessionStore.signedIn

    /**
     * Resumes a stored session, or reports that there is none.
     *
     * Deliberately a *refresh*, not a token read: the access token is never
     * persisted, so the only proof a session is still alive is the server
     * accepting the refresh token. A rejected one means it expired or was
     * revoked, and the user goes to the sign-in screen.
     */
    suspend fun restoreSession() {
        val refreshToken = sessionStore.refreshToken()
        if (refreshToken == null) {
            sessionStore.markRestored(hasSession = false)
            return
        }
        runCatching { authApi.refresh(RefreshRequest(refreshToken)) }
            .onSuccess { sessionStore.save(it.accessToken, it.refreshToken) }
            .onFailure { cause ->
                // Only a refusal ends the session. An unreachable server means
                // we do not know yet — clearing on that would sign the user out
                // every time the app opened without a network, and the stored
                // token is good for thirty days.
                if (cause is HttpException && cause.code() == 401) {
                    sessionStore.clear()
                } else {
                    sessionStore.markRestored(hasSession = false)
                }
            }
    }

    suspend fun login(email: String, password: String): Result<AuthResult> =
        runCatching { authApi.login(LoginRequest(email = email, password = password)) }
            .onSuccess { sessionStore.save(it.accessToken, it.refreshToken) }

    /**
     * Finishes a mindlog id sign-in for an account with no email address —
     * mindlog accounts are handle-based and the recovery email is optional.
     * Without this the button is a dead end for those users.
     */
    suspend fun completeMindlogId(pendingToken: String, email: String): Result<AuthResult> =
        runCatching {
            authApi.completeMindlogId(CompleteMindlogIdRequest(pendingToken, email))
        }.onSuccess { sessionStore.save(it.accessToken, it.refreshToken) }

    /** Called when the OAuth round trip hands the tokens back on the deep link. */
    suspend fun adoptTokens(accessToken: String, refreshToken: String) {
        sessionStore.save(accessToken, refreshToken)
    }

    suspend fun logout() {
        val refreshToken = sessionStore.refreshToken()
        // Clear locally whatever the server says: a network failure must not
        // leave the user apparently signed in.
        if (refreshToken != null) runCatching { authApi.logout(RefreshRequest(refreshToken)) }
        sessionStore.clear()
    }

    suspend fun authProviders(): AuthProviders =
        runCatching { authApi.version() }
            .map {
                AuthProviders(
                    mindlogId = it.authProviders.mindlogId,
                    google = it.authProviders.google,
                    passwordReset = it.authProviders.passwordReset,
                )
            }
            // Unreachable server: offer only the path that cannot be
            // misconfigured, rather than buttons that answer 503.
            .getOrDefault(AuthProviders())

    /** Where the mindlog id round trip starts; opened in a Custom Tab. */
    suspend fun mindlogIdAuthUrl(create: Boolean): String {
        val base = serverStore.current()
        val query = buildString {
            append("?client=native")
            if (create) append("&create=1")
        }
        return "${base}api/v1/auth/mindlog-id$query"
    }
}
