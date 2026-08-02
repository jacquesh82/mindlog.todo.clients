import CoreDatastore
import CoreNetwork
import Foundation
import Observation

public enum TasksState: Sendable {
    case loading
    case ready([TodoTask])
    case failed(APIError)
}

/// The open task list, kept in memory.
///
/// No local database at milestone 1, and that is a consequence of the API rather
/// than a shortcut: the change stream carries invalidation signals with no
/// payload, there is no `changedSince` cursor and no tombstone, so every event
/// forces a full re-read regardless. A SwiftData layer would add a schema,
/// migrations and a second source of truth without removing one request.
/// Offline writes are what would justify it — and they need a server-side answer
/// for deletions first.
///
/// Three things cause a reload: opening the screen, a `task` event on the
/// stream, and a local mutation.
@MainActor
@Observable
public final class TaskRepository {

    private let api: TodoAPI
    private let events: ChangeEventStream
    private let sessionStore: SessionStore
    private let navigation: NavigationRepository

    @ObservationIgnored private var watch: Task<Void, Never>?

    public private(set) var state: TasksState = .loading

    /// Vue courante ; « Aujourd'hui » à l'ouverture, comme sur le web.
    public private(set) var view: TaskView = .today

    public init(
        api: TodoAPI,
        events: ChangeEventStream,
        sessionStore: SessionStore,
        navigation: NavigationRepository
    ) {
        self.api = api
        self.events = events
        self.sessionStore = sessionStore
        self.navigation = navigation
    }

    public func stopWatching() {
        watch?.cancel()
        watch = nil
    }

    /// Subscribes to the change stream, but only while signed in.
    ///
    /// The guard is not tidiness. Opening the stream before a session exists
    /// sends it out with no `Authorization` header, the server answers 401, and
    /// ``CoreNetwork/TokenRefresher`` renews the token — *while*
    /// ``AuthRepository/restoreSession()`` is renewing it too. Refresh tokens
    /// rotate, so whichever of the two arrives second presents an invalidated
    /// token, gets a 401, and signs the user out. Waiting for a session removes
    /// the second refresher entirely.
    public func startWatching() {
        watch?.cancel()
        watch = Task { [weak self] in
            var subscription: Task<Void, Never>?
            defer { subscription?.cancel() }

            guard let store = self?.sessionStore else { return }
            for await signedIn in store.signedInChanges() {
                subscription?.cancel()
                guard signedIn == true else { continue }
                subscription = Task { [weak self] in
                    guard let self else { return }
                    // A quick-add can touch several rows at once; the same
                    // 300 ms window the web client settles on collapses that
                    // into one reload.
                    let signals = changeSignals(
                        from: self.events.changes(),
                        matching: [.task],
                        debounce: .milliseconds(Self.debounceMilliseconds)
                    )
                    for await _ in signals { await self.refresh() }
                }
            }
        }
    }

    /// Sélectionne ce que la liste montre. Le rechargement passe par le serveur,
    /// y compris pour un simple projet : filtrer localement supposerait d'avoir
    /// DÉJÀ toutes les tâches, ce que `limit=200` ne garantit pas — la liste
    /// paraîtrait juste jusqu'au jour où elle omettrait des lignes en silence.
    public func select(_ view: TaskView) {
        self.view = view
        Task { await refresh() }
    }

    public func refresh() async {
        guard sessionStore.accessToken != nil else { return }
        do {
            let tasks = try await load(view)
            state = .ready(tasks)
        } catch {
            state = .failed(error)
        }
    }

    private func load(_ view: TaskView) async throws(APIError) -> [TodoTask] {
        switch view {
        // `root: true` n'est demandé que sur les vues d'ensemble : dans un
        // projet ou un filtre, une sous-tâche dont le parent vit ailleurs doit
        // rester visible, sinon elle disparaît de la seule vue qui la contient.
        case .today:
            return try await api.listTasks(
                completed: false,
                root: true,
                dueBefore: Self.tomorrow()
            )
        case .all:
            return try await api.listTasks(completed: false, root: true)
        case .inbox:
            guard let id = await inboxProjectId() else { return [] }
            return try await api.listTasks(completed: false, projectId: id)
        case let .project(id, _):
            return try await api.listTasks(completed: false, projectId: id)
        case let .label(id, _):
            return try await api.listTasks(completed: false, labelId: id)
        case let .filter(id, _):
            return try await api.runFilter(id)
        }
    }

    /// « Aujourd'hui » = dû AVANT demain, ce qui inclut les retards — la même
    /// borne que le client web. Minuit local, pas UTC : la journée de
    /// l'utilisateur est celle de son fuseau.
    private static func tomorrow() -> String {
        let calendar = Calendar.current
        let startOfTomorrow = calendar.date(
            byAdding: .day,
            value: 1,
            to: calendar.startOfDay(for: Date())
        ) ?? Date()
        return ISO8601DateFormatter().string(from: startOfTomorrow)
    }

    /// La boîte de réception est un PROJET marqué `isInbox`, pas une vue à part.
    /// On lit d'abord l'état du tiroir, déjà chargé dans l'écrasante majorité des
    /// cas ; le repli n'existe que pour la sélection faite avant sa première
    /// réponse.
    private func inboxProjectId() async -> String? {
        if let id = navigation.state.ready?.projects.first(where: \.isInbox)?.id {
            return id
        }
        return try? await api.listProjects().first(where: \.isInbox)?.id
    }

    public func quickAdd(_ text: String) async -> Result<TodoTask, APIError> {
        do {
            // Minutes east of UTC, so the server resolves "tomorrow" against
            // the user's day rather than its own.
            let tz = TimeZone.current.secondsFromGMT() / 60
            let task = try await api.quickAdd(.init(text: text, tz: tz))
            await refresh()
            return .success(task)
        } catch {
            return .failure(error)
        }
    }

    public func setDone(_ id: String, done: Bool) async -> Result<Void, APIError> {
        // Optimistic: the checkbox must not wait on a round trip.
        if case let .ready(tasks) = state {
            state = .ready(tasks.filter { !(done && $0.id == id) })
        }

        do {
            _ = try await api.updateTask(id, .init(status: done ? .done : .todo))
            return .success(())
        } catch {
            // Roll back by re-reading rather than by restoring the old list:
            // the failure may itself be a sign the client is out of date.
            await refresh()
            return .failure(error)
        }
    }

    private static let debounceMilliseconds = 300
}
