import Foundation

/// Le découpage d'un flux `text/event-stream` en trames, ligne par ligne.
///
/// Le client Android reçoit ce travail tout fait d'okhttp-sse. `URLSession`
/// n'a pas d'équivalent, alors il est écrit ici — et séparé de
/// ``ChangeEventStream`` pour une raison précise : la boucle réseau autour ne
/// peut pas être testée sans un serveur, alors que ce qui casse dans un
/// analyseur SSE — le battement de cœur pris pour une donnée, la trame à
/// plusieurs lignes recollée sans saut de ligne — se teste ici en une ligne.
///
/// Ce serveur n'utilise qu'un champ : `data:`. Il n'envoie pas d'`event:`, et
/// son battement de cœur est un commentaire `: ping` nu. `id:` et `retry:` sont
/// reconnus par la spécification mais ce serveur ne s'en sert pas, et le client
/// n'en ferait rien.
struct SSEFrameDecoder {

    private var payload = ""

    /// - Returns: la charge utile de la trame que cette ligne vient de clore, ou
    ///   nil si la trame n'est pas finie.
    mutating func feed(_ line: String) -> String? {
        // Une ligne vide clôt la trame.
        if line.isEmpty {
            defer { payload = "" }
            return payload.isEmpty ? nil : payload
        }

        // Commentaire — le battement de cœur en est un.
        if line.hasPrefix(":") { return nil }

        guard line.hasPrefix("data:") else { return nil }

        // Un seul espace après les deux-points est un séparateur, pas une
        // donnée ; les suivants en sont.
        var value = line.dropFirst("data:".count)
        if value.hasPrefix(" ") { value = value.dropFirst() }

        // Plusieurs `data:` dans une trame se recollent séparés par un saut de
        // ligne : c'est ce que dit la spécification, et c'est ce qui permet à un
        // JSON indenté de traverser intact.
        payload += payload.isEmpty ? String(value) : "\n" + value
        return nil
    }
}
