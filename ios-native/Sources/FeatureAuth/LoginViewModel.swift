import CoreData
import CoreNetwork
import Foundation
import Observation

@MainActor
@Observable
public final class LoginViewModel {

    private let authRepository: AuthRepository
    private let callbackBus: OAuthCallbackBus

    public var email = ""
    public var password = ""
    public var pendingEmail = ""

    public private(set) var busy = false
    public private(set) var error: String?
    public private(set) var providers = AuthProviders()

    /// Non-nil once mindlog id returned an account without an email.
    public private(set) var pendingToken: String?

    @ObservationIgnored private var listening = false

    public init(authRepository: AuthRepository, callbackBus: OAuthCallbackBus) {
        self.authRepository = authRepository
        self.callbackBus = callbackBus
    }

    public var canSubmit: Bool {
        !busy && !email.isEmpty && !password.isEmpty
    }

    /// Called by the screen when it appears — the counterpart of the Android
    /// view model's `init`, which cannot be used here because a SwiftUI view may
    /// build its model more than once.
    public func start() {
        guard !listening else { return }
        listening = true

        // Ask the server which sign-in paths it has configured before drawing
        // any of their buttons.
        Task { providers = await authRepository.authProviders() }
        Task {
            for await callback in callbackBus.callbacks { handle(callback) }
        }
    }

    public func onFieldEdited() {
        error = nil
    }

    public func signIn() {
        guard canSubmit else { return }
        busy = true
        error = nil
        Task {
            let result = await authRepository.login(
                email: email.trimmingCharacters(in: .whitespacesAndNewlines),
                password: password
            )
            // On success the session store flips `signedIn` and the root view
            // swaps the screen; there is nothing left for this one to do.
            if case let .failure(cause) = result {
                busy = false
                error = Self.readable(cause)
            }
        }
    }

    public func submitPendingEmail() {
        guard let token = pendingToken, !pendingEmail.isEmpty else { return }
        busy = true
        error = nil
        Task {
            let result = await authRepository.completeMindlogId(
                pendingToken: token,
                email: pendingEmail.trimmingCharacters(in: .whitespacesAndNewlines)
            )
            if case let .failure(cause) = result {
                busy = false
                error = Self.readable(cause)
            }
        }
    }

    // MARK: - mindlog id

    /// Returns the URL to open, or nil if the provider is off.
    public func mindlogIdURL(create: Bool) -> URL? {
        providers.mindlogId ? authRepository.mindlogIdAuthURL(create: create) : nil
    }

    public var callbackScheme: String { authRepository.callbackScheme }

    /// The URL the authentication session came back with.
    public func handleCallback(_ url: URL) {
        callbackBus.handle(url)
    }

    /// The user dismissed the sheet, or the session failed to start.
    public func mindlogIdCancelled() {
        busy = false
    }

    private func handle(_ callback: OAuthCallback) {
        switch callback {
        case let .tokens(accessToken, refreshToken):
            authRepository.adoptTokens(accessToken: accessToken, refreshToken: refreshToken)
        case let .needsEmail(pendingToken):
            self.pendingToken = pendingToken
            busy = false
        case let .failed(reason):
            busy = false
            error = reason
        }
    }

    /// The wording the Android view model's `Throwable.readable()` produces,
    /// case for case.
    private static func readable(_ error: APIError) -> String {
        switch error {
        case let .http(status, _):
            switch status {
            case 401: "Incorrect email or password."
            case 429: "Too many attempts. Try again shortly."
            default: "The server refused the request (\(status))."
            }
        case .transport:
            "Cannot reach the server."
        case .invalidURL:
            "That server address is not usable."
        case .decoding:
            "The server sent something this version cannot read."
        }
    }
}
