package today.mindlog.todo.core.network.api

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import today.mindlog.todo.core.network.model.DrawCleanupRequest
import today.mindlog.todo.core.network.model.DrawCleanupResult
import today.mindlog.todo.core.network.model.ExtractedTasks
import today.mindlog.todo.core.network.model.NotePage
import today.mindlog.todo.core.network.model.NotePageHit
import today.mindlog.todo.core.network.model.NotePageSummary
import today.mindlog.todo.core.network.model.Notebook
import today.mindlog.todo.core.network.model.NotebookCreateRequest
import today.mindlog.todo.core.network.model.NotebookRagRequest
import today.mindlog.todo.core.network.model.NotebookRagResult
import today.mindlog.todo.core.network.model.NotebookUpdateRequest
import today.mindlog.todo.core.network.model.NoteSearchRequest
import today.mindlog.todo.core.network.model.PageCreateRequest
import today.mindlog.todo.core.network.model.PageUpdateRequest

/**
 * Notes : carnets et pages.
 *
 * Séparée de [TodoApi] parce que le domaine a sa propre forme — deux niveaux
 * d'objets, des routes assistées par modèle — et que tout empiler dans une
 * interface de cinquante méthodes rendrait les deux illisibles.
 *
 * Les listes renvoient des [NotePageSummary], sans le contenu : une page peut
 * peser plusieurs mégaoctets (images collées en data URL), et les empiler dans
 * une liste rendrait l'écran inutilisable. Le contenu ne vient qu'à l'ouverture.
 */
interface NotesApi {

    // --- carnets ---

    @GET("api/v1/notes/notebooks")
    suspend fun listNotebooks(): List<Notebook>

    @POST("api/v1/notes/notebooks")
    suspend fun createNotebook(@Body body: NotebookCreateRequest): Notebook

    @PATCH("api/v1/notes/notebooks/{id}")
    suspend fun updateNotebook(@Path("id") id: String, @Body body: NotebookUpdateRequest): Notebook

    @DELETE("api/v1/notes/notebooks/{id}")
    suspend fun deleteNotebook(@Path("id") id: String)

    // --- pages ---

    @GET("api/v1/notes/notebooks/{id}/pages")
    suspend fun listPages(@Path("id") notebookId: String): List<NotePageSummary>

    @POST("api/v1/notes/notebooks/{id}/pages")
    suspend fun createPage(
        @Path("id") notebookId: String,
        @Body body: PageCreateRequest,
    ): NotePage

    /** La seule route qui rende le contenu d'une page. */
    @GET("api/v1/notes/pages/{id}")
    suspend fun getPage(@Path("id") id: String): NotePage

    @PATCH("api/v1/notes/pages/{id}")
    suspend fun updatePage(@Path("id") id: String, @Body body: PageUpdateRequest): NotePage

    @DELETE("api/v1/notes/pages/{id}")
    suspend fun deletePage(@Path("id") id: String)

    @POST("api/v1/notes/pages/{id}/duplicate")
    suspend fun duplicatePage(@Path("id") id: String): NotePage

    // --- index sémantique et recherche ---

    /** Bascule TOUTES les pages d'un carnet dans (ou hors de) l'index. */
    @POST("api/v1/notes/notebooks/{id}/rag")
    suspend fun setNotebookRag(
        @Path("id") id: String,
        @Body body: NotebookRagRequest,
    ): NotebookRagResult

    @POST("api/v1/notes/search")
    suspend fun searchPages(@Body body: NoteSearchRequest): List<NotePageHit>

    // --- routes assistées par un modèle ---
    // Latence et coût sans commune mesure avec une lecture : à traiter comme des
    // actions déclenchées par l'utilisateur, jamais comme du chargement d'écran.

    @POST("api/v1/notes/notebooks/{id}/summarize")
    suspend fun summarizeNotebook(@Path("id") id: String): NotePage

    /** Propose des tâches ; n'en crée aucune — c'est un aperçu. */
    @POST("api/v1/notes/pages/{id}/extract-tasks")
    suspend fun extractTasks(@Path("id") id: String): ExtractedTasks

    @POST("api/v1/notes/draw/cleanup")
    suspend fun cleanupDrawing(@Body body: DrawCleanupRequest): DrawCleanupResult
}
