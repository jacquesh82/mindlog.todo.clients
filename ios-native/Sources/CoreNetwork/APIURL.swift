import Foundation

/// Builds a request URL from the server currently configured and a **relative**
/// service path.
///
/// This is the port of the Android client's `BaseUrlInterceptor`, minus the
/// interception: Retrofit needs a base URL fixed at build time and a hook to
/// rewrite it per request, `URLSession` does not, so the rewrite becomes the
/// only step there is.
///
/// The rule it exists to hold is the same one. Production and qualif are served
/// under a `/app` sub-path; anything that replaces the authority, or resolves
/// the path *against* the base instead of *appending* to it, silently drops
/// that prefix — and the failure only shows on promotion, because local is
/// served from the root and has no prefix to lose. `APIURLTests` pins the exact
/// output.
public enum APIURL {

    /// - Parameters:
    ///   - base: the configured server, with or without a trailing slash.
    ///   - path: a service path with no leading slash. A leading one would read
    ///     as "from the root of the host", which is exactly how `/app` gets
    ///     lost; it is stripped rather than trusted.
    public static func resolve(
        base: URL,
        path: String,
        query: [URLQueryItem] = []
    ) -> URL? {
        guard var components = URLComponents(url: base, resolvingAgainstBaseURL: false) else {
            return nil
        }
        let basePath = components.path.hasSuffix("/") ? components.path : components.path + "/"
        let relative = path.hasPrefix("/") ? String(path.dropFirst()) : path

        components.path = basePath + relative
        components.queryItems = query.isEmpty ? nil : query
        return components.url
    }
}
