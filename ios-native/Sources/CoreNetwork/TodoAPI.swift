import Foundation

/// The authenticated surface. Hand-written on purpose — it is a list of paths,
/// while generating it would drag in openapi-generator's own client, auth
/// handling and error types, none of which fit the stack this module builds.
/// It is `TodoTask` and its twenty fields that must never be retyped, and those
/// *are* generated.
///
/// Paths are relative; see the note on ``AuthAPI``.
public struct TodoAPI: Sendable {

    private let client: HTTPClient

    public init(client: HTTPClient) {
        self.client = client
    }

    public func me() async throws(APIError) -> TodoUser {
        try await client.send(.init(.get, "api/v1/me"), as: TodoUser.self)
    }

    // MARK: - Tasks

    /// `limit` defaults to 50 server-side and caps at 200. Milestone 1 asks for
    /// the maximum and does not paginate; the day a user has more than two
    /// hundred open tasks, that becomes a real gap rather than a hidden one.
    ///
    /// - Parameter dueBefore: ISO instant. "Today" is expressed as "due before
    ///   tomorrow", so it includes overdue.
    public func listTasks(
        completed: Bool? = nil,
        root: Bool? = nil,
        projectId: String? = nil,
        labelId: String? = nil,
        dueBefore: String? = nil,
        limit: Int = 200
    ) async throws(APIError) -> [TodoTask] {
        try await client.send(
            .init(.get, "api/v1/tasks", query: .query([
                ("completed", completed.map(String.init)),
                ("root", root.map(String.init)),
                ("projectId", projectId),
                ("labelId", labelId),
                ("dueBefore", dueBefore),
                ("limit", String(limit)),
            ])),
            as: [TodoTask].self
        )
    }

    public func createTask(_ body: TodoTaskCreateRequest) async throws(APIError) -> TodoTask {
        try await client.send(
            .init(.post, "api/v1/tasks", body: JSONBody.encode(body)),
            as: TodoTask.self
        )
    }

    /// One line of natural language, parsed server-side: dates, `#project`,
    /// `@label`, `p1`–`p4`. Using this instead of a form is what keeps the
    /// parsing rules in exactly one place.
    public func quickAdd(_ body: TodoTaskQuickAddRequest) async throws(APIError) -> TodoTask {
        try await client.send(
            .init(.post, "api/v1/tasks/quickadd", body: JSONBody.encode(body)),
            as: TodoTask.self
        )
    }

    public func updateTask(
        _ id: String,
        _ body: TodoTaskUpdateRequest
    ) async throws(APIError) -> TodoTask {
        try await client.send(
            .init(.patch, "api/v1/tasks/\(id)", body: JSONBody.encode(body)),
            as: TodoTask.self
        )
    }

    public func deleteTask(_ id: String) async throws(APIError) {
        try await client.send(.init(.delete, "api/v1/tasks/\(id)"))
    }

    // MARK: - Projects

    public func listProjects(
        includeArchived: Bool? = nil
    ) async throws(APIError) -> [TodoProject] {
        try await client.send(
            .init(.get, "api/v1/projects", query: .query([
                ("includeArchived", includeArchived.map(String.init)),
            ])),
            as: [TodoProject].self
        )
    }

    public func getProject(_ id: String) async throws(APIError) -> TodoProject {
        try await client.send(.init(.get, "api/v1/projects/\(id)"), as: TodoProject.self)
    }

    public func createProject(
        _ body: TodoProjectCreateRequest
    ) async throws(APIError) -> TodoProject {
        try await client.send(
            .init(.post, "api/v1/projects", body: JSONBody.encode(body)),
            as: TodoProject.self
        )
    }

    public func updateProject(
        _ id: String,
        _ body: TodoProjectUpdateRequest
    ) async throws(APIError) -> TodoProject {
        try await client.send(
            .init(.patch, "api/v1/projects/\(id)", body: JSONBody.encode(body)),
            as: TodoProject.self
        )
    }

    public func deleteProject(_ id: String) async throws(APIError) {
        try await client.send(.init(.delete, "api/v1/projects/\(id)"))
    }

    // MARK: - Sections

    /// `projectId` is required by the server: a section only exists inside a
    /// project, and listing them all at once is not offered.
    public func listSections(projectId: String) async throws(APIError) -> [TodoSection] {
        try await client.send(
            .init(.get, "api/v1/sections", query: .query([("projectId", projectId)])),
            as: [TodoSection].self
        )
    }

    public func createSection(
        _ body: TodoSectionCreateRequest
    ) async throws(APIError) -> TodoSection {
        try await client.send(
            .init(.post, "api/v1/sections", body: JSONBody.encode(body)),
            as: TodoSection.self
        )
    }

    public func updateSection(
        _ id: String,
        _ body: TodoSectionUpdateRequest
    ) async throws(APIError) -> TodoSection {
        try await client.send(
            .init(.patch, "api/v1/sections/\(id)", body: JSONBody.encode(body)),
            as: TodoSection.self
        )
    }

    public func deleteSection(_ id: String) async throws(APIError) {
        try await client.send(.init(.delete, "api/v1/sections/\(id)"))
    }

    // MARK: - Labels

    public func listLabels() async throws(APIError) -> [TodoLabel] {
        try await client.send(.init(.get, "api/v1/labels"), as: [TodoLabel].self)
    }

    public func createLabel(_ body: TodoLabelCreateRequest) async throws(APIError) -> TodoLabel {
        try await client.send(
            .init(.post, "api/v1/labels", body: JSONBody.encode(body)),
            as: TodoLabel.self
        )
    }

    public func updateLabel(
        _ id: String,
        _ body: TodoLabelUpdateRequest
    ) async throws(APIError) -> TodoLabel {
        try await client.send(
            .init(.patch, "api/v1/labels/\(id)", body: JSONBody.encode(body)),
            as: TodoLabel.self
        )
    }

    public func deleteLabel(_ id: String) async throws(APIError) {
        try await client.send(.init(.delete, "api/v1/labels/\(id)"))
    }

    // MARK: - Filters (saved views)

    public func listFilters() async throws(APIError) -> [TodoFilter] {
        try await client.send(.init(.get, "api/v1/filters"), as: [TodoFilter].self)
    }

    public func createFilter(_ body: TodoFilterCreateRequest) async throws(APIError) -> TodoFilter {
        try await client.send(
            .init(.post, "api/v1/filters", body: JSONBody.encode(body)),
            as: TodoFilter.self
        )
    }

    public func updateFilter(
        _ id: String,
        _ body: TodoFilterUpdateRequest
    ) async throws(APIError) -> TodoFilter {
        try await client.send(
            .init(.patch, "api/v1/filters/\(id)", body: JSONBody.encode(body)),
            as: TodoFilter.self
        )
    }

    public func deleteFilter(_ id: String) async throws(APIError) {
        try await client.send(.init(.delete, "api/v1/filters/\(id)"))
    }

    /// Runs the saved query server-side. The client never interprets the filter
    /// syntax itself — that grammar lives in one place, on the server.
    public func runFilter(_ id: String) async throws(APIError) -> [TodoTask] {
        try await client.send(.init(.get, "api/v1/filters/\(id)/tasks"), as: [TodoTask].self)
    }
}
