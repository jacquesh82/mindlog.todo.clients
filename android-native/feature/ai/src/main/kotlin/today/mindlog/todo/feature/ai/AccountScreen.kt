package today.mindlog.todo.feature.ai

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import today.mindlog.todo.core.data.AccountState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(onBack: () -> Unit, viewModel: AccountViewModel = hiltViewModel()) {
    val state by viewModel.account.collectAsStateWithLifecycle()
    val created by viewModel.createdKey.collectAsStateWithLifecycle()
    var naming by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(Unit) { viewModel.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Account") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        ) {
            when (val current = state) {
                is AccountState.Loading -> CircularProgressIndicator()
                is AccountState.Failed -> Text(
                    "Could not load your account.",
                    color = MaterialTheme.colorScheme.error,
                )

                is AccountState.Ready -> {
                    Text(current.user.email, style = MaterialTheme.typography.titleMedium)
                    current.user.displayName?.let { Text(it) }

                    current.storage?.let { storage ->
                        Spacer(Modifier.height(20.dp))
                        Text("Storage", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${storage.totalBytes / 1024 / 1024} MB used · " +
                                "notes ${storage.notesBytes / 1024 / 1024} MB · " +
                                "attachments ${storage.attachmentsBytes / 1024 / 1024} MB",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (storage.quota > 0) {
                            LinearProgressIndicator(
                                progress = {
                                    (storage.notesBytes.toFloat() / storage.quota).coerceIn(0f, 1f)
                                },
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            )
                        }
                    }

                    Spacer(Modifier.height(24.dp))
                    Row {
                        Text("API keys", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = { naming = true }) { Text("New key") }
                    }
                    if (current.apiKeys.isEmpty()) {
                        Text(
                            "No key yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        current.apiKeys.forEach { key ->
                            ListItem(
                                headlineContent = { Text(key.name ?: "Unnamed key") },
                                // Seul le préfixe existe côté serveur après la
                                // création : afficher autre chose supposerait
                                // d'avoir gardé le secret quelque part.
                                supportingContent = {
                                    Text(
                                        "${key.prefix}… · " +
                                            (key.lastUsedAt?.let { "last used ${it.take(10)}" }
                                                ?: "never used"),
                                    )
                                },
                                trailingContent = {
                                    IconButton(onClick = { viewModel.revokeApiKey(key.id) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Revoke key")
                                    }
                                },
                            )
                            HorizontalDivider()
                        }
                    }

                    Spacer(Modifier.height(24.dp))
                    Text("Export", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "A full JSON copy of your account.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { viewModel.exportToClipboard(clipboard::setText) }) {
                        Text("Copy export to clipboard")
                    }
                    viewModel.exportNote.collectAsStateWithLifecycle().value?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }

    if (naming) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { naming = false },
            title = { Text("New API key") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.createApiKey(name)
                    naming = false
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { naming = false }) { Text("Cancel") } },
        )
    }

    // Le secret ne revient jamais : cette boîte est le SEUL endroit où il est
    // lisible. Elle ne se ferme donc pas d'un tap à côté — seulement d'un geste
    // délibéré, une fois la clé copiée.
    created?.let { key ->
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Key created") },
            text = {
                Column {
                    Text(
                        "Copy it now — it is shown once and cannot be retrieved later.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(key.secret, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(key.secret))
                    viewModel.dismissCreatedKey()
                }) { Text("Copy and close") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissCreatedKey) { Text("I saved it") }
            },
        )
    }
}
