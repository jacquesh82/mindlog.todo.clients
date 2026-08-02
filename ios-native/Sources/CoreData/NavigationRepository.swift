import CoreDatastore
import CoreNetwork
import Foundation
import Observation

/// Compteurs de tâches ouvertes affichés en regard de chaque entrée.
///
/// Calculés ICI, à partir d'une seule lecture des tâches ouvertes, plutôt que
/// demandés au serveur entrée par entrée : un badge par projet et par étiquette
/// ferait autant de requêtes que le tiroir a de lignes. Le client web fait le
/// même choix dans `reloadSidebar`.
public struct DrawerCounts: Sendable, Equatable {
    public var today: Int = 0
    public var inbox: Int = 0
    public var byProject: [String: Int] = [:]
    public var byLabel: [String: Int] = [:]

    public init() {}
}

public enum NavigationState: Sendable {

    case loading
    case ready(Ready)
    case failed(APIError)

    public struct Ready: Sendable, Equatable {
        public var projects: [TodoProject]
        public var labels: [TodoLabel]
        public var filters: [TodoFilter]
        public var counts: DrawerCounts
    }

    /// L'équivalent du `state as? NavigationState.Ready` des écrans Android.
    public var ready: Ready? {
        if case let .ready(ready) = self { ready } else { nil }
    }
}

/// Everything the navigation drawer shows: projects, labels and saved filters.
///
/// One repository rather than three, because these three lists are never read
/// apart — the drawer shows them together and any of their change events makes
/// the whole drawer stale. Splitting them would triple the stream subscription,
/// the state machine and the reload path to serve a single screen; the web
/// client reaches the same conclusion in `reloadSidebar`.
///
/// Sections are deliberately NOT part of this state. They belong to one project,
/// are only read when that project is open, and loading every project's sections
/// to draw a drawer that never displays them would be a request per project on
/// every reload.
///
/// No local cache, for the reason spelled out on ``TaskRepository``: the change
/// stream carries invalidation without payload, so every event forces a full
/// re-read regardless.
@MainActor
@Observable
public final class NavigationRepository {

    private let api: TodoAPI
    private let events: ChangeEventStream
    private let sessionStore: SessionStore

    @ObservationIgnored private var watch: Task<Void, Never>?

    public private(set) var state: NavigationState = .loading

    public init(api: TodoAPI, events: ChangeEventStream, sessionStore: SessionStore) {
        self.api = api
        self.events = events
        self.sessionStore = sessionStore
    }

    /// Ces dépôts vivent aussi longtemps que le processus — c'est ce que
    /// `@ApplicationScope` dit du côté Android — donc rien ne les libère et il
    /// n'y a pas de `deinit` à écrire. L'arrêt existe pour les tests.
    public func stopWatching() {
        watch?.cancel()
        watch = nil
    }

    /// Same signed-in guard as ``TaskRepository/startWatching()``: subscribing
    /// before a session exists races the token refresh and can sign the user
    /// out.
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
                    let signals = changeSignals(
                        from: self.events.changes(),
                        matching: Self.drawerEntities,
                        debounce: .milliseconds(Self.debounceMilliseconds)
                    )
                    for await _ in signals { await self.refresh() }
                }
            }
        }
    }

    /// The four reads run CONCURRENTLY. Sequentially, the drawer would wait for
    /// the sum of four round trips on a mobile link; here it waits for the
    /// slowest. One failure fails the whole state: a drawer missing its filters
    /// without saying so is worse than a drawer that reports an error.
    public func refresh() async {
        guard sessionStore.accessToken != nil else { return }
        do {
            async let projects = api.listProjects()
            async let labels = api.listLabels()
            async let filters = api.listFilters()
            // Les tâches ouvertes ne servent QU'aux compteurs : la liste
            // affichée est celle du dépôt de tâches, qui suit la vue
            // sélectionnée et n'a pas la même portée.
            async let open = api.listTasks(completed: false)

            let loadedProjects = try await projects
            let loadedLabels = try await labels
            let loadedFilters = try await filters
            let loadedOpen = try await open

            state = .ready(
                .init(
                    projects: loadedProjects,
                    labels: loadedLabels,
                    filters: loadedFilters,
                    counts: Self.counts(of: loadedOpen, projects: loadedProjects)
                )
            )
        } catch {
            // `async let` perd le type d'erreur déclaré : le compilateur ne voit
            // plus que `any Error`. Tout ce qui n'est pas une ``APIError`` ici
            // est une annulation de tâche, qui n'a pas d'autre représentation
            // utile pour un écran.
            state = .failed(error as? APIError ?? .transport(message: "\(error)"))
        }
    }

    /// `dueDate` est comparée sur ses dix premiers caractères, donc sur la date
    /// civile telle que le serveur l'a écrite. C'est une approximation assumée
    /// pour un badge : la borne exacte d'« aujourd'hui » (minuit local) est
    /// appliquée par le serveur quand la vue est réellement ouverte.
    private static func counts(of open: [TodoTask], projects: [TodoProject]) -> DrawerCounts {
        let today = civilDay.string(from: Date())
        let inboxId = projects.first { $0.isInbox }?.id

        // `filter { }.count` et non `count(where:)` : cette dernière n'existe
        // qu'à partir d'iOS 18, alors que ce client vise iOS 17.
        var counts = DrawerCounts()
        counts.today = open.filter { task in
            guard let due = task.dueDate else { return false }
            return String(due.prefix(10)) <= today
        }.count
        counts.inbox = inboxId.map { id in open.filter { $0.projectId == id }.count } ?? 0
        counts.byProject = Dictionary(grouping: open.compactMap(\.projectId), by: { $0 })
            .mapValues(\.count)
        counts.byLabel = Dictionary(grouping: open.flatMap(\.labelIds), by: { $0 })
            .mapValues(\.count)
        return counts
    }

    // MARK: - Projects
    // Mutations do not touch `state` optimistically: unlike ticking a task off,
    // none of them is on a latency-critical path, and the server decides
    // ordering (`position`) and inbox rules. A reload after the call keeps one
    // source of truth.

    public func createProject(
        name: String,
        color: String? = nil,
        parentId: String? = nil
    ) async -> Result<TodoProject, APIError> {
        await reloading {
            try await self.api.createProject(
                .init(color: color, name: name, parentId: parentId)
            )
        }
    }

    public func renameProject(
        _ id: String,
        name: String
    ) async -> Result<TodoProject, APIError> {
        await reloading { try await self.api.updateProject(id, .init(name: name)) }
    }

    public func setProject(
        _ id: String,
        favorite: Bool
    ) async -> Result<TodoProject, APIError> {
        await reloading { try await self.api.updateProject(id, .init(isFavorite: favorite)) }
    }

    /// Archiving hides a project without losing its tasks — the reversible half
    /// of delete.
    public func setProject(
        _ id: String,
        archived: Bool
    ) async -> Result<TodoProject, APIError> {
        await reloading { try await self.api.updateProject(id, .init(archived: archived)) }
    }

    public func deleteProject(_ id: String) async -> Result<Void, APIError> {
        await reloading { try await self.api.deleteProject(id) }
    }

    // MARK: - Sections
    // Read on demand, not cached: see the type's note.

    public func sections(projectId: String) async -> Result<[TodoSection], APIError> {
        await attempting { try await self.api.listSections(projectId: projectId) }
    }

    public func createSection(
        projectId: String,
        name: String
    ) async -> Result<TodoSection, APIError> {
        await attempting {
            try await self.api.createSection(.init(name: name, projectId: projectId))
        }
    }

    public func renameSection(
        _ id: String,
        name: String
    ) async -> Result<TodoSection, APIError> {
        await attempting { try await self.api.updateSection(id, .init(name: name)) }
    }

    public func deleteSection(_ id: String) async -> Result<Void, APIError> {
        await attempting { try await self.api.deleteSection(id) }
    }

    // MARK: - Labels

    public func createLabel(
        name: String,
        color: String? = nil
    ) async -> Result<TodoLabel, APIError> {
        await reloading { try await self.api.createLabel(.init(color: color, name: name)) }
    }

    public func renameLabel(_ id: String, name: String) async -> Result<TodoLabel, APIError> {
        await reloading { try await self.api.updateLabel(id, .init(name: name)) }
    }

    public func deleteLabel(_ id: String) async -> Result<Void, APIError> {
        await reloading { try await self.api.deleteLabel(id) }
    }

    // MARK: - Filters (saved views)

    public func createFilter(
        name: String,
        query: String,
        color: String? = nil
    ) async -> Result<TodoFilter, APIError> {
        await reloading {
            try await self.api.createFilter(.init(color: color, name: name, query: query))
        }
    }

    public func updateFilter(
        _ id: String,
        name: String? = nil,
        query: String? = nil
    ) async -> Result<TodoFilter, APIError> {
        await reloading { try await self.api.updateFilter(id, .init(name: name, query: query)) }
    }

    public func deleteFilter(_ id: String) async -> Result<Void, APIError> {
        await reloading { try await self.api.deleteFilter(id) }
    }

    /// Runs the saved query server-side; the filter grammar is never parsed here.
    public func runFilter(_ id: String) async -> Result<[TodoTask], APIError> {
        await attempting { try await self.api.runFilter(id) }
    }

    // MARK: - Internals

    /// `runCatching { … }` — rien de plus.
    ///
    /// Deux annotations non décoratives sur le paramètre :
    ///
    ///  - `@MainActor`, sans quoi le compilateur tient la fermeture pour non
    ///    isolée et le `T` qu'elle rend traverse une frontière d'acteur qu'il
    ///    n'a aucune raison de traverser — le dépôt et l'appel sont sur le même.
    ///  - `throws` et non `throws(APIError)` : Swift 6.0 n'infère pas le type
    ///    d'erreur d'une fermeture littérale depuis son type attendu, donc la
    ///    version typée refuse tous les appels. Le `catch` ci-dessous rétablit
    ///    la garantie que la signature promet.
    private func attempting<T>(
        _ body: @MainActor () async throws -> T
    ) async -> Result<T, APIError> {
        do {
            let value = try await body()
            return .success(value)
        } catch let error as APIError {
            return .failure(error)
        } catch {
            return .failure(.transport(message: "\(error)"))
        }
    }

    /// `runCatching { … }.onSuccess { refresh() }`.
    private func reloading<T>(
        _ body: @MainActor () async throws -> T
    ) async -> Result<T, APIError> {
        let result = await attempting(body)
        if case .success = result { await refresh() }
        return result
    }

    private static let drawerEntities: Set<TodoChangeEvent.TodoEntity> = [
        .project, .section, .label, .filter,
    ]

    /// Same 300 ms window as ``TaskRepository``: one burst, one reload.
    private static let debounceMilliseconds = 300

    private static let civilDay: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "yyyy-MM-dd"
        formatter.timeZone = .current
        return formatter
    }()
}
