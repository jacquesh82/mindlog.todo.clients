package today.mindlog.todo.feature.calendar

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import today.mindlog.todo.core.data.CalendarState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(onBack: () -> Unit, viewModel: CalendarViewModel = hiltViewModel()) {
    val state by viewModel.calendar.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.refreshCalendar() }

    Scaffold(
        topBar = { BackBar("Calendar", onBack) },
        floatingActionButton = {
            FloatingActionButton(onClick = { adding = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add a calendar feed")
            }
        },
    ) { padding ->
        when (val current = state) {
            is CalendarState.Loading -> Box(padding) { CircularProgressIndicator() }
            is CalendarState.Failed -> Box(padding) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Could not load your calendars.")
                    TextButton(onClick = { viewModel.refreshCalendar() }) { Text("Try again") }
                }
            }

            is CalendarState.Ready -> LazyColumn(Modifier.padding(padding).fillMaxSize()) {
                item {
                    SectionTitle("Upcoming — next 28 days")
                }
                if (current.events.isEmpty()) {
                    item {
                        Text(
                            "Nothing scheduled.",
                            Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    items(current.events, key = { it.uid }) { event ->
                        ListItem(
                            headlineContent = { Text(event.summary) },
                            supportingContent = {
                                // Une entrée « journée entière » n'a pas d'heure
                                // à montrer : afficher minuit ferait croire à un
                                // rendez-vous nocturne.
                                Text(
                                    if (event.allDay) event.start.take(10)
                                    else event.start.replace('T', ' ').take(16),
                                )
                            },
                        )
                        HorizontalDivider()
                    }
                }

                item { SectionTitle("Subscriptions") }
                if (current.sources.isEmpty()) {
                    item {
                        Text(
                            "No iCal feed yet.",
                            Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    items(current.sources, key = { it.id }) { source ->
                        ListItem(
                            headlineContent = { Text(source.name) },
                            supportingContent = {
                                Text(
                                    source.lastSyncedAt?.let { "Synced ${it.take(10)}" }
                                        ?: "Never synced",
                                )
                            },
                            trailingContent = {
                                IconButton(onClick = { viewModel.deleteSource(source.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Remove feed")
                                }
                            },
                        )
                        HorizontalDivider()
                    }
                }

                current.connection?.let { connection ->
                    item { SectionTitle("mindlog id") }
                    item {
                        ListItem(
                            headlineContent = {
                                Text(if (connection.connected) "Account linked" else "Not linked")
                            },
                            supportingContent = {
                                // Être rattaché ne suffit pas : sans le droit
                                // agenda, aucun événement n'en vient, et le dire
                                // évite de chercher une panne ailleurs.
                                Text(
                                    if (connection.agendaGranted) "Agenda access granted"
                                    else "No agenda access — its events are not shown",
                                )
                            },
                        )
                    }
                }
            }
        }
    }

    if (adding) {
        var name by remember { mutableStateOf("") }
        var url by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text("Add an iCal feed") },
            text = {
                Column {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("https://…/calendar.ics") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.addSource(name, url)
                    adding = false
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(onBack: () -> Unit, viewModel: CalendarViewModel = hiltViewModel()) {
    val state by viewModel.dashboard.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.refreshDashboard() }

    Scaffold(topBar = { BackBar("Dashboard", onBack) }) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        ) {
            when {
                state.loading -> CircularProgressIndicator()
                state.error != null -> Text(state.error!!, color = MaterialTheme.colorScheme.error)
                else -> state.stats?.let { stats ->
                    // Le karma vient du tableau de bord lui-même : pas de second
                    // appel pour une valeur déjà reçue.
                    stats.karma?.let { karma ->
                        Text("Karma", style = MaterialTheme.typography.titleMedium)
                        Text("${karma.points} points · ${karma.level}")
                        karma.nextLevel?.let { next ->
                            Text(
                                "${karma.pointsToNext} to ${next}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text("Streak: ${karma.streakDays} days")
                        Spacer(Modifier.height(20.dp))
                    }

                    Text("Tasks", style = MaterialTheme.typography.titleMedium)
                    Stat("Active", stats.tasks.active)
                    Stat("Overdue", stats.tasks.overdue)
                    Stat("Due today", stats.tasks.dueToday)
                    Stat("Completed this week", stats.tasks.completedThisWeek)
                    Spacer(Modifier.height(8.dp))
                    Text("Completion ${stats.tasks.completionRate.toInt()}%")
                    LinearProgressIndicator(
                        progress = { stats.tasks.completionRate.toFloat() / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(Modifier.height(20.dp))
                    Text("Notes", style = MaterialTheme.typography.titleMedium)
                    Stat("Notebooks", stats.notes.notebooks)
                    Stat("Pages", stats.notes.pages)
                    Stat("Indexed for search", stats.notes.ragPages)
                    Text(
                        "${stats.notes.storageBytes / 1024 / 1024} MB of " +
                            "${stats.notes.storageQuota / 1024 / 1024} MB used",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    if (stats.completedTrend.isNotEmpty()) {
                        Spacer(Modifier.height(20.dp))
                        Text("Last 14 days", style = MaterialTheme.typography.titleMedium)
                        // Barres en texte plutôt qu'un graphique : la tendance se
                        // lit, et une dépendance de tracé pour quatorze valeurs
                        // serait payer cher une courbe.
                        val peak = stats.completedTrend.maxOf { it.count }.coerceAtLeast(1)
                        stats.completedTrend.forEach { point ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                                Text(
                                    point.date.takeLast(5),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(end = 8.dp),
                                )
                                Text(
                                    "█".repeat((point.count * 12 / peak).coerceAtLeast(if (point.count > 0) 1 else 0)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    if (point.count > 0) " ${point.count}" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: Int) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text("$value", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text.uppercase(),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun Box(padding: androidx.compose.foundation.layout.PaddingValues, content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Box(
        Modifier.padding(padding).fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackBar(title: String, onBack: () -> Unit) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
    )
}
