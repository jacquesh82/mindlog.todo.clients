package today.mindlog.todo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import today.mindlog.todo.core.data.AuthRepository
import today.mindlog.todo.core.data.TaskRepository
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
    authRepository: AuthRepository,
    taskRepository: TaskRepository,
) : ViewModel() {

    /** null while the stored session is still being checked. */
    val signedIn: StateFlow<Boolean?> = authRepository.signedIn

    init {
        viewModelScope.launch { authRepository.restoreSession() }
        // Subscribes for the life of the process, not of a screen: a change
        // made elsewhere should already be on screen when the user comes back.
        taskRepository.startWatching()
    }
}
