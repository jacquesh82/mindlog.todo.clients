import CoreData
import CoreDesignSystem
import CoreNetwork
import SwiftUI

public struct TasksView: View {

    @State private var viewModel: TasksViewModel
    @State private var columns = NavigationSplitViewVisibility.doubleColumn

    public init(viewModel: TasksViewModel) {
        _viewModel = State(initialValue: viewModel)
    }

    public var body: some View {
        NavigationSplitView(columnVisibility: $columns) {
            NavigationSidebar(
                state: viewModel.navigation,
                selected: viewModel.view,
                onSelect: viewModel.select
            )
        } detail: {
            NavigationStack { taskList }
        }
        .navigationSplitViewStyle(.balanced)
        .onAppear { viewModel.start() }
        .sheet(isPresented: quickAddPresented) {
            QuickAddSheet(viewModel: viewModel)
                .presentationDetents([.height(220)])
        }
    }

    private var taskList: some View {
        Group {
            switch viewModel.tasks {
            case .loading:
                ProgressView()
                    .frame(maxWidth: .infinity, maxHeight: .infinity)

            case .failed:
                VStack(spacing: 12) {
                    Text("Could not load your tasks.").font(.mindlogBodyLarge)
                    Button("Try again") { viewModel.refresh() }
                        .buttonStyle(.bordered)
                }
                .padding(24)
                .frame(maxWidth: .infinity, maxHeight: .infinity)

            case let .ready(tasks) where tasks.isEmpty:
                Text("Nothing in \(viewModel.view.title).")
                    .font(.mindlogBodyLarge)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)

            case let .ready(tasks):
                List(tasks) { task in
                    TaskRow(task: task) { viewModel.setDone(task.id) }
                        .listRowInsets(.init(top: 12, leading: 16, bottom: 12, trailing: 16))
                }
                .listStyle(.plain)
                .refreshable { viewModel.refresh() }
            }
        }
        // Le titre EST la vue sélectionnée : c'est le seul repère qui dise ce
        // que la liste montre une fois la colonne latérale refermée.
        .navigationTitle(viewModel.view.title)
        .navigationBarTitleDisplayMode(.inline)
        .background(MindlogColor.background)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button("Sign out", systemImage: "rectangle.portrait.and.arrow.right") {
                    viewModel.signOut()
                }
            }
            ToolbarItem(placement: .bottomBar) {
                Button("Add a task", systemImage: "plus") { viewModel.showQuickAdd() }
                    .buttonStyle(.borderedProminent)
            }
        }
    }

    private var quickAddPresented: Binding<Bool> {
        Binding(
            get: { viewModel.quickAdd.visible },
            set: { if !$0 { viewModel.hideQuickAdd() } }
        )
    }
}

private struct TaskRow: View {

    let task: TodoTask
    let onComplete: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            // Ring coloured by priority, mirroring the web and Android clients'
            // task rows so the three do not develop separate visual
            // vocabularies. 1 is urgent.
            Button(action: onComplete) {
                Image(systemName: "circle")
                    .font(.system(size: 22))
                    .foregroundStyle(MindlogColor.priority[task.priority] ?? MindlogColor.outline)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Mark done")

            VStack(alignment: .leading, spacing: 2) {
                Text(task.title).font(.mindlogBodyLarge)
                if let dueDate = task.dueDate {
                    Text(dueDate.prefix(10))
                        .font(.mindlogBodyMedium)
                        .foregroundStyle(MindlogColor.onSurfaceVariant)
                }
            }

            Spacer(minLength: 0)
        }
    }
}

private struct QuickAddSheet: View {

    @Bindable var viewModel: TasksViewModel
    @FocusState private var focused: Bool

    var body: some View {
        VStack(spacing: 16) {
            // One field, not a form: the server parses dates, #project, @label
            // and p1–p4 out of the text, so duplicating those rules in Swift
            // would only create a second, divergent parser.
            TextField("Buy bread tomorrow p1", text: $viewModel.quickAdd.text)
                .textFieldStyle(.roundedBorder)
                .submitLabel(.done)
                .focused($focused)
                .disabled(viewModel.quickAdd.busy)
                .onSubmit { viewModel.submitQuickAdd() }

            Button("Add") { viewModel.submitQuickAdd() }
                .buttonStyle(.borderedProminent)
                .frame(maxWidth: .infinity)
                .disabled(viewModel.quickAdd.busy || viewModel.quickAdd.text.isEmpty)

            Spacer(minLength: 0)
        }
        .padding(24)
        .onAppear { focused = true }
    }
}
