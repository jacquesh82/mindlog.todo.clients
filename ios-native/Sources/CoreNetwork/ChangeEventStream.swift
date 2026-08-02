import CoreDatastore
import Foundation

/// The server's change stream.
///
/// What arrives is an **invalidation signal, not a delta**: `{entity, action,
/// id?}` says what kind of thing changed, never what it became. There is no
/// `changedSince` cursor and no tombstone, so a listener's only correct
/// response is to re-read. That is also why there is no local database — one
/// would not save a single request.
///
/// The Android client gets its SSE parsing from okhttp-sse. `URLSession` has no
/// equivalent, so the framing is done here; it is twenty lines because this
/// server uses one field. The three things that matter are the same:
///
///  1. The stream carries the access token and **renews it**, through the same
///     ``TokenRefresher`` the request path uses. A stream that could not renew
///     would die fifteen minutes in.
///  2. No filtering on the event `type`. This server sends no `event:` field,
///     and its heartbeat is a bare `: ping` comment.
///  3. Reconnection with backoff. The server advertises `retry: 3000` and
///     nothing acts on it unless we do.
public final class ChangeEventStream: Sendable {

    private let serverStore: ServerStore
    private let sessionStore: SessionStore
    private let refresher: TokenRefresher
    private let session: URLSession

    public init(
        serverStore: ServerStore,
        sessionStore: SessionStore,
        refresher: TokenRefresher
    ) {
        self.serverStore = serverStore
        self.sessionStore = sessionStore
        self.refresher = refresher

        let configuration = URLSessionConfiguration.default
        // The stream is meant to stay open; the default 60 s request timeout
        // would kill it between two changes. This is the counterpart of the
        // Android client's `readTimeout(0)`.
        configuration.timeoutIntervalForRequest = .greatestFiniteMagnitude
        configuration.timeoutIntervalForResource = .greatestFiniteMagnitude
        configuration.waitsForConnectivity = true
        self.session = URLSession(configuration: configuration)
    }

    /// Reconnects on its own until the consumer stops iterating.
    public func changes() -> AsyncStream<TodoChangeEvent> {
        AsyncStream { continuation in
            let task = Task {
                var attempt = 0
                while !Task.isCancelled {
                    do {
                        let outcome = try await self.consume(into: continuation)
                        switch outcome {
                        case .closedByServer:
                            // A clean close is not a failure and is not retried
                            // — same call `retryWhen` makes on the Android side
                            // when okhttp reports `onClosed`.
                            continuation.finish()
                            return
                        case .unauthorized:
                            // Renew and reconnect straight away: this is an
                            // expired token, not an unhealthy server, and there
                            // is nothing to back off from.
                            let stale = await self.sessionStore.accessToken
                            guard await self.refresher.freshToken(
                                replacing: stale
                            ) != nil else {
                                continuation.finish()
                                return
                            }
                            attempt = 0
                            continue
                        }
                    } catch is CancellationError {
                        break
                    } catch {
                        // Anything else: the connection dropped or the server
                        // is unwell. Back off and try again.
                    }

                    try? await Task.sleep(for: .milliseconds(Self.backoffMilliseconds(attempt)))
                    attempt += 1
                }
                continuation.finish()
            }
            continuation.onTermination = { _ in task.cancel() }
        }
    }

    // MARK: - Internals

    private enum Outcome {
        case closedByServer
        case unauthorized
    }

    private func consume(
        into continuation: AsyncStream<TodoChangeEvent>.Continuation
    ) async throws -> Outcome {
        let base = await serverStore.baseURL
        guard let url = APIURL.resolve(base: base, path: "api/v1/events") else {
            throw APIError.invalidURL
        }

        var request = URLRequest(url: url)
        request.setValue("text/event-stream", forHTTPHeaderField: "Accept")
        if let token = await sessionStore.accessToken {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }

        let (bytes, response) = try await session.bytes(for: request)
        guard let http = response as? HTTPURLResponse else {
            throw APIError.transport(message: "Not an HTTP response.")
        }
        if http.statusCode == 401 { return .unauthorized }
        guard (200 ..< 300).contains(http.statusCode) else {
            throw APIError.http(status: http.statusCode, body: Data())
        }

        let decoder = JSONDecoder()
        var frames = SSEFrameDecoder()

        for try await line in bytes.lines {
            guard let payload = frames.feed(line) else { continue }
            guard let event = try? decoder.decode(
                TodoChangeEvent.self,
                from: Data(payload.utf8)
            ) else {
                // A frame this build cannot parse is a contract drift, not a
                // reason to tear down the stream.
                continue
            }
            continuation.yield(event)
        }

        return .closedByServer
    }

    /// 1 s, 2 s, 4 s … capped at 30 — the same ramp as the Android client.
    private static func backoffMilliseconds(_ attempt: Int) -> Int {
        min(1_000 << min(attempt, 5), 30_000)
    }
}
