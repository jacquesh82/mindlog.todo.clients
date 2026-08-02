package today.mindlog.todo.core.network

import okhttp3.Interceptor
import okhttp3.Response
import today.mindlog.todo.core.datastore.SessionStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Attaches the current access token, when there is one.
 *
 * Only attaches. Renewal is [TokenAuthenticator]'s job — OkHttp calls that one
 * on a 401, which is the moment renewal is actually warranted, rather than on
 * a clock this class would have to guess at.
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val sessionStore: SessionStore,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = sessionStore.accessToken() ?: return chain.proceed(chain.request())
        val request = chain.request().newBuilder()
            .header("Authorization", "Bearer $token")
            .build()
        return chain.proceed(request)
    }
}
