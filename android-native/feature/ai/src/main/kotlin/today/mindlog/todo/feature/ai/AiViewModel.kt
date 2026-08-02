package today.mindlog.todo.feature.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import today.mindlog.todo.core.data.AiRepository
import today.mindlog.todo.core.data.AiSettingsState
import today.mindlog.todo.core.data.SearchResults
import today.mindlog.todo.core.network.model.AskResult
import javax.inject.Inject

data class SearchUi(
    val query: String = "",
    val running: Boolean = false,
    /** `null` tant qu'aucune recherche n'a été lancée : « vide » ≠ « pas encore ». */
    val results: SearchResults? = null,
)

data class AskUi(
    val question: String = "",
    val running: Boolean = false,
    val answer: AskResult? = null,
    val error: String? = null,
)

data class KeyUi(
    val value: String = "",
    val saving: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class AiViewModel @Inject constructor(
    private val repository: AiRepository,
) : ViewModel() {

    val settings: StateFlow<AiSettingsState> = repository.settings

    private val _search = MutableStateFlow(SearchUi())
    val search: StateFlow<SearchUi> = _search.asStateFlow()

    private val _ask = MutableStateFlow(AskUi())
    val ask: StateFlow<AskUi> = _ask.asStateFlow()

    private val _key = MutableStateFlow(KeyUi())
    val key: StateFlow<KeyUi> = _key.asStateFlow()

    fun onQueryChange(value: String) = _search.update { it.copy(query = value) }

    /** Lancée à la demande, jamais à la frappe : chaque recherche est un appel payant. */
    fun runSearch() {
        val query = _search.value.query.trim()
        if (query.isEmpty() || _search.value.running) return
        _search.update { it.copy(running = true) }
        viewModelScope.launch {
            val results = repository.search(query)
            _search.update { it.copy(running = false, results = results) }
        }
    }

    fun onQuestionChange(value: String) = _ask.update { it.copy(question = value) }

    fun runAsk() {
        val question = _ask.value.question.trim()
        if (question.isEmpty() || _ask.value.running) return
        _ask.update { it.copy(running = true, error = null) }
        viewModelScope.launch {
            repository.ask(question).fold(
                { result -> _ask.update { it.copy(running = false, answer = result) } },
                { e -> _ask.update { it.copy(running = false, error = e.message ?: "The request failed") } },
            )
        }
    }

    // --- réglages ---------------------------------------------------------

    fun refreshSettings() = viewModelScope.launch { repository.refreshSettings() }

    fun onKeyChange(value: String) = _key.update { it.copy(value = value) }

    /**
     * La clé est effacée de l'état DÈS l'envoi réussi : la garder pour
     * réafficher le champ ferait vivre un secret dans la mémoire de l'écran
     * sans qu'aucune fonction n'en dépende.
     */
    fun saveKey() {
        val value = _key.value.value.trim()
        if (value.isEmpty() || _key.value.saving) return
        _key.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            repository.updateSettings(apiKey = value).fold(
                { _key.value = KeyUi() },
                { e -> _key.update { it.copy(saving = false, error = e.message ?: "Could not save the key") } },
            )
        }
    }

    fun deleteKey() = viewModelScope.launch { repository.deleteKey() }

    fun setProvider(provider: String) = viewModelScope.launch {
        repository.updateSettings(provider = provider)
    }

    fun setModel(model: String) = viewModelScope.launch { repository.updateSettings(model = model) }
}
