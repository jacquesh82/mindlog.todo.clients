package today.mindlog.todo.core.network.api

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import today.mindlog.todo.core.network.model.AiLog
import today.mindlog.todo.core.network.model.AiModelList
import today.mindlog.todo.core.network.model.AiModelsQueryRequest
import today.mindlog.todo.core.network.model.AiSettings
import today.mindlog.todo.core.network.model.AiSettingsUpdateRequest
import today.mindlog.todo.core.network.model.AiUsage
import today.mindlog.todo.core.network.model.PromptSaveRequest
import today.mindlog.todo.core.network.model.PromptView

/**
 * Réglages, consommation et gabarits de prompts.
 *
 * La clé d'API est en ÉCRITURE SEULE : elle part dans [AiSettingsUpdateRequest]
 * et ne revient jamais. `AiSettings.hasKey` dit seulement si une clé existe —
 * chercher à la relire pour pré-remplir un champ est vain par construction.
 */
interface AiApi {

    // --- gabarits de prompts ---

    @GET("api/v1/ai/prompts")
    suspend fun listPrompts(): List<PromptView>

    @PUT("api/v1/ai/prompts/{key}")
    suspend fun savePrompt(@Path("key") key: String, @Body body: PromptSaveRequest): PromptView

    /** Retire la surcharge : la valeur du fichier de référence reprend. */
    @DELETE("api/v1/ai/prompts/{key}")
    suspend fun resetPrompt(@Path("key") key: String): PromptView

    @POST("api/v1/ai/prompts/reset")
    suspend fun resetAllPrompts(): List<PromptView>

    // --- consommation ---

    @GET("api/v1/ai/usage")
    suspend fun usage(): AiUsage

    @GET("api/v1/ai/logs")
    suspend fun logs(@Query("limit") limit: Int = 50): List<AiLog>

    // --- réglages ---

    @GET("api/v1/ai/settings")
    suspend fun settings(): AiSettings

    @PATCH("api/v1/ai/settings")
    suspend fun updateSettings(@Body body: AiSettingsUpdateRequest): AiSettings

    @DELETE("api/v1/ai/settings/key")
    suspend fun deleteKey(): AiSettings

    /**
     * POST et non GET : la requête peut porter une clé d'API, qui n'a rien à
     * faire dans une URL — journaux serveur, historique, référents.
     */
    @POST("api/v1/ai/models")
    suspend fun models(@Body body: AiModelsQueryRequest): AiModelList
}
