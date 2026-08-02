package today.mindlog.todo.core.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import today.mindlog.todo.core.data.di.ApplicationScope
import today.mindlog.todo.core.datastore.SessionStore
import today.mindlog.todo.core.network.ChangeEventStream
import today.mindlog.todo.core.network.api.TodoApi
import today.mindlog.todo.core.network.model.ChangeEvent
import today.mindlog.todo.core.network.model.Filter
import today.mindlog.todo.core.network.model.FilterCreateRequest
import today.mindlog.todo.core.network.model.FilterUpdateRequest
import today.mindlog.todo.core.network.model.Label
import today.mindlog.todo.core.network.model.LabelCreateRequest
import today.mindlog.todo.core.network.model.LabelUpdateRequest
import today.mindlog.todo.core.network.model.Project
import today.mindlog.todo.core.network.model.ProjectCreateRequest
import today.mindlog.todo.core.network.model.ProjectUpdateRequest
import today.mindlog.todo.core.network.model.Section
import today.mindlog.todo.core.network.model.SectionCreateRequest
import today.mindlog.todo.core.network.model.SectionUpdateRequest
import today.mindlog.todo.core.network.model.Task
import javax.inject.Inject
import javax.inject.Singleton

sealed interface NavigationState {
    data object Loading : NavigationState
    data class Ready(
        val projects: List<Project>,
        val labels: List<Label>,
        val filters: List<Filter>,
    ) : NavigationState

    data class Failed(val cause: Throwable) : NavigationState
}

/**
 * Everything the navigation drawer shows: projects, labels and saved filters.
 *
 * One repository rather than three, because these three lists are never read
 * apart — the drawer shows them together and any of their change events makes
 * the whole drawer stale. Splitting them would triple the stream subscription,
 * the state machine and the reload path to serve a single screen; the web
 * client reaches the same conclusion in `reloadSidebar`.
 *
 * Sections are deliberately NOT part of this state. They belong to one project,
 * are only read when that project is open, and loading every project's sections
 * to draw a drawer that never displays them would be a request per project on
 * every reload.
 *
 * No local cache, for the reason spelled out on [TaskRepository]: the change
 * stream carries invalidation without payload, so every event forces a full
 * re-read regardless.
 */
@Singleton
class NavigationRepository @Inject constructor(
    private val api: TodoApi,
    private val events: ChangeEventStream,
    private val sessionStore: SessionStore,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<NavigationState>(NavigationState.Loading)
    val state: StateFlow<NavigationState> = _state.asStateFlow()

    /**
     * Same signed-in guard as [TaskRepository.startWatching]: subscribing before
     * a session exists races the token refresh and can sign the user out.
     */
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    fun startWatching() {
        scope.launch {
            sessionStore.signedIn
                .flatMapLatest { signedIn ->
                    if (signedIn == true) {
                        events.changes().filter { it.entity in DRAWER_ENTITIES }
                    } else {
                        emptyFlow()
                    }
                }
                .debounce(DEBOUNCE_MS)
                .catch { /* the stream reconnects on its own */ }
                .collect { refresh() }
        }
    }

    /**
     * The three lists are fetched CONCURRENTLY. Sequentially, the drawer would
     * wait for the sum of three round trips on a mobile link; here it waits for
     * the slowest. One failure fails the whole state: a drawer missing its
     * filters without saying so is worse than a drawer that reports an error.
     */
    suspend fun refresh() {
        if (sessionStore.accessToken() == null) return
        _state.value = runCatching {
            coroutineScope {
                val projects = async { api.listProjects() }
                val labels = async { api.listLabels() }
                val filters = async { api.listFilters() }
                NavigationState.Ready(projects.await(), labels.await(), filters.await())
            }
        }.fold({ it }, { NavigationState.Failed(it) })
    }

    // --- projects ---------------------------------------------------------
    // Mutations do not touch `_state` optimistically: unlike ticking a task
    // off, none of them is on a latency-critical path, and the server decides
    // ordering (`position`) and inbox rules. A reload after the call keeps one
    // source of truth.

    suspend fun createProject(
        name: String,
        color: String? = null,
        parentId: String? = null,
    ): Result<Project> = runCatching {
        api.createProject(ProjectCreateRequest(name = name, color = color, parentId = parentId))
    }.onSuccess { refresh() }

    suspend fun renameProject(id: String, name: String): Result<Project> =
        runCatching { api.updateProject(id, ProjectUpdateRequest(name = name)) }.onSuccess { refresh() }

    suspend fun setProjectFavorite(id: String, favorite: Boolean): Result<Project> =
        runCatching { api.updateProject(id, ProjectUpdateRequest(isFavorite = favorite)) }
            .onSuccess { refresh() }

    /** Archiving hides a project without losing its tasks — the reversible half of delete. */
    suspend fun setProjectArchived(id: String, archived: Boolean): Result<Project> =
        runCatching { api.updateProject(id, ProjectUpdateRequest(archived = archived)) }
            .onSuccess { refresh() }

    suspend fun deleteProject(id: String): Result<Unit> =
        runCatching { api.deleteProject(id) }.onSuccess { refresh() }

    // --- sections ---------------------------------------------------------
    // Read on demand, not cached: see the class note.

    suspend fun sections(projectId: String): Result<List<Section>> =
        runCatching { api.listSections(projectId) }

    suspend fun createSection(projectId: String, name: String): Result<Section> =
        runCatching { api.createSection(SectionCreateRequest(name = name, projectId = projectId)) }

    suspend fun renameSection(id: String, name: String): Result<Section> =
        runCatching { api.updateSection(id, SectionUpdateRequest(name = name)) }

    suspend fun deleteSection(id: String): Result<Unit> =
        runCatching { api.deleteSection(id) }

    // --- labels -----------------------------------------------------------

    suspend fun createLabel(name: String, color: String? = null): Result<Label> =
        runCatching { api.createLabel(LabelCreateRequest(name = name, color = color)) }
            .onSuccess { refresh() }

    suspend fun renameLabel(id: String, name: String): Result<Label> =
        runCatching { api.updateLabel(id, LabelUpdateRequest(name = name)) }.onSuccess { refresh() }

    suspend fun deleteLabel(id: String): Result<Unit> =
        runCatching { api.deleteLabel(id) }.onSuccess { refresh() }

    // --- filters (saved views) --------------------------------------------

    suspend fun createFilter(name: String, query: String, color: String? = null): Result<Filter> =
        runCatching { api.createFilter(FilterCreateRequest(name = name, query = query, color = color)) }
            .onSuccess { refresh() }

    suspend fun updateFilter(id: String, name: String? = null, query: String? = null): Result<Filter> =
        runCatching { api.updateFilter(id, FilterUpdateRequest(name = name, query = query)) }
            .onSuccess { refresh() }

    suspend fun deleteFilter(id: String): Result<Unit> =
        runCatching { api.deleteFilter(id) }.onSuccess { refresh() }

    /** Runs the saved query server-side; the filter grammar is never parsed here. */
    suspend fun runFilter(id: String): Result<List<Task>> = runCatching { api.runFilter(id) }

    private companion object {
        val DRAWER_ENTITIES = setOf(
            ChangeEvent.Entity.project,
            ChangeEvent.Entity.section,
            ChangeEvent.Entity.label,
            ChangeEvent.Entity.filter,
        )

        /** Same 300 ms window as [TaskRepository]: one burst, one reload. */
        const val DEBOUNCE_MS = 300L
    }
}
