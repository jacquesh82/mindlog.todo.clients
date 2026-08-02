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
import kotlinx.serialization.Serializable
import today.mindlog.todo.feature.auth.LoginScreen
import today.mindlog.todo.feature.tasks.TasksScreen

// Type-safe routes rather than strings: a renamed destination becomes a
// compile error instead of a crash at the tap.
@Serializable private data object Splash
@Serializable private data object Login
@Serializable private data object Tasks

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
            composable<Tasks> { TasksScreen() }
        }
    }
}
