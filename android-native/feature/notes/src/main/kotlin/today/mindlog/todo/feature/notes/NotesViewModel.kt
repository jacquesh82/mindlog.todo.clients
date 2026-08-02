package today.mindlog.todo.feature.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import today.mindlog.todo.core.data.NotebooksState
import today.mindlog.todo.core.data.NotesRepository
import today.mindlog.todo.core.network.model.NotePageSummary
import javax.inject.Inject

/** Pages d'un carnet : chargées à l'ouverture, jamais conservées ailleurs. */
data class PagesState(
    val loading: Boolean = false,
    val pages: List<NotePageSummary> = emptyList(),
    val error: String? = null,
)

/**
 * Éditeur d'une page.
 *
 * `saved` distingue « rien à écrire » de « écrit » : sans lui, l'écran ne peut
 * pas dire à l'utilisateur si sa dernière frappe est partie, et c'est la seule
 * question qui compte quand on ferme une note.
 */
data class EditorState(
    val loading: Boolean = true,
    val title: String = "",
    val markdown: String = "",
    val readOnly: Boolean = false,
    val dirty: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class NotesViewModel @Inject constructor(
    private val repository: NotesRepository,
) : ViewModel() {

    val notebooks: StateFlow<NotebooksState> = repository.notebooks

    private val _pages = MutableStateFlow(PagesState())
    val pages: StateFlow<PagesState> = _pages.asStateFlow()

    private val _editor = MutableStateFlow(EditorState())
    val editor: StateFlow<EditorState> = _editor.asStateFlow()

    /** Document d'origine : c'est lui qui porte les boîtes à reconduire. */
    private var document: PageDocument? = null

    init {
        viewModelScope.launch { repository.refreshNotebooks() }
    }

    fun refreshNotebooks() = viewModelScope.launch { repository.refreshNotebooks() }

    fun createNotebook(name: String) = viewModelScope.launch {
        if (name.isNotBlank()) repository.createNotebook(name.trim())
    }

    fun deleteNotebook(id: String) = viewModelScope.launch { repository.deleteNotebook(id) }

    // --- pages ------------------------------------------------------------

    fun openNotebook(notebookId: String) {
        _pages.value = PagesState(loading = true)
        viewModelScope.launch {
            repository.pages(notebookId).fold(
                { _pages.value = PagesState(pages = it) },
                { _pages.value = PagesState(error = it.message ?: "Could not load pages") },
            )
        }
    }

    fun createPage(notebookId: String, title: String, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            // Une page neuve naît en `raw` : c'est le seul mode que cet écran
            // sait éditer, et l'ouvrir en canevas la rendrait aussitôt en
            // lecture seule.
            repository.createPage(notebookId, title = title.ifBlank { "Untitled" }, content = NEW_PAGE)
                .onSuccess {
                    openNotebook(notebookId)
                    onCreated(it.id)
                }
        }
    }

    fun deletePage(notebookId: String, pageId: String) = viewModelScope.launch {
        repository.deletePage(pageId).onSuccess { openNotebook(notebookId) }
    }

    // --- éditeur ----------------------------------------------------------

    fun openPage(pageId: String) {
        _editor.value = EditorState(loading = true)
        viewModelScope.launch {
            repository.page(pageId).fold(
                { page ->
                    val doc = parsePageContent(page.content)
                    document = doc
                    _editor.value = EditorState(
                        loading = false,
                        title = page.title,
                        markdown = doc.markdown,
                        // Le canevas de boîtes n'a pas d'éditeur natif : on
                        // l'ouvre en lecture plutôt que d'offrir une édition qui
                        // écraserait ce qu'elle ne sait pas afficher.
                        readOnly = doc.isBlocksCanvas,
                    )
                },
                { _editor.value = EditorState(loading = false, error = it.message ?: "Could not open page") },
            )
        }
    }

    fun onTitleChange(value: String) = _editor.update { it.copy(title = value, dirty = true) }
    fun onMarkdownChange(value: String) = _editor.update { it.copy(markdown = value, dirty = true) }

    /**
     * Sauvegarde explicite, déclenchée par l'utilisateur. Pas d'écriture à
     * chaque frappe : chaque PATCH renvoie la page entière, et une note de
     * plusieurs mégaoctets sur un lien mobile ne se réécrit pas au caractère.
     */
    fun save() {
        val state = _editor.value
        val doc = document ?: return
        if (state.readOnly || !state.dirty || state.saving) return
        _editor.update { it.copy(saving = true) }
        viewModelScope.launch {
            repository.savePage(
                id = currentPageId ?: return@launch,
                title = state.title,
                content = doc.withMarkdown(state.markdown),
            ).fold(
                { _editor.update { s -> s.copy(saving = false, dirty = false) } },
                { e -> _editor.update { s -> s.copy(saving = false, error = e.message ?: "Save failed") } },
            )
        }
    }

    private var currentPageId: String? = null

    fun bindPage(pageId: String) {
        currentPageId = pageId
        openPage(pageId)
    }

    private companion object {
        /** Document vide en mode texte, la forme que l'éditeur natif sait tenir. */
        const val NEW_PAGE = """{"mode":"raw","boxes":[],"markdown":""}"""
    }
}
