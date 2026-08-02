package today.mindlog.todo.feature.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material.icons.filled.Menu
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import today.mindlog.todo.core.data.TasksState
import today.mindlog.todo.core.designsystem.theme.PriorityColors
import today.mindlog.todo.core.network.model.Task

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(viewModel: TasksViewModel = hiltViewModel()) {
    val state by viewModel.tasks.collectAsStateWithLifecycle()
    val quickAdd by viewModel.quickAdd.collectAsStateWithLifecycle()
    val navigation by viewModel.navigation.collectAsStateWithLifecycle()
    val view by viewModel.view.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            NavigationDrawerContent(
                state = navigation,
                selected = view,
                onSelect = { selection ->
                    viewModel.select(selection)
                    // Refermer fait partie de la sélection : la liste est
                    // derrière le tiroir, la laisser ouverte cacherait ce qu'on
                    // vient de demander.
                    scope.launch { drawerState.close() }
                },
            )
        },
    ) {
    Scaffold(
        topBar = {
            TopAppBar(
                // Le titre EST la vue sélectionnée : c'est le seul repère qui
                // dise ce que la liste montre une fois le tiroir refermé.
                title = { Text(view.title) },
                navigationIcon = {
                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                        Icon(Icons.Default.Menu, contentDescription = "Open navigation")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::signOut) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Sign out")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = viewModel::showQuickAdd) {
                Icon(Icons.Default.Add, contentDescription = "Add a task")
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (val current = state) {
                is TasksState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))

                is TasksState.Failed -> Column(
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Could not load your tasks.", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { viewModel.refresh() }) { Text("Try again") }
                }

                is TasksState.Ready ->
                    if (current.tasks.isEmpty()) {
                        Text(
                            "Nothing in ${view.title}.",
                            modifier = Modifier.align(Alignment.Center),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    } else {
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(current.tasks, key = { it.id }) { task ->
                                TaskRow(task, onComplete = { viewModel.setDone(task.id) })
                                HorizontalDivider()
                            }
                        }
                    }
            }
        }

        if (quickAdd.visible) {
            ModalBottomSheet(
                onDismissRequest = viewModel::hideQuickAdd,
                sheetState = sheetState,
            ) {
                QuickAddSheet(quickAdd, viewModel)
            }
        }
    }
    }
}

@Composable
private fun TaskRow(task: Task, onComplete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Ring coloured by priority, mirroring the web client's task rows so
        // the two do not develop separate visual vocabularies. 1 is urgent.
        IconButton(onClick = onComplete, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = Icons.Default.RadioButtonUnchecked,
                contentDescription = "Mark done",
                tint = PriorityColors[task.priority] ?: MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(22.dp),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(task.title, style = MaterialTheme.typography.bodyLarge)
            task.dueDate?.let {
                Text(
                    it.take(10),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun QuickAddSheet(state: QuickAddState, viewModel: TasksViewModel) {
    Column(Modifier.fillMaxWidth().padding(24.dp)) {
        OutlinedTextField(
            value = state.text,
            onValueChange = viewModel::onQuickAddTextChange,
            // One field, not a form: the server parses dates, #project, @label
            // and p1–p4 out of the text, so duplicating those rules in Kotlin
            // would only create a second, divergent parser.
            label = { Text("Buy bread tomorrow p1") },
            singleLine = true,
            enabled = !state.busy,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { viewModel.submitQuickAdd() }),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = viewModel::submitQuickAdd,
            enabled = !state.busy && state.text.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Add") }
        Spacer(Modifier.height(16.dp))
    }
}
