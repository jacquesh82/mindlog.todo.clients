import Foundation

/// Ce que le tiroir de navigation sélectionne, et donc ce que la liste montre.
///
/// Le nom est porté par la sélection plutôt que relu dans le dépôt au moment de
/// l'affichage : la barre de titre doit rester juste pendant le rechargement qui
/// suit un renommage, et une projection qui va rechercher son libellé dans une
/// liste en cours de rafraîchissement affiche un blanc.
///
/// Chaque cas dit AU SERVEUR ce qu'il veut ; aucun n'est un filtrage local de la
/// liste complète. C'est ce qui permet à ``filter(id:title:)`` d'exister : sa
/// requête est une grammaire que seul le serveur sait interpréter.
///
/// > Note : le nom vient du client Android, où il désigne une *vue de tâches*.
/// > En SwiftUI le suffixe `View` annonce d'ordinaire un composant d'interface —
/// > ce n'en est pas un. Le nom est conservé pour que les deux clients se
/// > relisent l'un l'autre.
public enum TaskView: Hashable, Sendable {

    /// En retard ou dû aujourd'hui — la vue par défaut, comme sur le web.
    case today

    /// Tout ce qui est ouvert, quelle que soit l'échéance.
    case all

    /// Le projet marqué `isInbox` ; sa résolution appartient au dépôt.
    case inbox

    case project(id: String, title: String)

    case label(id: String, title: String)

    /// Vue enregistrée : la requête est exécutée par `GET /filters/{id}/tasks`.
    case filter(id: String, title: String)

    /// Libellé affiché dans la barre de titre.
    public var title: String {
        switch self {
        case .today: "Today"
        case .all: "All tasks"
        case .inbox: "Inbox"
        case let .project(_, title): title
        case let .label(_, title): title
        case let .filter(_, title): title
        }
    }

    /// L'identifiant de l'entrée sélectionnée, pour les trois cas qui en ont un.
    public var id: String? {
        switch self {
        case .today, .all, .inbox: nil
        case let .project(id, _): id
        case let .label(id, _): id
        case let .filter(id, _): id
        }
    }
}
