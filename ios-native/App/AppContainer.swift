import CoreData
import CoreDatastore
import CoreNetwork
import Foundation

/// The composition root.
///
/// Where the Android client has Hilt, this has one initializer. A dependency
/// graph with a dozen nodes, all of them singletons, all of them built in one
/// order, does not need a framework to describe it — and the order here is not
/// arbitrary:
///
///  1. the **bare** HTTP client, which attaches no token;
///  2. ``AuthAPI`` on top of it, so a refresh cannot recurse into its own
///     renewal;
///  3. ``TokenRefresher``, which needs that AuthAPI;
///  4. the **authenticated** client, which needs that refresher.
///
/// Writing it out is what makes the cycle Hilt breaks with a `Provider` simply
/// not exist.
@MainActor
final class AppContainer {

    let serverStore: ServerStore
    let sessionStore: SessionStore
    let callbackBus: OAuthCallbackBus

    let authRepository: AuthRepository
    let navigationRepository: NavigationRepository
    let taskRepository: TaskRepository

    init() {
        serverStore = ServerStore()
        sessionStore = SessionStore()
        callbackBus = OAuthCallbackBus()

        // One URLSession for both clients: connection pooling is per session,
        // and two would open two pools to the same host.
        let session = HTTPClient.defaultSession()

        let bareClient = HTTPClient(serverStore: serverStore, session: session)
        let authAPI = AuthAPI(client: bareClient)
        let refresher = TokenRefresher(sessionStore: sessionStore, authAPI: authAPI)

        let authenticatedClient = HTTPClient(
            serverStore: serverStore,
            sessionStore: sessionStore,
            refresher: refresher,
            session: session
        )
        let todoAPI = TodoAPI(client: authenticatedClient)

        let events = ChangeEventStream(
            serverStore: serverStore,
            sessionStore: sessionStore,
            refresher: refresher
        )

        authRepository = AuthRepository(
            authAPI: authAPI,
            sessionStore: sessionStore,
            serverStore: serverStore
        )
        navigationRepository = NavigationRepository(
            api: todoAPI,
            events: events,
            sessionStore: sessionStore
        )
        taskRepository = TaskRepository(
            api: todoAPI,
            events: events,
            sessionStore: sessionStore,
            navigation: navigationRepository
        )
    }
}
