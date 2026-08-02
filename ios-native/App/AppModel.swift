import CoreData
import Foundation
import Observation

/// The port of `AppViewModel`: what the whole app, rather than a screen, needs.
@MainActor
@Observable
final class AppModel {

    let container: AppContainer

    @ObservationIgnored private var started = false

    init(container: AppContainer = AppContainer()) {
        self.container = container
    }

    /// nil while the stored session is still being checked.
    var signedIn: Bool? { container.sessionStore.signedIn }

    func start() {
        guard !started else { return }
        started = true

        Task { await container.authRepository.restoreSession() }

        // Subscribes for the life of the process, not of a screen: a change made
        // elsewhere should already be on screen when the user comes back.
        container.taskRepository.startWatching()
    }

    /// The OAuth return leg, when it comes back through the app's URL scheme
    /// rather than through the authentication session.
    func handle(_ url: URL) {
        container.callbackBus.handle(url)
    }
}
