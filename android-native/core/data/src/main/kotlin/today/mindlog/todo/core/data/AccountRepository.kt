package today.mindlog.todo.core.data

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import today.mindlog.todo.core.datastore.SessionStore
import today.mindlog.todo.core.network.api.AccountApi
import today.mindlog.todo.core.network.api.TodoApi
import today.mindlog.todo.core.network.model.ApiKey
import today.mindlog.todo.core.network.model.ApiKeyCreateRequest
import today.mindlog.todo.core.network.model.ApiKeyCreated
import today.mindlog.todo.core.network.model.Attachment
import today.mindlog.todo.core.network.model.AttachmentCreateRequest
import today.mindlog.todo.core.network.model.ProfileUpdateRequest
import today.mindlog.todo.core.network.model.StorageUsage
import today.mindlog.todo.core.network.model.User
import javax.inject.Inject
import javax.inject.Singleton

sealed interface AccountState {
    data object Loading : AccountState
    data class Ready(
        val user: User,
        val apiKeys: List<ApiKey>,
        val storage: StorageUsage?,
    ) : AccountState

    data class Failed(val cause: Throwable) : AccountState
}

/**
 * Compte : profil, clés d'API, occupation disque, export.
 *
 * Le secret d'une clé n'est JAMAIS conservé ici. Le serveur ne le rend qu'à la
 * création ; le porter dans l'état du dépôt le ferait vivre en mémoire bien
 * après l'écran qui devait l'afficher, sans qu'aucune fonction n'en dépende.
 * C'est l'appelant qui le reçoit, le montre une fois, et l'oublie.
 */
@Singleton
class AccountRepository @Inject constructor(
    private val account: AccountApi,
    private val todo: TodoApi,
    private val sessionStore: SessionStore,
) {
    private val _state = MutableStateFlow<AccountState>(AccountState.Loading)
    val state: StateFlow<AccountState> = _state.asStateFlow()

    suspend fun refresh() {
        if (sessionStore.accessToken() == null) return
        _state.value = runCatching {
            coroutineScope {
                val user = async { todo.me() }
                val keys = async { account.listApiKeys() }
                // L'occupation disque est accessoire : son échec ne doit pas
                // priver l'écran du profil et des clés.
                val storage = async { runCatching { account.storage() }.getOrNull() }
                AccountState.Ready(user.await(), keys.await(), storage.await())
            }
        }.fold({ it }, { AccountState.Failed(it) })
    }

    suspend fun updateProfile(displayName: String? = null, avatarUrl: String? = null): Result<User> =
        runCatching {
            account.updateProfile(
                ProfileUpdateRequest(displayName = displayName, avatarUrl = avatarUrl),
            )
        }.onSuccess { refresh() }

    /** Le secret est dans le retour, et nulle part ailleurs. */
    suspend fun createApiKey(name: String?): Result<ApiKeyCreated> =
        runCatching { account.createApiKey(ApiKeyCreateRequest(name = name?.takeIf { it.isNotBlank() })) }
            .onSuccess { refresh() }

    suspend fun revokeApiKey(id: String): Result<Unit> =
        runCatching { account.revokeApiKey(id) }.onSuccess { refresh() }

    /** JSON brut de la sauvegarde ; l'écran décide d'en faire un partage ou un fichier. */
    suspend fun export(): Result<String> = runCatching { account.export().toString() }

    // --- pièces jointes ---------------------------------------------------
    // Câblées ici mais AUCUN écran ne les consomme encore : il faudrait une vue
    // de détail de tâche, qui n'existe pas dans ce client. Signalé plutôt que
    // laissé croire au support complet.

    suspend fun attachments(taskId: String): Result<List<Attachment>> =
        runCatching { account.taskAttachments(taskId) }

    suspend fun addAttachment(
        taskId: String,
        filename: String,
        text: String,
        mime: String? = null,
    ): Result<Attachment> = runCatching {
        account.addAttachment(
            taskId,
            AttachmentCreateRequest(filename = filename, mime = mime, content = text),
        )
    }

    suspend fun attachment(id: String): Result<Attachment> = runCatching { account.attachment(id) }

    suspend fun deleteAttachment(id: String): Result<Unit> =
        runCatching { account.deleteAttachment(id) }
}
