package today.mindlog.todo.core.network

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Guards the `/app` sub-path.
 *
 * Production and qualif are served under `https://todo…/app/`, while local is
 * served from the root. A base URL without a trailing slash, or a service path
 * with a leading one, silently drops the prefix — and the failure only appears
 * on promotion, because local has no prefix to lose.
 *
 * These assert the exact rewrite [BaseUrlInterceptor] performs.
 */
class ApiUrlTest {

    private fun rebase(base: String, path: String, query: String? = null): String {
        val original = "${BaseUrlInterceptor.PLACEHOLDER_BASE_URL}$path".toHttpUrl()
        return base.toHttpUrl().newBuilder()
            .addPathSegments(original.encodedPath.trimStart('/'))
            .encodedQuery(query)
            .build()
            .toString()
    }

    @Test
    fun `keeps the app sub-path in production`() {
        assertEquals(
            "https://todo.mindlog.today/app/api/v1/tasks",
            rebase("https://todo.mindlog.today/app/", "api/v1/tasks"),
        )
    }

    @Test
    fun `keeps the app sub-path in qualif`() {
        assertEquals(
            "https://todo.gra01.mindlog.today/app/api/v1/auth/login",
            rebase("https://todo.gra01.mindlog.today/app/", "api/v1/auth/login"),
        )
    }

    @Test
    fun `works against a root-served local server`() {
        assertEquals(
            "http://10.0.2.2:8080/api/v1/tasks",
            rebase("http://10.0.2.2:8080/", "api/v1/tasks"),
        )
    }

    @Test
    fun `carries the query string across`() {
        assertEquals(
            "https://todo.mindlog.today/app/api/v1/tasks?completed=false&limit=200",
            rebase("https://todo.mindlog.today/app/", "api/v1/tasks", "completed=false&limit=200"),
        )
    }

    @Test
    fun `the environment defaults all end in a slash`() {
        // The whole trap in one assertion: OkHttp resolves a relative path
        // against the *last* segment, so a base of `…/app` (no slash) would
        // resolve to `…/api/v1/tasks` and lose `/app` entirely.
        listOf(
            "https://todo.mindlog.today/app/",
            "https://todo.gra01.mindlog.today/app/",
            "http://10.0.2.2:8080/",
        ).forEach { assertEquals(true, it.endsWith("/")) }

        assertEquals(
            "https://todo.mindlog.today/api/v1/tasks",
            "https://todo.mindlog.today/app".toHttpUrl().resolve("/api/v1/tasks").toString(),
        )
    }
}
