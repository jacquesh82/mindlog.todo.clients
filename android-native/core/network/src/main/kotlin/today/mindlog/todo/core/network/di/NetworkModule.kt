package today.mindlog.todo.core.network.di

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import today.mindlog.todo.core.network.AuthInterceptor
import today.mindlog.todo.core.network.BaseUrlInterceptor
import today.mindlog.todo.core.network.TokenAuthenticator
import today.mindlog.todo.core.network.api.AuthApi
import today.mindlog.todo.core.network.api.AccountApi
import today.mindlog.todo.core.network.api.AiApi
import today.mindlog.todo.core.network.api.CalendarApi
import today.mindlog.todo.core.network.api.NotesApi
import today.mindlog.todo.core.network.api.TodoApi
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

/** The bare client: no token attached, no renewal. Serves [AuthApi]. */
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class AuthClient

/** The full client: token attached, renewed and replayed on 401. */
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class ApiClient

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun json(): Json = Json {
        // The server adds fields without asking; a client that refuses to parse
        // an unfamiliar one would break on every deployment.
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    @Provides
    @Singleton
    @AuthClient
    fun authOkHttp(baseUrl: BaseUrlInterceptor): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(baseUrl)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

    @Provides
    @Singleton
    @ApiClient
    fun apiOkHttp(
        baseUrl: BaseUrlInterceptor,
        auth: AuthInterceptor,
        authenticator: TokenAuthenticator,
    ): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(baseUrl)
            .addInterceptor(auth)
            .authenticator(authenticator)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

    private fun retrofit(client: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            // Replaced per request by BaseUrlInterceptor; the real base lives in
            // ServerStore because the user can change it.
            .baseUrl(BaseUrlInterceptor.PLACEHOLDER_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides @Singleton @AuthClient
    fun authRetrofit(@AuthClient client: OkHttpClient, json: Json): Retrofit = retrofit(client, json)

    @Provides @Singleton @ApiClient
    fun apiRetrofit(@ApiClient client: OkHttpClient, json: Json): Retrofit = retrofit(client, json)

    @Provides @Singleton
    fun authApi(@AuthClient retrofit: Retrofit): AuthApi = retrofit.create(AuthApi::class.java)

    @Provides @Singleton
    fun todoApi(@ApiClient retrofit: Retrofit): TodoApi = retrofit.create(TodoApi::class.java)

    // Même client que TodoApi : mêmes en-têtes, même renouvellement de jeton.
    // Seule l'interface est découpée, pas la pile réseau.
    @Provides
    @Singleton
    fun notesApi(@ApiClient retrofit: Retrofit): NotesApi = retrofit.create(NotesApi::class.java)

    @Provides
    @Singleton
    fun aiApi(@ApiClient retrofit: Retrofit): AiApi = retrofit.create(AiApi::class.java)

    @Provides
    @Singleton
    fun calendarApi(@ApiClient retrofit: Retrofit): CalendarApi =
        retrofit.create(CalendarApi::class.java)

    @Provides
    @Singleton
    fun accountApi(@ApiClient retrofit: Retrofit): AccountApi = retrofit.create(AccountApi::class.java)
}
