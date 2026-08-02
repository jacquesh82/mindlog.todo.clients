package today.mindlog.todo.feature.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import today.mindlog.todo.core.data.AuthRepository
import today.mindlog.todo.core.data.NavigationRepository
import today.mindlog.todo.core.data.NavigationState
import today.mindlog.todo.core.data.TaskRepository
import today.mindlog.todo.core.data.TaskView
import today.mindlog.todo.core.data.TasksState
import javax.inject.Inject

data class QuickAddState(
    val visible: Boolean = false,
    val text: String = "",
    val busy: Boolean = false,
)

@HiltViewModel
class TasksViewModel @Inject constructor(
    private val taskRepository: TaskRepository,
    private val navigationRepository: NavigationRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    val tasks: StateFlow<TasksState> = taskRepository.state

    /** Contenu du tiroir, et vue sélectionnée : les deux viennent des dépôts. */
    val navigation: StateFlow<NavigationState> = navigationRepository.state
    val view: StateFlow<TaskView> = taskRepository.view

    private val _quickAdd = MutableStateFlow(QuickAddState())
    val quickAdd: StateFlow<QuickAddState> = _quickAdd.asStateFlow()

    init {
        viewModelScope.launch { taskRepository.refresh() }
        viewModelScope.launch { navigationRepository.refresh() }
    }

    /** Sélection dans le tiroir : le dépôt recharge la liste pour cette vue. */
    fun select(view: TaskView) = taskRepository.select(view)

    fun refresh() = viewModelScope.launch { taskRepository.refresh() }

    /** Page suivante ; le dépôt ignore l'appel si une page est déjà en vol. */
    fun loadMore() = taskRepository.loadMore()

    fun setDone(id: String) = viewModelScope.launch { taskRepository.setDone(id, done = true) }

    fun showQuickAdd() = _quickAdd.update { QuickAddState(visible = true) }
    fun hideQuickAdd() = _quickAdd.update { QuickAddState() }
    fun onQuickAddTextChange(value: String) = _quickAdd.update { it.copy(text = value) }

    fun submitQuickAdd() {
        val text = _quickAdd.value.text.trim()
        if (text.isEmpty()) return
        _quickAdd.update { it.copy(busy = true) }
        viewModelScope.launch {
            taskRepository.quickAdd(text)
            _quickAdd.value = QuickAddState()
        }
    }

    fun signOut() = viewModelScope.launch { authRepository.logout() }
}
