import CoreDatastore
import Foundation

public struct HTTPRequest: Sendable {

    public enum Method: String, Sendable {
        case get = "GET"
        case post = "POST"
        case patch = "PATCH"
        case delete = "DELETE"
    }

    public var method: Method
    /// Relative, never leading-slashed — see ``APIURL``.
    public var path: String
    public var query: [URLQueryItem]
    public var body: Data?

    public init(
        _ method: Method,
        _ path: String,
        query: [URLQueryItem] = [],
        body: Data? = nil
    ) {
        self.method = method
        self.path = path
        self.query = query
        self.body = body
    }
}

/// One request, on the server currently configured.
///
/// This is where the Android client's three OkHttp pieces land — the base URL
/// rewrite, the `Authorization` header, and the 401 renewal — because
/// `URLSession` has no interceptor chain to hang them on. They keep the same
/// division of labour:
///
///  - the token is *attached* here and nowhere else;
///  - it is *renewed* by ``TokenRefresher``, on a 401, which is the moment
///    renewal is actually warranted rather than on a clock this type would have
///    to guess at;
///  - the request is replayed **once**. A 401 that survives a fresh token is
///    not an expiry problem.
///
/// Two instances exist, exactly as on Android. The bare one carries no token
/// and no refresher, and serves ``AuthAPI``; giving it the refresher would mean
/// a failing refresh triggering its own refresh, forever.
public final class HTTPClient: Sendable {

    private let session: URLSession
    private let serverStore: ServerStore
    private let sessionStore: SessionStore?
    private let refresher: TokenRefresher?

    public init(
        serverStore: ServerStore,
        sessionStore: SessionStore? = nil,
        refresher: TokenRefresher? = nil,
        session: URLSession = .shared
    ) {
        self.serverStore = serverStore
        self.sessionStore = sessionStore
        self.refresher = refresher
        self.session = session
    }

    /// The timeouts the Android client sets on both of its OkHttp clients.
    public static func defaultSession() -> URLSession {
        let configuration = URLSessionConfiguration.default
        configuration.timeoutIntervalForRequest = 15
        configuration.timeoutIntervalForResource = 30
        return URLSession(configuration: configuration)
    }

    // MARK: - Sending

    public func send(_ request: HTTPRequest) async throws(APIError) {
        _ = try await perform(request, allowRenewal: true)
    }

    public func send<Response: Decodable & Sendable>(
        _ request: HTTPRequest,
        as _: Response.Type
    ) async throws(APIError) -> Response {
        let data = try await perform(request, allowRenewal: true)
        do {
            return try JSONDecoder().decode(Response.self, from: data)
        } catch {
            // Unknown keys are ignored by Codable, which is the behaviour the
            // Android client asks kotlinx.serialization for: the server adds
            // fields without warning and a client that refused to parse one
            // would break on every deployment. What it does NOT have is
            // `coerceInputValues` — a null where the contract promises a value
            // lands here rather than being silently defaulted.
            throw APIError.decoding(message: String(describing: error))
        }
    }

    // MARK: - Internals

    private func perform(
        _ request: HTTPRequest,
        allowRenewal: Bool
    ) async throws(APIError) -> Data {
        let base = await serverStore.baseURL
        guard let url = APIURL.resolve(base: base, path: request.path, query: request.query) else {
            throw APIError.invalidURL
        }

        var urlRequest = URLRequest(url: url)
        urlRequest.httpMethod = request.method.rawValue
        if let body = request.body {
            urlRequest.httpBody = body
            urlRequest.setValue("application/json", forHTTPHeaderField: "Content-Type")
        }

        let token: String? = if let sessionStore { await sessionStore.accessToken } else { nil }
        if let token {
            urlRequest.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }

        let data: Data
        let response: URLResponse
        do {
            (data, response) = try await session.data(for: urlRequest)
        } catch {
            throw APIError.transport(message: String(describing: error))
        }

        guard let http = response as? HTTPURLResponse else {
            throw APIError.transport(message: "Not an HTTP response.")
        }

        if http.statusCode == 401, allowRenewal, let refresher {
            guard await refresher.freshToken(replacing: token) != nil else {
                throw APIError.http(status: 401, body: data)
            }
            // Replayed with `allowRenewal: false` — one replay, never a loop.
            // The fresh token is read back from the store rather than threaded
            // through, so the replay and any request racing it use the same one.
            return try await perform(request, allowRenewal: false)
        }

        guard (200 ..< 300).contains(http.statusCode) else {
            throw APIError.http(status: http.statusCode, body: data)
        }
        return data
    }
}

// MARK: - Helpers

public extension [URLQueryItem] {
    /// Drops the pairs whose value is nil, so an unset filter is an absent
    /// parameter rather than `?projectId=null`.
    static func query(_ pairs: [(String, String?)]) -> [URLQueryItem] {
        pairs.compactMap { name, value in
            value.map { URLQueryItem(name: name, value: $0) }
        }
    }
}

enum JSONBody {
    static func encode(_ value: some Encodable) throws(APIError) -> Data {
        do {
            return try JSONEncoder().encode(value)
        } catch {
            throw APIError.decoding(message: String(describing: error))
        }
    }
}
