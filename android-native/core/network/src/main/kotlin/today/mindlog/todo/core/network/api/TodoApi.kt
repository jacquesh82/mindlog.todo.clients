package today.mindlog.todo.core.network.api

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
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
import today.mindlog.todo.core.network.model.AskResult
import today.mindlog.todo.core.network.model.Task
import today.mindlog.todo.core.network.model.TaskAskRequest
import today.mindlog.todo.core.network.model.TaskSearchHit
import today.mindlog.todo.core.network.model.TaskSearchRequest
import today.mindlog.todo.core.network.model.TaskCreateRequest
import today.mindlog.todo.core.network.model.TaskQuickAddRequest
import today.mindlog.todo.core.network.model.TaskUpdateRequest
import today.mindlog.todo.core.network.model.User

/**
 * The authenticated surface. Hand-written on purpose — it is thirty lines,
 * while generating it would drag in openapi-generator's own ApiClient and auth
 * handling, neither of which fits the client this module builds.
 *
 * Paths are relative; see the note on [AuthApi].
 */
interface TodoApi {

    @GET("api/v1/me")
    suspend fun me(): User

    // --- tasks ---

    /**
     * `limit` defaults to 50 server-side and caps at 200. Milestone 1 asks for
     * the maximum and does not paginate; the day a user has more than two
     * hundred open tasks, that becomes a real gap rather than a hidden one.
     */
    @GET("api/v1/tasks")
    suspend fun listTasks(
        @Query("completed") completed: Boolean? = null,
        @Query("root") root: Boolean? = null,
        @Query("projectId") projectId: String? = null,
        @Query("labelId") labelId: String? = null,
        /** ISO instant. `Today` is expressed as "due before tomorrow", so it includes overdue. */
        @Query("dueBefore") dueBefore: String? = null,
        @Query("limit") limit: Int = 200,
    ): List<Task>

    @POST("api/v1/tasks")
    suspend fun createTask(@Body body: TaskCreateRequest): Task

    /**
     * One line of natural language, parsed server-side: dates, `#project`,
     * `@label`, `p1`–`p4`. Using this instead of a form is what keeps the
     * parsing rules in exactly one place.
     */
    @POST("api/v1/tasks/quickadd")
    suspend fun quickAdd(@Body body: TaskQuickAddRequest): Task

    @PATCH("api/v1/tasks/{id}")
    suspend fun updateTask(@Path("id") id: String, @Body body: TaskUpdateRequest): Task

    @DELETE("api/v1/tasks/{id}")
    suspend fun deleteTask(@Path("id") id: String)

    /** Recherche sémantique sur les tâches ; le score est porté par le hit. */
    @POST("api/v1/tasks/search")
    suspend fun searchTasks(@Body body: TaskSearchRequest): List<TaskSearchHit>

    /**
     * Question en langue naturelle. La réponse cite les tâches qui l'ont
     * fondée : elles sont rendues telles quelles, pas résumées, pour que
     * l'utilisateur puisse vérifier ce sur quoi le modèle s'appuie.
     */
    @POST("api/v1/tasks/ask")
    suspend fun ask(@Body body: TaskAskRequest): AskResult

    // --- projects ---

    @GET("api/v1/projects")
    suspend fun listProjects(
        @Query("includeArchived") includeArchived: Boolean? = null,
    ): List<Project>

    @GET("api/v1/projects/{id}")
    suspend fun getProject(@Path("id") id: String): Project

    @POST("api/v1/projects")
    suspend fun createProject(@Body body: ProjectCreateRequest): Project

    @PATCH("api/v1/projects/{id}")
    suspend fun updateProject(@Path("id") id: String, @Body body: ProjectUpdateRequest): Project

    @DELETE("api/v1/projects/{id}")
    suspend fun deleteProject(@Path("id") id: String)

    // --- sections ---

    /**
     * `projectId` is required by the server: a section only exists inside a
     * project, and listing them all at once is not offered.
     */
    @GET("api/v1/sections")
    suspend fun listSections(@Query("projectId") projectId: String): List<Section>

    @POST("api/v1/sections")
    suspend fun createSection(@Body body: SectionCreateRequest): Section

    @PATCH("api/v1/sections/{id}")
    suspend fun updateSection(@Path("id") id: String, @Body body: SectionUpdateRequest): Section

    @DELETE("api/v1/sections/{id}")
    suspend fun deleteSection(@Path("id") id: String)

    // --- labels ---

    @GET("api/v1/labels")
    suspend fun listLabels(): List<Label>

    @POST("api/v1/labels")
    suspend fun createLabel(@Body body: LabelCreateRequest): Label

    @PATCH("api/v1/labels/{id}")
    suspend fun updateLabel(@Path("id") id: String, @Body body: LabelUpdateRequest): Label

    @DELETE("api/v1/labels/{id}")
    suspend fun deleteLabel(@Path("id") id: String)

    // --- filters (saved views) ---

    @GET("api/v1/filters")
    suspend fun listFilters(): List<Filter>

    @POST("api/v1/filters")
    suspend fun createFilter(@Body body: FilterCreateRequest): Filter

    @PATCH("api/v1/filters/{id}")
    suspend fun updateFilter(@Path("id") id: String, @Body body: FilterUpdateRequest): Filter

    @DELETE("api/v1/filters/{id}")
    suspend fun deleteFilter(@Path("id") id: String)

    /**
     * Runs the saved query server-side. The client never interprets the filter
     * syntax itself — that grammar lives in one place, on the server.
     */
    @GET("api/v1/filters/{id}/tasks")
    suspend fun runFilter(@Path("id") id: String): List<Task>
}
