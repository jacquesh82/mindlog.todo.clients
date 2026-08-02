import Foundation

/// Which deployment a build points at.
///
/// The counterpart of `-PmindlogEnv=…` on the Android client. The build
/// configuration writes the *name* into Info.plist (`MindlogEnv`); the URL that
/// goes with it is resolved here rather than in an .xcconfig, so the one rule
/// that has ever broken this — the trailing slash — sits somewhere a test can
/// reach it.
///
/// Only the *default* is fixed at build time. ``ServerStore`` lets the value be
/// changed at runtime, which is how a debug build gets pointed at qualif
/// without a recompile.
public enum MindlogEnvironment: String, Sendable, CaseIterable {
    case prod
    case qualif
    case local

    /// > Important: every one of these ends in `/`. Production and qualif are
    /// > served under the `/app` sub-path, and a base that does not end in a
    /// > slash makes the sub-path ambiguous the moment anything resolves a
    /// > relative path against it. `APIURLTests` pins the resulting URLs.
    public var defaultBaseURL: URL {
        switch self {
        case .prod: URL(string: "https://todo.mindlog.today/app/")!
        case .qualif: URL(string: "https://todo.gra01.mindlog.today/app/")!
        // Not 10.0.2.2: that address is the Android emulator's alias for its
        // host. The iOS simulator shares the host's network stack outright, so
        // localhost is the host.
        case .local: URL(string: "http://localhost:8080/")!
        }
    }

    /// `prod` is the default so an un-flagged build is never a staging one —
    /// same reasoning as the Android convention plugin.
    public static var current: MindlogEnvironment {
        let raw = Bundle.main.object(forInfoDictionaryKey: "MindlogEnv") as? String
        return raw.flatMap(MindlogEnvironment.init(rawValue:)) ?? .prod
    }
}
