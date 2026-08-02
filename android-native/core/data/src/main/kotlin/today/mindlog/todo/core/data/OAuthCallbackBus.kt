package today.mindlog.todo.core.data

import android.net.Uri
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/** What the browser can hand back on the deep link. */
sealed interface OAuthCallback {
    data class Tokens(val accessToken: String, val refreshToken: String) : OAuthCallback

    /**
     * mindlog id returned a profile with no email address. The server issues a
     * short-lived pending token and the app has to collect one.
     */
    data class NeedsEmail(val pendingToken: String) : OAuthCallback

    data class Failed(val reason: String) : OAuthCallback
}

/**
 * Carries the OAuth return leg from the activity to whichever screen is
 * waiting.
 *
 * The deep link lands on `MainActivity.onNewIntent`, but the outcome belongs to
 * the sign-in screen — and one of the outcomes needs a text field. A shared
 * flow keeps the activity from having to know that.
 */
@Singleton
class OAuthCallbackBus @Inject constructor() {

    private val _callbacks = MutableSharedFlow<OAuthCallback>(extraBufferCapacity = 1)
    val callbacks: SharedFlow<OAuthCallback> = _callbacks.asSharedFlow()

    /**
     * Parses `…://auth/callback#access_token=…&refresh_token=…&expires_in=…`,
     * or `#mindlog_id_pending=…`.
     *
     * The tokens travel in the **fragment**, not the query, so they never reach
     * a server log or a Referer header. The web client reads the same shape.
     */
    fun onDeepLink(uri: Uri): Boolean {
        val fragment = uri.fragment ?: return false
        val params = fragment.split('&')
            .mapNotNull { part ->
                val i = part.indexOf('=')
                if (i <= 0) null else Uri.decode(part.take(i)) to Uri.decode(part.substring(i + 1))
            }
            .toMap()

        val callback = when {
            params["access_token"] != null && params["refresh_token"] != null ->
                OAuthCallback.Tokens(params.getValue("access_token"), params.getValue("refresh_token"))

            params["mindlog_id_pending"] != null ->
                OAuthCallback.NeedsEmail(params.getValue("mindlog_id_pending"))

            params["error"] != null -> OAuthCallback.Failed(params.getValue("error"))

            else -> return false
        }

        return _callbacks.tryEmit(callback)
    }
}
