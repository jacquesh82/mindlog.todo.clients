package today.mindlog.todo

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dagger.hilt.android.AndroidEntryPoint
import today.mindlog.todo.core.data.OAuthCallbackBus
import today.mindlog.todo.core.designsystem.theme.MindlogTodoTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var oAuthCallbacks: OAuthCallbackBus

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // The browser may have started us cold rather than resumed us.
        handleDeepLink(intent)

        setContent {
            MindlogTodoTheme {
                MindlogTodoApp()
            }
        }
    }

    /**
     * The activity is `singleTask`, so the mindlog id return leg lands here
     * rather than starting a second copy of the app.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: Intent?) {
        val uri = intent?.data ?: return
        if (intent.action != Intent.ACTION_VIEW) return
        oAuthCallbacks.onDeepLink(uri)
    }
}
