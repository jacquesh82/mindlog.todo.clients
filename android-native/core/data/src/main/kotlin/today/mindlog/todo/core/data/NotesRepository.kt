package today.mindlog.todo.core.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import today.mindlog.todo.core.datastore.SessionStore
import today.mindlog.todo.core.network.api.NotesApi
import today.mindlog.todo.core.network.model.DrawCleanupRequest
import today.mindlog.todo.core.network.model.NotePage
import today.mindlog.todo.core.network.model.NotePageHit
import today.mindlog.todo.core.network.model.NotePageSummary
import today.mindlog.todo.core.network.model.Notebook
import today.mindlog.todo.core.network.model.NotebookCreateRequest
import today.mindlog.todo.core.network.model.NotebookRagRequest
import today.mindlog.todo.core.network.model.NotebookUpdateRequest
import today.mindlog.todo.core.network.model.NoteSearchRequest
import today.mindlog.todo.core.network.model.PageCreateRequest
import today.mindlog.todo.core.network.model.PageUpdateRequest
import javax.inject.Inject
import javax.inject.Singleton

sealed interface NotebooksState {
    data object Loading : NotebooksState
    data class Ready(val notebooks: List<Notebook>) : NotebooksState
    data class Failed(val cause: Throwable) : NotebooksState
}

/**
 * Carnets et pages.
 *
 * Deux différences assumées avec [TaskRepository] et [NavigationRepository] :
 *
 * 1. **Aucun abonnement au flux de changements.** `ChangeEvent.Entity` ne
 *    connaît que `task`, `project`, `section`, `label` et `filter` : le serveur
 *    n'émet RIEN pour les notes. S'abonner donnerait une illusion de temps réel
 *    que rien n'alimente ; les listes se rechargent donc à l'ouverture et après
 *    chaque mutation locale, ce qui est honnête.
 * 2. **Le contenu d'une page n'est jamais gardé en cache.** Une page peut peser
 *    plusieurs mégaoctets (images collées en data URL) ; en conserver plusieurs
 *    en mémoire ferait tomber l'app avant d'être utile. Seuls les résumés le
 *    sont.
 */
@Singleton
class NotesRepository @Inject constructor(
    private val api: NotesApi,
    private val sessionStore: SessionStore,
) {
    private val _notebooks = MutableStateFlow<NotebooksState>(NotebooksState.Loading)
    val notebooks: StateFlow<NotebooksState> = _notebooks.asStateFlow()

    suspend fun refreshNotebooks() {
        if (sessionStore.accessToken() == null) return
        _notebooks.value = runCatching { api.listNotebooks() }
            .fold({ NotebooksState.Ready(it) }, { NotebooksState.Failed(it) })
    }

    // --- carnets ----------------------------------------------------------

    suspend fun createNotebook(name: String, color: String? = null): Result<Notebook> =
        runCatching { api.createNotebook(NotebookCreateRequest(name = name, color = color)) }
            .onSuccess { refreshNotebooks() }

    suspend fun renameNotebook(id: String, name: String): Result<Notebook> =
        runCatching { api.updateNotebook(id, NotebookUpdateRequest(name = name)) }
            .onSuccess { refreshNotebooks() }

    suspend fun deleteNotebook(id: String): Result<Unit> =
        runCatching { api.deleteNotebook(id) }.onSuccess { refreshNotebooks() }

    // --- pages ------------------------------------------------------------
    // Renvoyées par `Result` plutôt que gardées dans un `StateFlow` : les pages
    // appartiennent à un carnet et n'ont de sens qu'à l'écran qui l'ouvre.

    suspend fun pages(notebookId: String): Result<List<NotePageSummary>> =
        runCatching { api.listPages(notebookId) }

    suspend fun page(id: String): Result<NotePage> = runCatching { api.getPage(id) }

    suspend fun createPage(
        notebookId: String,
        title: String? = null,
        content: String? = null,
    ): Result<NotePage> =
        runCatching { api.createPage(notebookId, PageCreateRequest(title = title, content = content)) }

    /**
     * L'édition envoie titre ET contenu tels que l'écran les tient. Un PATCH par
     * frappe serait intenable ; c'est à l'appelant de temporiser, le dépôt ne
     * décide pas de la cadence de sauvegarde.
     */
    suspend fun savePage(id: String, title: String? = null, content: String? = null): Result<NotePage> =
        runCatching { api.updatePage(id, PageUpdateRequest(title = title, content = content)) }

    suspend fun setPageInRag(id: String, inRag: Boolean): Result<NotePage> =
        runCatching { api.updatePage(id, PageUpdateRequest(inRag = inRag)) }

    suspend fun deletePage(id: String): Result<Unit> = runCatching { api.deletePage(id) }

    suspend fun duplicatePage(id: String): Result<NotePage> = runCatching { api.duplicatePage(id) }

    // --- index sémantique -------------------------------------------------

    /** Renvoie le nombre de pages basculées. */
    suspend fun setNotebookRag(id: String, inRag: Boolean): Result<Int> =
        runCatching { api.setNotebookRag(id, NotebookRagRequest(inRag = inRag)).updated }

    suspend fun search(
        query: String,
        k: Int = 10,
        notebookIds: List<String>? = null,
    ): Result<List<NotePageHit>> = runCatching {
        api.searchPages(NoteSearchRequest(query = query, k = k, notebookIds = notebookIds))
    }

    // --- routes assistées par un modèle -----------------------------------
    // Pas de `refresh` implicite : ces appels durent des secondes, et recharger
    // dans leur dos masquerait à l'écran le moment où le résultat arrive.

    suspend fun summarizeNotebook(id: String): Result<NotePage> =
        runCatching { api.summarizeNotebook(id) }

    /** Aperçu : rien n'est créé tant que l'utilisateur n'a pas choisi. */
    suspend fun extractTasks(pageId: String): Result<List<String>> =
        runCatching { api.extractTasks(pageId).tasks }

    suspend fun cleanupDrawing(request: DrawCleanupRequest): Result<String> =
        runCatching { api.cleanupDrawing(request).svg }
}
