package today.mindlog.todo.feature.ai

import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import today.mindlog.todo.core.data.AccountRepository
import today.mindlog.todo.core.data.AccountState
import today.mindlog.todo.core.network.model.ApiKeyCreated
import javax.inject.Inject

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val repository: AccountRepository,
) : ViewModel() {

    val account: StateFlow<AccountState> = repository.state

    /**
     * La clé fraîchement créée, le temps de l'afficher.
     *
     * Elle vit ICI et nulle part ailleurs : le dépôt ne la garde pas, le serveur
     * ne la rendra plus. [dismissCreatedKey] est donc une VRAIE suppression, pas
     * une fermeture de fenêtre.
     */
    private val _createdKey = MutableStateFlow<ApiKeyCreated?>(null)
    val createdKey: StateFlow<ApiKeyCreated?> = _createdKey.asStateFlow()

    private val _exportNote = MutableStateFlow<String?>(null)
    val exportNote: StateFlow<String?> = _exportNote.asStateFlow()

    fun refresh() = viewModelScope.launch { repository.refresh() }

    fun createApiKey(name: String) = viewModelScope.launch {
        repository.createApiKey(name).onSuccess { _createdKey.value = it }
    }

    fun dismissCreatedKey() {
        _createdKey.value = null
    }

    fun revokeApiKey(id: String) = viewModelScope.launch { repository.revokeApiKey(id) }

    /**
     * L'export part dans le presse-papier faute d'un choix de fichier dans ce
     * client. C'est un pis-aller assumé : une sauvegarde de compte peut être
     * volumineuse, et le presse-papier n'est pas fait pour ça — mais il vaut
     * mieux que pas d'export du tout, et l'écran le dit.
     */
    fun exportToClipboard(copy: (AnnotatedString) -> Unit) = viewModelScope.launch {
        _exportNote.value = "Exporting…"
        repository.export().fold(
            { json ->
                copy(AnnotatedString(json))
                _exportNote.value = "Copied ${json.length / 1024} KB to the clipboard."
            },
            { _exportNote.value = "Export failed: ${it.message ?: "unknown error"}" },
        )
    }
}
