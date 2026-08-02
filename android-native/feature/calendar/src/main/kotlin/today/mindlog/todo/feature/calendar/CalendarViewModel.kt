package today.mindlog.todo.feature.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import today.mindlog.todo.core.data.CalendarRepository
import today.mindlog.todo.core.data.CalendarState
import today.mindlog.todo.core.network.model.DashboardStats
import javax.inject.Inject

data class DashboardUi(
    val loading: Boolean = true,
    val stats: DashboardStats? = null,
    val error: String? = null,
)

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val repository: CalendarRepository,
) : ViewModel() {

    val calendar: StateFlow<CalendarState> = repository.state

    private val _dashboard = MutableStateFlow(DashboardUi())
    val dashboard: StateFlow<DashboardUi> = _dashboard.asStateFlow()

    fun refreshCalendar() = viewModelScope.launch { repository.refresh() }

    /**
     * Le karma est DÉJÀ dans le tableau de bord : le demander séparément ferait
     * un second aller-retour pour une valeur qu'on vient de recevoir.
     */
    fun refreshDashboard() {
        _dashboard.value = DashboardUi(loading = true)
        viewModelScope.launch {
            repository.dashboard().fold(
                { _dashboard.value = DashboardUi(loading = false, stats = it) },
                { _dashboard.value = DashboardUi(loading = false, error = it.message ?: "Could not load stats") },
            )
        }
    }

    fun addSource(name: String, url: String) = viewModelScope.launch {
        if (name.isNotBlank() && url.isNotBlank()) repository.addSource(name.trim(), url.trim())
    }

    fun deleteSource(id: String) = viewModelScope.launch { repository.deleteSource(id) }
}
