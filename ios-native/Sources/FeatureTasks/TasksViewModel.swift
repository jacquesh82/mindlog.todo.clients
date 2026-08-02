import CoreData
import Foundation
import Observation

public struct QuickAddState: Sendable, Equatable {
    public var visible = false
    public var text = ""
    public var busy = false

    public init() {}
}

@MainActor
@Observable
public final class TasksViewModel {

    private let taskRepository: TaskRepository
    private let navigationRepository: NavigationRepository
    private let authRepository: AuthRepository

    @ObservationIgnored private var started = false

    /// Ces trois-là ne sont pas recopiés dans un état local : ils sont lus
    /// directement dans les dépôts, qui sont eux-mêmes observables. Un miroir
    /// ici serait une seconde source de vérité à tenir synchronisée, ce que le
    /// `StateFlow` exposé tel quel du côté Android évite déjà.
    public var tasks: TasksState { taskRepository.state }
    public var navigation: NavigationState { navigationRepository.state }
    public var view: TaskView { taskRepository.view }

    public var quickAdd = QuickAddState()

    public init(
        taskRepository: TaskRepository,
        navigationRepository: NavigationRepository,
        authRepository: AuthRepository
    ) {
        self.taskRepository = taskRepository
        self.navigationRepository = navigationRepository
        self.authRepository = authRepository
    }

    public func start() {
        guard !started else { return }
        started = true
        Task { await taskRepository.refresh() }
        Task { await navigationRepository.refresh() }
    }

    /// Sélection dans le tiroir : le dépôt recharge la liste pour cette vue.
    public func select(_ view: TaskView) {
        taskRepository.select(view)
    }

    public func refresh() {
        Task { await taskRepository.refresh() }
    }

    public func setDone(_ id: String) {
        Task { _ = await taskRepository.setDone(id, done: true) }
    }

    public func showQuickAdd() {
        quickAdd = QuickAddState()
        quickAdd.visible = true
    }

    public func hideQuickAdd() {
        quickAdd = QuickAddState()
    }

    public func submitQuickAdd() {
        let text = quickAdd.text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return }
        quickAdd.busy = true
        Task {
            _ = await taskRepository.quickAdd(text)
            quickAdd = QuickAddState()
        }
    }

    public func signOut() {
        Task { await authRepository.logout() }
    }
}
