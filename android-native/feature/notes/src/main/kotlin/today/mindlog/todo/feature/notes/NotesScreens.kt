package today.mindlog.todo.feature.notes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import today.mindlog.todo.core.data.NotebooksState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotebooksScreen(
    onBack: () -> Unit,
    onOpenNotebook: (id: String, name: String) -> Unit,
    viewModel: NotesViewModel = hiltViewModel(),
) {
    val state by viewModel.notebooks.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notebooks") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { creating = true }) {
                Icon(Icons.Default.Add, contentDescription = "New notebook")
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (val current = state) {
                is NotebooksState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                is NotebooksState.Failed -> Retry(
                    message = "Could not load your notebooks.",
                    onRetry = { viewModel.refreshNotebooks() },
                    modifier = Modifier.align(Alignment.Center),
                )

                is NotebooksState.Ready -> if (current.notebooks.isEmpty()) {
                    Text(
                        "No notebooks yet.",
                        Modifier.align(Alignment.Center),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(current.notebooks, key = { it.id }) { notebook ->
                            ListItem(
                                headlineContent = { Text(notebook.name) },
                                trailingContent = {
                                    IconButton(onClick = { viewModel.deleteNotebook(notebook.id) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete notebook")
                                    }
                                },
                                modifier = Modifier.clickable { onOpenNotebook(notebook.id, notebook.name) },
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }

    if (creating) {
        NameDialog(
            title = "New notebook",
            confirm = "Create",
            onDismiss = { creating = false },
            onConfirm = { name ->
                viewModel.createNotebook(name)
                creating = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PagesScreen(
    notebookId: String,
    notebookName: String,
    onBack: () -> Unit,
    onOpenPage: (String) -> Unit,
    viewModel: NotesViewModel = hiltViewModel(),
) {
    val state by viewModel.pages.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }

    // Recharge à chaque entrée : rien ne notifie ce client qu'une page a changé
    // ailleurs, le flux d'événements du serveur ne couvre pas les notes.
    LaunchedEffect(notebookId) { viewModel.openNotebook(notebookId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(notebookName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { creating = true }) {
                Icon(Icons.Default.Add, contentDescription = "New page")
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.error != null -> Retry(
                    message = state.error!!,
                    onRetry = { viewModel.openNotebook(notebookId) },
                    modifier = Modifier.align(Alignment.Center),
                )

                state.pages.isEmpty() -> Text(
                    "No pages yet.",
                    Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.bodyLarge,
                )

                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(state.pages, key = { it.id }) { page ->
                        ListItem(
                            headlineContent = { Text(page.title.ifBlank { "Untitled" }) },
                            supportingContent = {
                                // `inRag` décide si la page nourrit la recherche
                                // sémantique : c'est une propriété que
                                // l'utilisateur règle, donc qu'il doit voir.
                                if (page.inRag) Text("In search index")
                            },
                            trailingContent = {
                                IconButton(onClick = { viewModel.deletePage(notebookId, page.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete page")
                                }
                            },
                            modifier = Modifier.clickable { onOpenPage(page.id) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (creating) {
        NameDialog(
            title = "New page",
            confirm = "Create",
            onDismiss = { creating = false },
            onConfirm = { title ->
                creating = false
                viewModel.createPage(notebookId, title) { onOpenPage(it) }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageEditorScreen(
    pageId: String,
    onBack: () -> Unit,
    viewModel: NotesViewModel = hiltViewModel(),
) {
    val state by viewModel.editor.collectAsStateWithLifecycle()

    LaunchedEffect(pageId) { viewModel.bindPage(pageId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.dirty) "Unsaved" else "Saved") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!state.readOnly) {
                        IconButton(onClick = viewModel::save, enabled = state.dirty && !state.saving) {
                            Icon(Icons.Default.Check, contentDescription = "Save")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                state.error != null -> Retry(
                    message = state.error!!,
                    onRetry = { viewModel.bindPage(pageId) },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )

                else -> {
                    if (state.readOnly) {
                        // Dire pourquoi, et pas seulement que c'est verrouillé :
                        // sans cette phrase, une page ouverte en lecture passe
                        // pour un bug.
                        Text(
                            "This page uses the block canvas, which only the web client can edit. " +
                                "Its content is shown here unchanged.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                    OutlinedTextField(
                        value = state.title,
                        onValueChange = viewModel::onTitleChange,
                        label = { Text("Title") },
                        singleLine = true,
                        readOnly = state.readOnly,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = state.markdown,
                        onValueChange = viewModel::onMarkdownChange,
                        label = { Text("Markdown") },
                        readOnly = state.readOnly,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )
                }
            }
        }
    }
}

// --- éléments partagés ----------------------------------------------------

@Composable
private fun Retry(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(message, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onRetry) { Text("Try again") }
    }
}

@Composable
private fun NameDialog(
    title: String,
    confirm: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(value) }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
