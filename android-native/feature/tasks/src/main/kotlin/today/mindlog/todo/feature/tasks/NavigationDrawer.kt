package today.mindlog.todo.feature.tasks

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import today.mindlog.todo.core.data.DrawerCounts
import today.mindlog.todo.core.data.NavigationState
import today.mindlog.todo.core.data.TaskView
import androidx.compose.foundation.background
import androidx.compose.material.icons.filled.Circle

/**
 * Le tiroir de navigation : vues d'ensemble, projets, étiquettes, filtres.
 *
 * Une seule `LazyColumn` plutôt qu'une colonne défilante de sous-listes : le
 * nombre de projets n'est pas borné, et une `Column` verticale les composerait
 * tous, y compris ceux qu'on ne voit jamais.
 */
@Composable
fun NavigationDrawerContent(
    state: NavigationState,
    selected: TaskView,
    onSelect: (TaskView) -> Unit,
    onOpenNotes: () -> Unit = {},
    onOpenSearch: () -> Unit = {},
    onOpenAsk: () -> Unit = {},
) {
    ModalDrawerSheet {
        LazyColumn(contentPadding = PaddingValues(vertical = 12.dp)) {
            item {
                DrawerEntry(
                    label = TaskView.Today.title,
                    icon = Icons.Default.CalendarToday,
                    count = (state as? NavigationState.Ready)?.counts?.today,
                    selected = selected is TaskView.Today,
                    onClick = { onSelect(TaskView.Today) },
                )
            }
            item {
                DrawerEntry(
                    label = TaskView.Inbox.title,
                    icon = Icons.Default.Inbox,
                    count = (state as? NavigationState.Ready)?.counts?.inbox,
                    selected = selected is TaskView.Inbox,
                    onClick = { onSelect(TaskView.Inbox) },
                )
            }
            item {
                DrawerEntry(
                    label = TaskView.All.title,
                    icon = Icons.Default.List,
                    count = null,
                    selected = selected is TaskView.All,
                    onClick = { onSelect(TaskView.All) },
                )
            }

            // Les notes ne sont pas une vue de tâches : l'entrée quitte cet
            // écran au lieu de changer la sélection, d'où l'absence d'état
            // « sélectionné ».
            item {
                DrawerEntry(
                    label = "Notes",
                    icon = Icons.AutoMirrored.Filled.MenuBook,
                    count = null,
                    selected = false,
                    onClick = onOpenNotes,
                )
            }

            item {
                DrawerEntry(
                    label = "Search",
                    icon = Icons.Default.Search,
                    count = null,
                    selected = false,
                    onClick = onOpenSearch,
                )
            }
            item {
                DrawerEntry(
                    label = "Ask AI",
                    icon = Icons.Default.AutoAwesome,
                    count = null,
                    selected = false,
                    onClick = onOpenAsk,
                )
            }

            when (state) {
                // Le tiroir reste utilisable pendant le chargement et même en
                // cas d'échec : les trois vues d'ensemble ci-dessus ne dépendent
                // d'aucune des listes, et les priver de leur badge vaut mieux
                // que de rendre la navigation inaccessible.
                is NavigationState.Loading -> Unit

                is NavigationState.Failed -> item {
                    Text(
                        "Could not load projects.",
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                is NavigationState.Ready -> {
                    // La boîte de réception a déjà sa propre entrée en haut ;
                    // la relister parmi les projets la ferait apparaître deux
                    // fois, sous deux noms différents.
                    val projects = state.projects.filterNot { it.isInbox }

                    section("Projects", projects.isNotEmpty())
                    items(projects, key = { it.id }) { project ->
                        DrawerEntry(
                            label = project.name,
                            icon = Icons.Default.Circle,
                            tint = project.color?.let(::parseHexColor),
                            count = state.counts.byProject[project.id],
                            selected = (selected as? TaskView.Project)?.id == project.id,
                            onClick = { onSelect(TaskView.Project(project.id, project.name)) },
                        )
                    }

                    section("Labels", state.labels.isNotEmpty())
                    items(state.labels, key = { it.id }) { label ->
                        DrawerEntry(
                            label = label.name,
                            icon = Icons.AutoMirrored.Filled.Label,
                            tint = label.color?.let(::parseHexColor),
                            count = state.counts.byLabel[label.id],
                            selected = (selected as? TaskView.Label)?.id == label.id,
                            onClick = { onSelect(TaskView.Label(label.id, label.name)) },
                        )
                    }

                    section("Filters", state.filters.isNotEmpty())
                    items(state.filters, key = { it.id }) { filter ->
                        DrawerEntry(
                            label = filter.name,
                            icon = Icons.Default.FilterList,
                            tint = filter.color?.let(::parseHexColor),
                            // Pas de compteur : un filtre est une requête que
                            // seul le serveur sait exécuter, le compter d'ici
                            // demanderait une requête par filtre.
                            count = null,
                            selected = (selected as? TaskView.Filter)?.id == filter.id,
                            onClick = { onSelect(TaskView.Filter(filter.id, filter.name)) },
                        )
                    }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.section(title: String, visible: Boolean) {
    if (!visible) return
    item {
        HorizontalDivider(Modifier.padding(horizontal = 28.dp, vertical = 8.dp))
        Text(
            title.uppercase(),
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DrawerEntry(
    label: String,
    icon: ImageVector,
    count: Int?,
    selected: Boolean,
    onClick: () -> Unit,
    tint: Color? = null,
) {
    NavigationDrawerItem(
        icon = {
            if (tint != null) {
                // Un projet ou une étiquette porte une couleur : la pastille la
                // montre, plutôt qu'une icône générique teintée qui la rendrait
                // illisible sur les teintes claires.
                Box(Modifier.size(12.dp).clip(CircleShape).background(tint))
            } else {
                Icon(icon, contentDescription = null)
            }
        },
        label = { Text(label, maxLines = 1) },
        badge = { count?.takeIf { it > 0 }?.let { Text(it.toString()) } },
        selected = selected,
        onClick = onClick,
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
    )
}

/**
 * `#rrggbb` → couleur Compose. Le serveur valide déjà ce format, mais une
 * valeur inattendue ne doit pas faire tomber le tiroir : on retombe alors sur
 * l'icône générique.
 */
private fun parseHexColor(hex: String): Color? = runCatching {
    Color(android.graphics.Color.parseColor(hex))
}.getOrNull()
