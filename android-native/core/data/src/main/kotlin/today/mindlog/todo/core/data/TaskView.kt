package today.mindlog.todo.core.data

/**
 * Ce que le tiroir de navigation sélectionne, et donc ce que la liste montre.
 *
 * Le nom est porté par la sélection plutôt que relu dans le dépôt au moment de
 * l'affichage : la barre de titre doit rester juste pendant le rechargement qui
 * suit un renommage, et une projection qui va rechercher son libellé dans une
 * liste en cours de rafraîchissement affiche un blanc.
 *
 * Chaque variante dit AU SERVEUR ce qu'elle veut ; aucune n'est un filtrage
 * local de la liste complète. C'est ce qui permet à `Filter` d'exister : sa
 * requête est une grammaire que seul le serveur sait interpréter.
 */
sealed interface TaskView {

    /** Libellé affiché dans la barre de titre. */
    val title: String

    /** En retard ou dû aujourd'hui — la vue par défaut, comme sur le web. */
    data object Today : TaskView {
        override val title: String get() = "Today"
    }

    /** Tout ce qui est ouvert, quelle que soit l'échéance. */
    data object All : TaskView {
        override val title: String get() = "All tasks"
    }

    /** Le projet marqué `isInbox` ; sa résolution appartient au dépôt. */
    data object Inbox : TaskView {
        override val title: String get() = "Inbox"
    }

    data class Project(val id: String, override val title: String) : TaskView

    data class Label(val id: String, override val title: String) : TaskView

    /** Vue enregistrée : la requête est exécutée par `GET /filters/{id}/tasks`. */
    data class Filter(val id: String, override val title: String) : TaskView
}
