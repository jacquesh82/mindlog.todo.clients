import Foundation

/// What the browser can hand back on the return leg.
public enum OAuthCallback: Sendable, Equatable {
    case tokens(accessToken: String, refreshToken: String)

    /// mindlog id returned a profile with no email address. The server issues a
    /// short-lived pending token and the app has to collect one.
    case needsEmail(pendingToken: String)

    case failed(reason: String)
}

/// Carries the OAuth return leg to whichever screen is waiting.
///
/// On Android the deep link lands on `MainActivity.onNewIntent` and the outcome
/// belongs to the sign-in screen, so a shared flow sits between them. Here
/// `ASWebAuthenticationSession` usually hands the callback URL straight back to
/// the caller — but not always: the user can be bounced through Safari and
/// return through the app's URL scheme instead. Both paths end at ``handle(_:)``
/// so the sign-in screen has one thing to listen to.
@MainActor
public final class OAuthCallbackBus {

    public let callbacks: AsyncStream<OAuthCallback>
    private let continuation: AsyncStream<OAuthCallback>.Continuation

    public init() {
        (callbacks, continuation) = AsyncStream<OAuthCallback>.makeStream(
            bufferingPolicy: .bufferingNewest(1)
        )
    }

    /// Parses `…://auth/callback#access_token=…&refresh_token=…&expires_in=…`,
    /// or `#mindlog_id_pending=…`.
    ///
    /// The tokens travel in the **fragment**, not the query, so they never reach
    /// a server log or a Referer header. The web and Android clients read the
    /// same shape.
    ///
    /// - Returns: whether the URL was one of ours.
    @discardableResult
    public func handle(_ url: URL) -> Bool {
        guard let fragment = URLComponents(url: url, resolvingAgainstBaseURL: false)?
            .percentEncodedFragment
        else { return false }

        let params = Self.parse(fragment: fragment)

        let callback: OAuthCallback
        if let accessToken = params["access_token"], let refreshToken = params["refresh_token"] {
            callback = .tokens(accessToken: accessToken, refreshToken: refreshToken)
        } else if let pending = params["mindlog_id_pending"] {
            callback = .needsEmail(pendingToken: pending)
        } else if let error = params["error"] {
            callback = .failed(reason: error)
        } else {
            return false
        }

        continuation.yield(callback)
        return true
    }

    /// Decoded pair by pair rather than through `URLComponents(query:)`: the
    /// fragment is not a query, and a token containing `+` would come back with
    /// a space in it if it were treated as one.
    private static func parse(fragment: String) -> [String: String] {
        fragment.split(separator: "&").reduce(into: [:]) { params, part in
            guard let separator = part.firstIndex(of: "="),
                  separator != part.startIndex
            else { return }
            let name = String(part[part.startIndex ..< separator])
            let value = String(part[part.index(after: separator)...])
            guard let name = name.removingPercentEncoding,
                  let value = value.removingPercentEncoding
            else { return }
            params[name] = value
        }
    }
}
