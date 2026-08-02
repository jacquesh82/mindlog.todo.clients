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
    data class Ready(val tasks: List<Task>) : TasksState
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

    suspend fun refresh() {
        if (sessionStore.accessToken() == null) return
        _state.value = runCatching { load(_view.value) }
            .fold({ TasksState.Ready(it) }, { TasksState.Failed(it) })
    }

    private suspend fun load(view: TaskView): List<Task> = when (view) {
        // `root = true` n'est demandé que sur les vues d'ensemble : dans un
        // projet ou un filtre, une sous-tâche dont le parent vit ailleurs doit
        // rester visible, sinon elle disparaît de la seule vue qui la contient.
        TaskView.Today -> api.listTasks(completed = false, root = true, dueBefore = tomorrow())
        TaskView.All -> api.listTasks(completed = false, root = true)
        TaskView.Inbox -> inboxProjectId()
            ?.let { api.listTasks(completed = false, projectId = it) }
            .orEmpty()

        is TaskView.Project -> api.listTasks(completed = false, projectId = view.id)
        is TaskView.Label -> api.listTasks(completed = false, labelId = view.id)
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
    }
}
