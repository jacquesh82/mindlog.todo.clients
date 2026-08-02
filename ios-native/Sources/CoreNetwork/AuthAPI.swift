import Foundation

/// Endpoints that do not need — and must not wait on — an access token.
///
/// Served by the bare ``HTTPClient``, so a failing refresh cannot recurse into
/// itself through ``TokenRefresher``.
///
/// Every path is **relative**: a leading slash would read as an absolute path
/// and would erase the `/app` prefix that production and qualif are served
/// under. `APIURLTests` pins this.
public struct AuthAPI: Sendable {

    private let client: HTTPClient

    public init(client: HTTPClient) {
        self.client = client
    }

    public func login(_ body: TodoLoginRequest) async throws(APIError) -> TodoAuthResult {
        try await client.send(
            .init(.post, "api/v1/auth/login", body: JSONBody.encode(body)),
            as: TodoAuthResult.self
        )
    }

    public func register(_ body: TodoRegisterRequest) async throws(APIError) -> TodoAuthResult {
        try await client.send(
            .init(.post, "api/v1/auth/register", body: JSONBody.encode(body)),
            as: TodoAuthResult.self
        )
    }

    public func refresh(_ body: TodoRefreshRequest) async throws(APIError) -> TodoAuthResult {
        try await client.send(
            .init(.post, "api/v1/auth/refresh", body: JSONBody.encode(body)),
            as: TodoAuthResult.self
        )
    }

    public func logout(_ body: TodoRefreshRequest) async throws(APIError) {
        try await client.send(.init(.post, "api/v1/auth/logout", body: JSONBody.encode(body)))
    }

    /// Finishes a mindlog id sign-in whose account carried no email address.
    public func completeMindlogId(
        _ body: TodoCompleteMindlogIdRequest
    ) async throws(APIError) -> TodoAuthResult {
        try await client.send(
            .init(.post, "api/v1/auth/mindlog-id/complete", body: JSONBody.encode(body)),
            as: TodoAuthResult.self
        )
    }

    /// Public. `authProviders` is what tells the sign-in screen whether to offer
    /// the mindlog id button at all — offering one this deployment has not
    /// configured just produces a 503 under the user's finger.
    public func version() async throws(APIError) -> TodoVersionInfo {
        try await client.send(.init(.get, "api/v1/version"), as: TodoVersionInfo.self)
    }
}
