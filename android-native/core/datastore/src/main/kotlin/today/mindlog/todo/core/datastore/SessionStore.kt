package today.mindlog.todo.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "mindlog_todo_session",
)

/** What a successful sign-in yields. Mirrors the server's AuthResult. */
data class Session(
    val accessToken: String,
    val refreshToken: String,
    val userId: String,
    val email: String,
)

/**
 * Holds the tokens, and only the tokens.
 *
 * The access token lives **in memory only**. It expires in fifteen minutes, so
 * persisting it saves nothing — on the next launch the refresh token produces a
 * new one anyway — while widening the window in which it could be read off
 * disk.
 *
 * The refresh token is good for thirty days, so it is persisted, encrypted by
 * [KeystoreCipher].
 */
@Singleton
class SessionStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val cipher: KeystoreCipher,
) {
    private val _accessToken = MutableStateFlow<String?>(null)

    private val _signedIn = MutableStateFlow<Boolean?>(null)

    /** null until the stored session has been looked at — the splash waits on it. */
    val signedIn: StateFlow<Boolean?> = _signedIn.asStateFlow()

    fun accessToken(): String? = _accessToken.value

    suspend fun refreshToken(): String? {
        val blob = context.sessionDataStore.data.first()[REFRESH_TOKEN] ?: return null
        return try {
            cipher.decrypt(blob)
        } catch (_: KeystoreCipher.KeyPermanentlyUnusable) {
            // The blob outlived its key. Nothing can recover it; drop it and
            // let the user sign in again.
            clear()
            null
        }
    }

    suspend fun save(accessToken: String, refreshToken: String) {
        _accessToken.value = accessToken
        context.sessionDataStore.edit { it[REFRESH_TOKEN] = cipher.encrypt(refreshToken) }
        _signedIn.value = true
    }

    /** The access token was rotated but the session is unchanged. */
    fun updateAccessToken(accessToken: String) {
        _accessToken.value = accessToken
    }

    suspend fun clear() {
        _accessToken.value = null
        context.sessionDataStore.edit { it.remove(REFRESH_TOKEN) }
        _signedIn.value = false
    }

    /** Called once at startup: is there anything to resume from? */
    suspend fun markRestored(hasSession: Boolean) {
        _signedIn.value = hasSession
    }

    private companion object {
        val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
    }
}
