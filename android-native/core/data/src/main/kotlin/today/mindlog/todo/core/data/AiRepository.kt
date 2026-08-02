package today.mindlog.todo.core.data

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import today.mindlog.todo.core.datastore.SessionStore
import today.mindlog.todo.core.network.api.AiApi
import today.mindlog.todo.core.network.api.NotesApi
import today.mindlog.todo.core.network.api.TodoApi
import today.mindlog.todo.core.network.model.AiModelsQueryRequest
import today.mindlog.todo.core.network.model.AiSettings
import today.mindlog.todo.core.network.model.AiSettingsUpdateRequest
import today.mindlog.todo.core.network.model.AiUsage
import today.mindlog.todo.core.network.model.AskResult
import today.mindlog.todo.core.network.model.ChatModel
import today.mindlog.todo.core.network.model.NotePageHit
import today.mindlog.todo.core.network.model.NoteSearchRequest
import today.mindlog.todo.core.network.model.TaskAskRequest
import today.mindlog.todo.core.network.model.TaskSearchHit
import today.mindlog.todo.core.network.model.TaskSearchRequest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Une recherche interroge DEUX corpus ; l'écran les présente séparément.
 *
 * Les échecs sont PORTÉS, pas avalés : une liste vide parce que le corpus n'a
 * rien trouvé et une liste vide parce que la requête a échoué se ressemblent à
 * l'écran, et ne veulent pas dire la même chose.
 */
data class SearchResults(
    val tasks: List<TaskSearchHit> = emptyList(),
    val notes: List<NotePageHit> = emptyList(),
    val tasksFailed: Boolean = false,
    val notesFailed: Boolean = false,
)

sealed interface AiSettingsState {
    data object Loading : AiSettingsState
    data class Ready(val settings: AiSettings, val usage: AiUsage?) : AiSettingsState
    data class Failed(val cause: Throwable) : AiSettingsState
}

/**
 * Recherche sémantique, questions en langue naturelle, et réglages du modèle.
 *
 * Regroupés parce qu'ils partagent une même contrainte, absente du reste de
 * l'app : ces appels coûtent des secondes et de l'argent. Rien ici n'est
 * rafraîchi automatiquement, rien n'est relancé sur un événement — tout part
 * d'un geste explicite de l'utilisateur.
 */
@Singleton
class AiRepository @Inject constructor(
    private val ai: AiApi,
    private val todo: TodoApi,
    private val notes: NotesApi,
    private val sessionStore: SessionStore,
) {
    private val _settings = MutableStateFlow<AiSettingsState>(AiSettingsState.Loading)
    val settings: StateFlow<AiSettingsState> = _settings.asStateFlow()

    // --- recherche --------------------------------------------------------

    /**
     * Tâches et notes sont interrogées EN PARALLÈLE, et un corpus qui échoue ne
     * vide pas l'autre : mieux vaut la moitié des résultats que rien, tant que
     * l'écran dit laquelle manque.
     */
    suspend fun search(query: String, k: Int = 10): SearchResults = coroutineScope {
        val tasks = async { runCatching { todo.searchTasks(TaskSearchRequest(query = query, k = k)) } }
        val pages = async { runCatching { notes.searchPages(NoteSearchRequest(query = query, k = k)) } }
        val taskResult = tasks.await()
        val pageResult = pages.await()
        SearchResults(
            tasks = taskResult.getOrDefault(emptyList()),
            notes = pageResult.getOrDefault(emptyList()),
            tasksFailed = taskResult.isFailure,
            notesFailed = pageResult.isFailure,
        )
    }

    /** Question sur les tâches ; la réponse cite ses sources. */
    suspend fun ask(question: String): Result<AskResult> =
        runCatching { todo.ask(TaskAskRequest(question = question)) }

    // --- réglages ---------------------------------------------------------

    suspend fun refreshSettings() {
        if (sessionStore.accessToken() == null) return
        _settings.value = runCatching {
            coroutineScope {
                val settings = async { ai.settings() }
                // La consommation est accessoire : en mode auto-hébergé elle
                // peut échouer sans que les réglages cessent d'être lisibles.
                val usage = async { runCatching { ai.usage() }.getOrNull() }
                AiSettingsState.Ready(settings.await(), usage.await())
            }
        }.fold({ it }, { AiSettingsState.Failed(it) })
    }

    /**
     * `apiKey` part et ne revient jamais : la réponse ne porte que `hasKey`.
     * Ne pas la conserver ici non plus — le dépôt n'a aucune raison de garder
     * en mémoire un secret que le serveur a déjà.
     */
    suspend fun updateSettings(
        provider: String? = null,
        model: String? = null,
        apiKey: String? = null,
    ): Result<AiSettings> = runCatching {
        ai.updateSettings(
            AiSettingsUpdateRequest(
                provider = provider?.let(AiSettingsUpdateRequest.Provider::valueOf),
                model = model,
                apiKey = apiKey,
            ),
        )
    }.onSuccess { refreshSettings() }

    suspend fun deleteKey(): Result<AiSettings> =
        runCatching { ai.deleteKey() }.onSuccess { refreshSettings() }

    /** Liste vivante des modèles d'un fournisseur, clé éventuelle à l'appui. */
    suspend fun models(provider: String, apiKey: String? = null): Result<List<ChatModel>> =
        runCatching {
            ai.models(
                AiModelsQueryRequest(
                    provider = AiModelsQueryRequest.Provider.valueOf(provider),
                    apiKey = apiKey,
                ),
            ).models
        }

    // --- gabarits de prompts ---------------------------------------------

    suspend fun prompts() = runCatching { ai.listPrompts() }

    suspend fun resetPrompt(key: String) = runCatching { ai.resetPrompt(key) }

    suspend fun logs(limit: Int = 50) = runCatching { ai.logs(limit) }
}
