import Foundation
import Observation

/// Which deployment the app talks to.
///
/// ``MindlogEnvironment/current`` only sets the default; the value can be
/// changed at runtime, which is how a debug build gets pointed at qualif
/// without a recompile. Same arrangement as the Android client's `ServerStore`,
/// with `UserDefaults` where it has DataStore.
@MainActor
@Observable
public final class ServerStore {

    private let defaults: UserDefaults
    public let environment: MindlogEnvironment

    public private(set) var baseURL: URL

    public init(
        defaults: UserDefaults = .standard,
        environment: MindlogEnvironment = .current
    ) {
        self.defaults = defaults
        self.environment = environment
        self.baseURL = defaults.string(forKey: Self.key)
            .flatMap(URL.init(string:))
            ?? environment.defaultBaseURL
    }

    /// The trailing slash is not cosmetic: prod and qualif URLs carry the
    /// `/app` sub-path, and a base without one turns every relative path
    /// resolution into a guess. ``APIURL`` appends rather than resolves, so it
    /// survives either form — but the value is also concatenated as a string to
    /// build the mindlog id URL, and that one does not.
    public func set(_ url: URL) {
        let normalised = url.absoluteString.hasSuffix("/")
            ? url
            : URL(string: url.absoluteString + "/") ?? url
        baseURL = normalised
        defaults.set(normalised.absoluteString, forKey: Self.key)
    }

    public func reset() {
        defaults.removeObject(forKey: Self.key)
        baseURL = environment.defaultBaseURL
    }

    private static let key = "base_url"
}
