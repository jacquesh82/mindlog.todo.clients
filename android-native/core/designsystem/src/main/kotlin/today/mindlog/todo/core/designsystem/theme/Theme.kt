package today.mindlog.todo.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.isSystemInDarkTheme

/**
 * The todo palette, fixed — no dynamic colour.
 *
 * Material You would swap the amber for whatever the user's wallpaper
 * suggests, and the amber *is* the product's identity: it is the same colour
 * the web client and the marketing site use. Same call the archived talk client
 * made for its own palette.
 */
@Composable
fun MindlogTodoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = MindlogTypography,
        shapes = MindlogShapes,
        content = content,
    )
}
