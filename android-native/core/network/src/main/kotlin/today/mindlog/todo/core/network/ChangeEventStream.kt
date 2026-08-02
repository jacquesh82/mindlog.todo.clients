package today.mindlog.todo.core.network

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import today.mindlog.todo.core.network.di.ApiClient
import today.mindlog.todo.core.network.model.ChangeEvent
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

/**
 * The server's change stream.
 *
 * What arrives is an **invalidation signal, not a delta**: `{entity, action,
 * id?}` says what kind of thing changed, never what it became. There is no
 * `changedSince` cursor and no tombstone, so a listener's only correct
 * response is to re-read. That is also why there is no local database yet — one
 * would not save a single request.
 *
 * Adapted from the archived talk client's `EventStream.kt`, with three changes
 * that matter:
 *
 *  1. The client is **derived** from the authenticated one rather than built
 *     fresh, so the stream inherits the auth interceptor *and* the 401 renewal.
 *     A stream that could not renew would die fifteen minutes in.
 *  2. No filtering on the event `type`. This server sends no `event:` field, so
 *     the type is always null; its heartbeat is a bare `: ping` comment, which
 *     okhttp-sse already discards.
 *  3. Reconnection with backoff. The server advertises `retry: 3000`, but
 *     okhttp-sse does not act on it — nothing reconnects unless we do.
 */
@Singleton
class ChangeEventStream @Inject constructor(
    @ApiClient okHttpClient: OkHttpClient,
    private val json: Json,
) {
    private val sseClient: OkHttpClient = okHttpClient.newBuilder()
        // The stream is meant to stay open; any read timeout would kill it
        // between two changes.
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    fun changes(): Flow<ChangeEvent> = rawEvents().retryWhen { cause, attempt ->
        if (cause is CancellationSignal) return@retryWhen false
        delay(min(1_000L shl attempt.toInt().coerceAtMost(5), MAX_BACKOFF_MS))
        true
    }

    private class CancellationSignal : Exception()

    private fun rawEvents(): Flow<ChangeEvent> = callbackFlow {
        val request = Request.Builder()
            .url("${BaseUrlInterceptor.PLACEHOLDER_BASE_URL}api/v1/events")
            .header("Accept", "text/event-stream")
            .build()

        val listener = object : EventSourceListener() {
            override fun onEvent(
                eventSource: EventSource,
                id: String?,
                type: String?,
                data: String,
            ) {
                runCatching { json.decodeFromString<ChangeEvent>(data) }
                    .onSuccess { trySend(it) }
                // A frame we cannot parse is a contract drift, not a reason to
                // tear down the stream.
            }

            override fun onClosed(eventSource: EventSource) {
                close(CancellationSignal())
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                close(t ?: IllegalStateException("SSE stream failed: ${response?.code}"))
            }
        }

        val source = EventSources.createFactory(sseClient).newEventSource(request, listener)
        awaitClose { source.cancel() }
    }

    private companion object {
        const val MAX_BACKOFF_MS = 30_000L
    }
}
