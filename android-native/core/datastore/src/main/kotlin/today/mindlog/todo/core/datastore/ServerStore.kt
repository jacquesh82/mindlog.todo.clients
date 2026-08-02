package today.mindlog.todo.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.serverDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "mindlog_todo_server",
)

/**
 * Which deployment the app talks to.
 *
 * `-PmindlogEnv` only sets the *default* (see [BuildConfig.DEFAULT_BASE_URL]);
 * the value can be changed at runtime, which is how a debug build gets pointed
 * at qualif without a recompile. Same arrangement as the archived talk client.
 */
@Singleton
class ServerStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    val baseUrl: Flow<String> = context.serverDataStore.data.map { prefs ->
        prefs[BASE_URL] ?: BuildConfig.DEFAULT_BASE_URL
    }

    suspend fun current(): String = baseUrl.first()

    /**
     * The trailing slash is not cosmetic: prod and qualif URLs carry the `/app`
     * sub-path, and Retrofit drops the last segment of a base URL that lacks
     * one — turning `…/app` into `…/`, which 404s in production while local
     * (no sub-path) keeps working.
     */
    suspend fun set(url: String) {
        val normalised = if (url.endsWith("/")) url else "$url/"
        context.serverDataStore.edit { it[BASE_URL] = normalised }
    }

    suspend fun reset() {
        context.serverDataStore.edit { it.remove(BASE_URL) }
    }

    val environment: String get() = BuildConfig.MINDLOG_ENV

    private companion object {
        val BASE_URL = stringPreferencesKey("base_url")
    }
}
