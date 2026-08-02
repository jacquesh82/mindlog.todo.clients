package today.mindlog.todo.core.network.api

import kotlinx.serialization.json.JsonObject
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import today.mindlog.todo.core.network.model.ApiKey
import today.mindlog.todo.core.network.model.ApiKeyCreateRequest
import today.mindlog.todo.core.network.model.ApiKeyCreated
import today.mindlog.todo.core.network.model.Attachment
import today.mindlog.todo.core.network.model.AttachmentCreateRequest
import today.mindlog.todo.core.network.model.ProfileUpdateRequest
import today.mindlog.todo.core.network.model.StorageUsage
import today.mindlog.todo.core.network.model.User

/** Profil, clés d'API, occupation disque, export, pièces jointes. */
interface AccountApi {

    @PATCH("api/v1/me")
    suspend fun updateProfile(@Body body: ProfileUpdateRequest): User

    // --- clés d'API ---

    @GET("api/v1/api-keys")
    suspend fun listApiKeys(): List<ApiKey>

    /**
     * Le secret n'est renvoyé QU'ICI, à la création — [ApiKey] ne porte qu'un
     * préfixe. Un écran qui ne le montre pas à cet instant le perd pour de bon.
     */
    @POST("api/v1/api-keys")
    suspend fun createApiKey(@Body body: ApiKeyCreateRequest): ApiKeyCreated

    @DELETE("api/v1/api-keys/{id}")
    suspend fun revokeApiKey(@Path("id") id: String)

    // --- stockage et export ---

    @GET("api/v1/storage")
    suspend fun storage(): StorageUsage

    /**
     * Sauvegarde complète du compte. Le corps agrège toutes les collections et
     * n'a volontairement pas de forme figée au contrat : on le rend donc en
     * JSON brut, à charge de l'appelant de l'écrire ou de le partager.
     */
    @GET("api/v1/export")
    suspend fun export(): JsonObject

    // --- pièces jointes ---
    // Ce que le serveur stocke est le TEXTE EXTRAIT, pas le fichier : c'est lui
    // qui nourrit l'embedding de la tâche. L'extraction appartient à l'appelant.

    @GET("api/v1/tasks/{id}/attachments")
    suspend fun taskAttachments(@Path("id") taskId: String): List<Attachment>

    @POST("api/v1/tasks/{id}/attachments")
    suspend fun addAttachment(
        @Path("id") taskId: String,
        @Body body: AttachmentCreateRequest,
    ): Attachment

    /** Seule route qui rende le texte extrait ; les listes l'omettent. */
    @GET("api/v1/attachments/{id}")
    suspend fun attachment(@Path("id") id: String): Attachment

    @DELETE("api/v1/attachments/{id}")
    suspend fun deleteAttachment(@Path("id") id: String)
}
