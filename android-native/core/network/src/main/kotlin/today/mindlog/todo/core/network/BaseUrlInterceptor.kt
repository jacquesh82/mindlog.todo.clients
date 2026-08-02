package today.mindlog.todo.core.network

import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import today.mindlog.todo.core.datastore.ServerStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Rewrites each request onto the server currently configured.
 *
 * Retrofit is built against [PLACEHOLDER_BASE_URL] and the service interfaces
 * declare **relative** paths, so the real base — which the user can change at
 * runtime — is applied here.
 *
 * The archived talk client did this by swapping scheme, host and port. That is
 * not enough here: production and qualif live under a `/app` sub-path, and
 * replacing only the authority silently drops it. This resolves the request
 * path *against* the configured base instead, so the prefix survives.
 *
 * `runBlocking` is safe in an interceptor — OkHttp never calls one on the main
 * thread — and DataStore serves the value from memory after the first read.
 */
@Singleton
class BaseUrlInterceptor @Inject constructor(
    private val serverStore: ServerStore,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val base = runBlocking { serverStore.current() }.toHttpUrl()
        val original = chain.request().url

        val rebuilt = base.newBuilder()
            .addPathSegments(original.encodedPath.trimStart('/'))
            .encodedQuery(original.encodedQuery)
            .build()

        return chain.proceed(chain.request().newBuilder().url(rebuilt).build())
    }

    companion object {
        /**
         * Never contacted. Retrofit demands a base URL at build time, and every
         * request is re-based above.
         */
        const val PLACEHOLDER_BASE_URL = "http://mindlog.invalid/"
    }
}
