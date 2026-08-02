package today.mindlog.todo

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import kotlinx.serialization.Serializable
import today.mindlog.todo.feature.auth.LoginScreen
import today.mindlog.todo.feature.ai.AccountScreen
import today.mindlog.todo.feature.ai.AiSettingsScreen
import today.mindlog.todo.feature.calendar.CalendarScreen
import today.mindlog.todo.feature.calendar.DashboardScreen
import today.mindlog.todo.feature.ai.AskScreen
import today.mindlog.todo.feature.ai.SearchScreen
import today.mindlog.todo.feature.notes.NotebooksScreen
import today.mindlog.todo.feature.notes.PageEditorScreen
import today.mindlog.todo.feature.notes.PagesScreen
import today.mindlog.todo.feature.tasks.TasksScreen

// Type-safe routes rather than strings: a renamed destination becomes a
// compile error instead of a crash at the tap.
@Serializable private data object Splash
@Serializable private data object Login
@Serializable private data object Tasks
@Serializable private data object Notebooks
@Serializable private data class Pages(val notebookId: String, val notebookName: String)
@Serializable private data class PageEditor(val pageId: String)
@Serializable private data object Search
@Serializable private data object Ask
@Serializable private data object AiSettings
@Serializable private data object Calendar
@Serializable private data object Dashboard
@Serializable private data object Account

@Composable
fun MindlogTodoApp(viewModel: AppViewModel = hiltViewModel()) {
    val signedIn by viewModel.signedIn.collectAsStateWithLifecycle()
    val navController = rememberNavController()

    // A guard rather than a nested graph. With three destinations, a graph
    // would be more machinery than the problem has.
    LaunchedEffect(signedIn) {
        when (signedIn) {
            null -> Unit // still restoring
            true -> navController.navigate(Tasks) { popUpTo(Splash) { inclusive = true } }
            false -> navController.navigate(Login) { popUpTo(Splash) { inclusive = true } }
        }
    }

    Surface(Modifier.fillMaxSize()) {
        NavHost(navController = navController, startDestination = Splash) {
            composable<Splash> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            composable<Login> { LoginScreen() }
            composable<Tasks> {
                TasksScreen(
                    onOpenNotes = { navController.navigate(Notebooks) },
                    onOpenSearch = { navController.navigate(Search) },
                    onOpenAsk = { navController.navigate(Ask) },
                    onOpenCalendar = { navController.navigate(Calendar) },
                    onOpenDashboard = { navController.navigate(Dashboard) },
                    onOpenAccount = { navController.navigate(Account) },
                )
            }
            composable<Search> { SearchScreen(onBack = { navController.popBackStack() }) }
            composable<Ask> {
                AskScreen(
                    onBack = { navController.popBackStack() },
                    onOpenSettings = { navController.navigate(AiSettings) },
                )
            }
            composable<AiSettings> { AiSettingsScreen(onBack = { navController.popBackStack() }) }
            composable<Calendar> { CalendarScreen(onBack = { navController.popBackStack() }) }
            composable<Dashboard> { DashboardScreen(onBack = { navController.popBackStack() }) }
            composable<Account> { AccountScreen(onBack = { navController.popBackStack() }) }
            composable<Notebooks> {
                NotebooksScreen(
                    onBack = { navController.popBackStack() },
                    onOpenNotebook = { id, name -> navController.navigate(Pages(id, name)) },
                )
            }
            composable<Pages> { entry ->
                val route = entry.toRoute<Pages>()
                PagesScreen(
                    notebookId = route.notebookId,
                    notebookName = route.notebookName,
                    onBack = { navController.popBackStack() },
                    onOpenPage = { navController.navigate(PageEditor(it)) },
                )
            }
            composable<PageEditor> { entry ->
                PageEditorScreen(
                    pageId = entry.toRoute<PageEditor>().pageId,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}
