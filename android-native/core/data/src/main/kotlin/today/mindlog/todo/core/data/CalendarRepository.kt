package today.mindlog.todo.core.data

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import today.mindlog.todo.core.datastore.SessionStore
import today.mindlog.todo.core.network.api.CalendarApi
import today.mindlog.todo.core.network.model.CalendarEvent
import today.mindlog.todo.core.network.model.CalendarSource
import today.mindlog.todo.core.network.model.CalendarSourceCreateRequest
import today.mindlog.todo.core.network.model.CalendarSourceUpdateRequest
import today.mindlog.todo.core.network.model.DashboardStats
import today.mindlog.todo.core.network.model.Karma
import today.mindlog.todo.core.network.model.MindlogIdConnection
import java.net.URI
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

sealed interface CalendarState {
    data object Loading : CalendarState
    data class Ready(
        val sources: List<CalendarSource>,
        val events: List<CalendarEvent>,
        val connection: MindlogIdConnection?,
    ) : CalendarState

    data class Failed(val cause: Throwable) : CalendarState
}

/**
 * Agendas et statistiques.
 *
 * Les événements sont demandés sur une FENÊTRE explicite. Sans bornes, le
 * serveur choisit les siennes et l'écran afficherait une période qu'il ne sait
 * pas nommer ; en les posant ici, ce qui est affiché correspond à ce qui a été
 * demandé.
 */
@Singleton
class CalendarRepository @Inject constructor(
    private val api: CalendarApi,
    private val sessionStore: SessionStore,
) {
    private val _state = MutableStateFlow<CalendarState>(CalendarState.Loading)
    val state: StateFlow<CalendarState> = _state.asStateFlow()

    /** Par défaut : d'aujourd'hui à quatre semaines — l'horizon d'un agenda consulté sur un téléphone. */
    suspend fun refresh(daysAhead: Long = 28) {
        if (sessionStore.accessToken() == null) return
        val today = LocalDate.now()
        val from = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toString()
        val to = today.plusDays(daysAhead).atStartOfDay(ZoneId.systemDefault()).toInstant().toString()

        _state.value = runCatching {
            coroutineScope {
                val sources = async { api.listSources() }
                val events = async { api.events(from = from, to = to) }
                // Le rattachement mindlog id est accessoire : son échec ne doit
                // pas priver l'écran de ses agendas.
                val connection = async { runCatching { api.mindlogIdConnection() }.getOrNull() }
                CalendarState.Ready(sources.await(), events.await(), connection.await())
            }
        }.fold({ it }, { CalendarState.Failed(it) })
    }

    /**
     * `url` est saisie à la main, donc reçue en `String` ; le contrat la déclare
     * en `format: uri` et le DTO généré exige un [java.net.URI]. La conversion
     * est faite ICI, dans le `runCatching` : une adresse mal formée devient un
     * échec de `Result` que l'écran sait afficher, au lieu de partir au serveur
     * pour en revenir en 400.
     */
    suspend fun addSource(name: String, url: String, color: String? = null): Result<CalendarSource> =
        runCatching {
            api.createSource(
                CalendarSourceCreateRequest(name = name, url = URI.create(url.trim()), color = color),
            )
        }.onSuccess { refresh() }

    suspend fun renameSource(id: String, name: String): Result<CalendarSource> =
        runCatching { api.updateSource(id, CalendarSourceUpdateRequest(name = name)) }
            .onSuccess { refresh() }

    suspend fun deleteSource(id: String): Result<Unit> =
        runCatching { api.deleteSource(id) }.onSuccess { refresh() }

    suspend fun disconnectMindlogId(): Result<Unit> =
        runCatching { api.disconnectMindlogId() }.onSuccess { refresh() }

    // --- statistiques -----------------------------------------------------
    // Rendues par `Result` et non gardées en état : ce sont des instantanés
    // qu'un écran demande quand il s'ouvre, pas une source de vérité que le
    // reste de l'app consulterait.

    suspend fun karma(): Result<Karma> = runCatching { api.karma() }

    suspend fun dashboard(): Result<DashboardStats> = runCatching { api.dashboard() }
}
