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
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<TasksState>(TasksState.Loading)
    val state: StateFlow<TasksState> = _state.asStateFlow()

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

    suspend fun refresh() {
        if (sessionStore.accessToken() == null) return
        _state.value = runCatching { api.listTasks(completed = false, root = true) }
            .fold({ TasksState.Ready(it) }, { TasksState.Failed(it) })
    }

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
