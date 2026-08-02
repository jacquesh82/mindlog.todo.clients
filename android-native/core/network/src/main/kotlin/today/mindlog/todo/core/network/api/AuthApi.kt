package today.mindlog.todo.core.network.api

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import today.mindlog.todo.core.network.model.AuthResult
import today.mindlog.todo.core.network.model.CompleteMindlogIdRequest
import today.mindlog.todo.core.network.model.LoginRequest
import today.mindlog.todo.core.network.model.RefreshRequest
import today.mindlog.todo.core.network.model.RegisterRequest
import today.mindlog.todo.core.network.model.VersionInfo

/**
 * Endpoints that do not need — and must not wait on — an access token.
 *
 * Served by the bare OkHttp client, so a failing refresh cannot recurse into
 * itself through [today.mindlog.todo.core.network.TokenAuthenticator].
 *
 * Note every path is **relative**: a leading slash would be an absolute path
 * and would erase the `/app` prefix that production and qualif are served
 * under. `ApiUrlTest` pins this.
 */
interface AuthApi {

    @POST("api/v1/auth/login")
    suspend fun login(@Body body: LoginRequest): AuthResult

    @POST("api/v1/auth/register")
    suspend fun register(@Body body: RegisterRequest): AuthResult

    @POST("api/v1/auth/refresh")
    suspend fun refresh(@Body body: RefreshRequest): AuthResult

    @POST("api/v1/auth/logout")
    suspend fun logout(@Body body: RefreshRequest)

    /** Finishes a mindlog id sign-in whose account carried no email address. */
    @POST("api/v1/auth/mindlog-id/complete")
    suspend fun completeMindlogId(@Body body: CompleteMindlogIdRequest): AuthResult

    /**
     * Public. `authProviders` is what tells the sign-in screen whether to offer
     * the mindlog id button at all — offering one this deployment has not
     * configured just produces a 503 under the user's finger.
     */
    @GET("api/v1/version")
    suspend fun version(): VersionInfo
}
