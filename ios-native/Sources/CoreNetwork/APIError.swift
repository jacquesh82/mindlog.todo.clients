import Foundation

/// What a call to the API can fail with.
///
/// Deliberately closed and `Sendable`, where the Android client passes
/// `Throwable` around: an error raised on a background task and rendered on the
/// main actor crosses an isolation boundary, and `any Error` does not. The
/// cases are the ones callers actually branch on — the sign-in screen maps
/// ``http(status:body:)`` 401 and 429 to their own wording, and everything else
/// to one sentence.
public enum APIError: Error, Sendable, Equatable {

    /// The configured server is not a URL a request can be built from.
    case invalidURL

    /// The server was never reached — no network, DNS, TLS, timeout. The
    /// counterpart of `IOException` on the Android side, and the reason a
    /// failed session restore does not sign the user out.
    case transport(message: String)

    /// The server answered, and refused.
    case http(status: Int, body: Data)

    /// The server answered something this build cannot read. A contract drift,
    /// not a network problem.
    case decoding(message: String)

    public var statusCode: Int? {
        if case let .http(status, _) = self { status } else { nil }
    }
}
