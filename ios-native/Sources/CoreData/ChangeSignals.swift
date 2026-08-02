import CoreNetwork
import Foundation

/// Réduit une rafale d'événements de changement à un seul signal de rechargement.
///
/// C'est le `debounce(300)` des deux dépôts Android, qui n'a pas d'équivalent
/// dans `AsyncSequence` : un ajout rapide peut toucher plusieurs lignes, et sans
/// cette fenêtre chaque ligne provoquerait sa propre relecture complète.
///
/// Front descendant, comme kotlinx : le signal part 300 ms après le *dernier*
/// événement de la rafale, pas après le premier.
func changeSignals(
    from stream: AsyncStream<TodoChangeEvent>,
    matching entities: Set<TodoChangeEvent.TodoEntity>,
    debounce window: Duration
) -> AsyncStream<Void> {
    AsyncStream<Void> { continuation in
        let feed = Task {
            var pending: Task<Void, Never>?
            for await event in stream where entities.contains(event.entity) {
                pending?.cancel()
                pending = Task {
                    try? await Task.sleep(for: window)
                    guard !Task.isCancelled else { return }
                    continuation.yield(())
                }
            }
            pending?.cancel()
            continuation.finish()
        }
        continuation.onTermination = { _ in feed.cancel() }
    }
}
