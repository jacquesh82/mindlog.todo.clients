package today.mindlog.todo.core.network.api

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import today.mindlog.todo.core.network.model.CalendarEvent
import today.mindlog.todo.core.network.model.CalendarSource
import today.mindlog.todo.core.network.model.CalendarSourceCreateRequest
import today.mindlog.todo.core.network.model.CalendarSourceUpdateRequest
import today.mindlog.todo.core.network.model.DashboardStats
import today.mindlog.todo.core.network.model.Karma
import today.mindlog.todo.core.network.model.MindlogIdConnection

/**
 * Agendas et statistiques.
 *
 * L'agenda a deux origines — les abonnements iCal de l'utilisateur et le compte
 * mindlog id quand il a accordé le droit correspondant — mais [events] les rend
 * FUSIONNÉS : le client n'a pas à savoir d'où vient chaque entrée, ni à les
 * recoller lui-même.
 */
interface CalendarApi {

    @GET("api/v1/calendar/mindlog-id")
    suspend fun mindlogIdConnection(): MindlogIdConnection

    @DELETE("api/v1/calendar/mindlog-id")
    suspend fun disconnectMindlogId()

    @GET("api/v1/calendar/sources")
    suspend fun listSources(): List<CalendarSource>

    @POST("api/v1/calendar/sources")
    suspend fun createSource(@Body body: CalendarSourceCreateRequest): CalendarSource

    @PATCH("api/v1/calendar/sources/{id}")
    suspend fun updateSource(
        @Path("id") id: String,
        @Body body: CalendarSourceUpdateRequest,
    ): CalendarSource

    @DELETE("api/v1/calendar/sources/{id}")
    suspend fun deleteSource(@Path("id") id: String)

    /** Bornes ISO facultatives ; sans elles, le serveur choisit sa fenêtre. */
    @GET("api/v1/calendar/events")
    suspend fun events(
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
    ): List<CalendarEvent>

    // --- statistiques ---
    // Deux lectures agrégées, calculées côté serveur. Les recomposer depuis les
    // listes locales donnerait des chiffres différents de ceux du web dès que la
    // pagination entre en jeu.

    @GET("api/v1/karma")
    suspend fun karma(): Karma

    @GET("api/v1/dashboard")
    suspend fun dashboard(): DashboardStats
}
