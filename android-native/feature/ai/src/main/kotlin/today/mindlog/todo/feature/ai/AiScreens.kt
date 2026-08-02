package today.mindlog.todo.feature.ai

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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import today.mindlog.todo.core.data.AiSettingsState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(onBack: () -> Unit, viewModel: AiViewModel = hiltViewModel()) {
    val state by viewModel.search.collectAsStateWithLifecycle()

    Scaffold(topBar = { BackBar("Search", onBack) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                label = { Text("Search tasks and notes") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { viewModel.runSearch() }),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = viewModel::runSearch,
                enabled = state.query.isNotBlank() && !state.running,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (state.running) "Searching…" else "Search") }
            Spacer(Modifier.height(16.dp))

            val results = state.results
            when {
                results == null -> Text(
                    "Semantic search over your tasks and note pages.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                else -> LazyColumn(Modifier.fillMaxSize()) {
                    // Chaque corpus dit son propre sort. Un échec affiché comme
                    // « aucun résultat » ferait conclure à tort que rien ne
                    // correspond.
                    item { CorpusHeader("Tasks", results.tasks.size, results.tasksFailed) }
                    items(results.tasks, key = { it.id }) { hit ->
                        ListItem(
                            headlineContent = { Text(hit.title) },
                            supportingContent = { Text("score ${"%.2f".format(hit.score)}") },
                        )
                        HorizontalDivider()
                    }
                    item { Spacer(Modifier.height(12.dp)) }
                    item { CorpusHeader("Notes", results.notes.size, results.notesFailed) }
                    items(results.notes, key = { it.id }) { hit ->
                        ListItem(
                            headlineContent = { Text(hit.title.ifBlank { "Untitled" }) },
                            supportingContent = { Text("score ${"%.2f".format(hit.score)}") },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun CorpusHeader(label: String, count: Int, failed: Boolean) {
    Text(
        text = when {
            failed -> "$label — unavailable"
            count == 0 -> "$label — no match"
            else -> "$label ($count)"
        },
        style = MaterialTheme.typography.labelLarge,
        color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AskScreen(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: AiViewModel = hiltViewModel(),
) {
    val state by viewModel.ask.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ask AI") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "AI settings")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        ) {
            OutlinedTextField(
                value = state.question,
                onValueChange = viewModel::onQuestionChange,
                label = { Text("Ask about your tasks") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = viewModel::runAsk,
                enabled = state.question.isNotBlank() && !state.running,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (state.running) "Thinking…" else "Ask") }

            state.error?.let {
                Spacer(Modifier.height(16.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            state.answer?.let { answer ->
                Spacer(Modifier.height(20.dp))
                Text(answer.answer, style = MaterialTheme.typography.bodyLarge)

                if (answer.sources.isNotEmpty()) {
                    Spacer(Modifier.height(20.dp))
                    // Les sources sont rendues telles quelles : c'est ce qui
                    // permet de vérifier la réponse au lieu de la croire.
                    Text("Based on", style = MaterialTheme.typography.labelLarge)
                    answer.sources.forEach { task ->
                        ListItem(headlineContent = { Text(task.title) })
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSettingsScreen(onBack: () -> Unit, viewModel: AiViewModel = hiltViewModel()) {
    val state by viewModel.settings.collectAsStateWithLifecycle()
    val key by viewModel.key.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.refreshSettings() }

    Scaffold(topBar = { BackBar("AI settings", onBack) }) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        ) {
            when (val current = state) {
                is AiSettingsState.Loading -> CircularProgressIndicator()
                is AiSettingsState.Failed -> Text(
                    "Could not load AI settings.",
                    color = MaterialTheme.colorScheme.error,
                )

                is AiSettingsState.Ready -> {
                    val settings = current.settings

                    if (settings.cloudHosted) {
                        // En mode hébergé, la clé est celle du service : offrir
                        // les champs serait promettre un réglage qui n'aurait
                        // aucun effet.
                        Text("Hosted mode", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "This deployment uses a shared key with a monthly allowance. " +
                                "Provider and model are set by the server.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        settings.credits?.let {
                            Spacer(Modifier.height(12.dp))
                            Text("${it.usedTokens} / ${it.limitTokens} tokens used")
                            Text(
                                "Resets ${it.resetAt.take(10)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        Text("Provider", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Row {
                            settings.providers.forEach { provider ->
                                FilterChip(
                                    selected = provider.id.value == settings.provider.value,
                                    onClick = { viewModel.setProvider(provider.id.value) },
                                    label = { Text(provider.label) },
                                    modifier = Modifier.padding(end = 8.dp),
                                )
                            }
                        }

                        Spacer(Modifier.height(20.dp))
                        Text("Model", style = MaterialTheme.typography.titleMedium)
                        Text(settings.model, style = MaterialTheme.typography.bodyMedium)

                        Spacer(Modifier.height(20.dp))
                        Text("API key", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (settings.hasKey) "A key is configured." else "No key configured.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = key.value,
                            onValueChange = viewModel::onKeyChange,
                            label = { Text("New key") },
                            singleLine = true,
                            // Le champ est masqué et jamais pré-rempli : le
                            // serveur ne rend pas la clé, et l'écran n'a pas à
                            // en garder une copie.
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        key.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        Spacer(Modifier.height(8.dp))
                        Row {
                            Button(
                                onClick = viewModel::saveKey,
                                enabled = key.value.isNotBlank() && !key.saving,
                            ) { Text("Save key") }
                            Spacer(Modifier.height(8.dp))
                            if (settings.hasKey) {
                                OutlinedButton(
                                    onClick = viewModel::deleteKey,
                                    modifier = Modifier.padding(start = 8.dp),
                                ) { Text("Forget key") }
                            }
                        }
                    }

                    current.usage?.let { usage ->
                        Spacer(Modifier.height(24.dp))
                        Text("Usage", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${usage.calls} calls · ${usage.totalTokens} tokens",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
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
