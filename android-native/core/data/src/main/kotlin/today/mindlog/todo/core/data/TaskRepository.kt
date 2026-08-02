package today.mindlog.todo.core.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import today.mindlog.todo.core.datastore.SessionStore
import today.mindlog.todo.core.network.ChangeEventStream
import today.mindlog.todo.core.network.api.TodoApi
import today.mindlog.todo.core.data.di.ApplicationScope
import today.mindlog.todo.core.network.model.ChangeEvent
import today.mindlog.todo.core.network.model.Task
import today.mindlog.todo.core.network.model.TaskQuickAddRequest
import today.mindlog.todo.core.network.model.TaskUpdateRequest
import java.time.LocalDate
import java.time.ZoneId
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

sealed interface TasksState {
    data object Loading : TasksState

    /**
     * @param hasMore une page pleine est revenue : il reste probablement des
     *   tâches. C'est la seule information dont on dispose — l'API ne rend pas
     *   de total — mais elle suffit à ne pas s'arrêter en silence.
     * @param loadingMore une page suivante est en vol ; l'écran l'annonce au
     *   lieu de laisser croire que la liste est finie.
     */
    data class Ready(
        val tasks: List<Task>,
        val hasMore: Boolean = false,
        val loadingMore: Boolean = false,
    ) : TasksState

    data class Failed(val cause: Throwable) : TasksState
}

/**
 * The open task list, kept in memory.
 *
 * No local database at milestone 1, and that is a consequence of the API rather
 * than a shortcut: the change stream carries invalidation signals with no
 * payload, there is no `changedSince` cursor and no tombstone, so every event
 * forces a full re-read regardless. A Room layer would add a schema, migrations
 * and a second source of truth without removing one request. Offline writes are
 * what would justify it — and they need a server-side answer for deletions
 * first.
 *
 * Three things cause a reload: opening the screen, a `task` event on the
 * stream, and a local mutation.
 */
@Singleton
class TaskRepository @Inject constructor(
    private val api: TodoApi,
    private val events: ChangeEventStream,
    private val sessionStore: SessionStore,
    private val navigation: NavigationRepository,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<TasksState>(TasksState.Loading)
    val state: StateFlow<TasksState> = _state.asStateFlow()

    /** Vue courante ; « Aujourd'hui » à l'ouverture, comme sur le web. */
    private val _view = MutableStateFlow<TaskView>(TaskView.Today)
    val view: StateFlow<TaskView> = _view.asStateFlow()

    /**
     * Subscribes to the change stream, but only while signed in.
     *
     * The guard is not tidiness. Opening the stream before a session exists
     * sends it out with no `Authorization` header, the server answers 401, and
     * [today.mindlog.todo.core.network.TokenAuthenticator] renews the token —
     * *while* `restoreSession` is renewing it too. Refresh tokens rotate, so
     * whichever of the two arrives second presents an invalidated token, gets a
     * 401, and signs the user out. Waiting for a session removes the second
     * refresher entirely.
     */
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    fun startWatching() {
        scope.launch {
            sessionStore.signedIn
                .flatMapLatest { signedIn ->
                    if (signedIn == true) {
                        events.changes().filter { it.entity == ChangeEvent.Entity.task }
                    } else {
                        emptyFlow()
                    }
                }
                // A quick-add can touch several rows at once; the same 300 ms
                // window the web client settles on collapses that into one
                // reload.
                .debounce(DEBOUNCE_MS)
                .catch { /* the stream reconnects on its own; do not kill the scope */ }
                .collect { refresh() }
        }
    }

    /**
     * Sélectionne ce que la liste montre. Le rechargement passe par le serveur,
     * y compris pour un simple projet : filtrer localement supposerait d'avoir
     * DÉJÀ toutes les tâches, ce que `limit=200` ne garantit pas — la liste
     * paraîtrait juste jusqu'au jour où elle omettrait des lignes en silence.
     */
    fun select(view: TaskView) {
        _view.value = view
        scope.launch { refresh() }
    }

    /**
     * Recharge depuis le début — mais en redemandant AUTANT de tâches que
     * l'utilisateur en avait déjà déroulé, dans la limite du maximum serveur.
     * Repartir d'une seule page le renverrait en haut de sa liste à chaque
     * événement du flux ; au-delà de 200 chargées, la queue est refaite au
     * défilement, ce que `hasMore` signale.
     */
    suspend fun refresh() {
        if (sessionStore.accessToken() == null) return
        val loaded = (_state.value as? TasksState.Ready)?.tasks?.size ?: 0
        val want = loaded.coerceAtLeast(PAGE).coerceAtMost(MAX_PAGE)
        _state.value = runCatching { load(_view.value, limit = want, offset = 0) }
            .fold(
                { TasksState.Ready(it, hasMore = paginable(_view.value) && it.size >= want) },
                { TasksState.Failed(it) },
            )
    }

    /**
     * Page suivante, ajoutée à la liste courante. Sans effet si une page est
     * déjà en vol : un défilement rapide déclencherait sinon plusieurs fois la
     * même requête et dupliquerait des lignes.
     */
    fun loadMore() {
        val current = _state.value as? TasksState.Ready ?: return
        if (!current.hasMore || current.loadingMore) return
        _state.value = current.copy(loadingMore = true)
        scope.launch {
            val offset = current.tasks.size
            runCatching { load(_view.value, limit = PAGE, offset = offset) }.fold(
                { page ->
                    _state.value = TasksState.Ready(
                        tasks = current.tasks + page,
                        hasMore = page.size >= PAGE,
                    )
                },
                {
                    // On garde ce qui est affiché : perdre la liste entière
                    // parce qu'une page a échoué serait plus punitif que le
                    // défaut lui-même.
                    _state.value = current.copy(loadingMore = false)
                },
            )
        }
    }

    /**
     * Une vue enregistrée n'est pas paginable : `GET /filters/{id}/tasks`
     * exécute la requête et rend tout. Prétendre le contraire ferait défiler
     * l'écran vers une page qui n'existe pas.
     */
    private fun paginable(view: TaskView): Boolean = view !is TaskView.Filter

    private suspend fun load(view: TaskView, limit: Int, offset: Int): List<Task> = when (view) {
        // `root = true` n'est demandé que sur les vues d'ensemble : dans un
        // projet ou un filtre, une sous-tâche dont le parent vit ailleurs doit
        // rester visible, sinon elle disparaît de la seule vue qui la contient.
        TaskView.Today -> api.listTasks(
            completed = false, root = true, dueBefore = tomorrow(), limit = limit, offset = offset,
        )

        TaskView.All -> api.listTasks(completed = false, root = true, limit = limit, offset = offset)
        TaskView.Inbox -> inboxProjectId()
            ?.let { api.listTasks(completed = false, projectId = it, limit = limit, offset = offset) }
            .orEmpty()

        is TaskView.Project ->
            api.listTasks(completed = false, projectId = view.id, limit = limit, offset = offset)

        is TaskView.Label ->
            api.listTasks(completed = false, labelId = view.id, limit = limit, offset = offset)

        // Pas de pagination ici : le serveur exécute le filtre et rend tout.
        is TaskView.Filter -> api.runFilter(view.id)
    }

    /**
     * « Aujourd'hui » = dû AVANT demain, ce qui inclut les retards — la même
     * borne que le client web. Minuit local, pas UTC : la journée de
     * l'utilisateur est celle de son fuseau.
     */
    private fun tomorrow(): String =
        LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toString()

    /**
     * La boîte de réception est un PROJET marqué `isInbox`, pas une vue à part.
     * On lit d'abord l'état du tiroir, déjà chargé dans l'écrasante majorité des
     * cas ; le repli n'existe que pour la sélection faite avant sa première
     * réponse.
     */
    private suspend fun inboxProjectId(): String? =
        (navigation.state.value as? NavigationState.Ready)
            ?.projects
            ?.firstOrNull { it.isInbox }
            ?.id
            ?: runCatching { api.listProjects().firstOrNull { it.isInbox }?.id }.getOrNull()

    suspend fun quickAdd(text: String): Result<Task> = runCatching {
        // Minutes east of UTC, so the server resolves "tomorrow" against the
        // user's day rather than its own.
        val tz = TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60_000
        api.quickAdd(TaskQuickAddRequest(text = text, tz = tz))
    }.onSuccess { refresh() }

    suspend fun setDone(id: String, done: Boolean): Result<Unit> {
        val previous = _state.value

        // Optimistic: the checkbox must not wait on a round trip.
        (previous as? TasksState.Ready)?.let { ready ->
            _state.value = TasksState.Ready(ready.tasks.filterNot { done && it.id == id })
        }

        return runCatching {
            api.updateTask(
                id,
                TaskUpdateRequest(
                    status = if (done) {
                        TaskUpdateRequest.Status.done
                    } else {
                        TaskUpdateRequest.Status.todo
                    },
                ),
            )
            Unit
        }.onFailure {
            // Roll back by re-reading rather than by restoring the old list:
            // the failure may itself be a sign the client is out of date.
            refresh()
        }
    }

    private companion object {
        const val DEBOUNCE_MS = 300L

        /** Taille d'une page — le défaut du serveur. */
        const val PAGE = 50

        /** Plafond imposé par l'API sur une seule requête. */
        const val MAX_PAGE = 200
    }
}
