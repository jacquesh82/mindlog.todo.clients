package today.mindlog.todo.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Tonal ramp grown from the two brand anchors the web client already uses
// (`web/src/styles.css`): #8F621E in light, #EDBA6E in dark. Both sit at hue
// ~36°, so the ramp is one hue at varying tone rather than two palettes.

private val Amber10 = Color(0xFF2E1C00)
private val Amber20 = Color(0xFF4A2F00)
private val Amber30 = Color(0xFF6A4600)
private val Amber40 = Color(0xFF8F621E) // brand, light
private val Amber60 = Color(0xFFC08F3F)
private val Amber80 = Color(0xFFEDBA6E) // brand, dark
private val Amber90 = Color(0xFFFFDDB0)
private val Amber95 = Color(0xFFFFEEDC)

// A muted companion for secondary surfaces — same hue, low chroma, so it reads
// as a neutral without going grey.
private val Neutral10 = Color(0xFF1F1B16)
private val Neutral20 = Color(0xFF35302A)
private val Neutral30 = Color(0xFF4C463F)
private val Neutral50 = Color(0xFF7E7667)
private val Neutral80 = Color(0xFFD1C5B4)
private val Neutral90 = Color(0xFFEDE0CF)
private val Neutral95 = Color(0xFFFBEFDE)
private val Neutral99 = Color(0xFFFFFBFF)

private val Red40 = Color(0xFFBA1A1A)
private val Red80 = Color(0xFFFFB4AB)
private val Red90 = Color(0xFFFFDAD6)
private val Red20 = Color(0xFF690005)

internal val LightColorScheme = lightColorScheme(
    primary = Amber40,
    // White on #8F621E is 5.3:1 — above the 4.5:1 body-text threshold.
    onPrimary = Color.White,
    primaryContainer = Amber90,
    onPrimaryContainer = Amber10,
    secondary = Neutral30,
    onSecondary = Color.White,
    secondaryContainer = Neutral90,
    onSecondaryContainer = Neutral10,
    tertiary = Amber30,
    onTertiary = Color.White,
    tertiaryContainer = Amber95,
    onTertiaryContainer = Amber10,
    error = Red40,
    onError = Color.White,
    errorContainer = Red90,
    onErrorContainer = Red20,
    background = Neutral99,
    onBackground = Neutral10,
    surface = Neutral99,
    onSurface = Neutral10,
    surfaceVariant = Neutral90,
    onSurfaceVariant = Neutral30,
    outline = Neutral50,
    outlineVariant = Neutral80,
)

internal val DarkColorScheme = darkColorScheme(
    primary = Amber80,
    // Counter-intuitive but correct: the dark theme's primary is the *light*
    // amber, so what sits on it must be dark (7.4:1). The web client reaches
    // the same conclusion by luminance in `web/src/theme.ts`, where the token
    // is called --color-brand-ink.
    onPrimary = Amber20,
    primaryContainer = Amber30,
    onPrimaryContainer = Amber90,
    secondary = Neutral80,
    onSecondary = Neutral20,
    secondaryContainer = Neutral30,
    onSecondaryContainer = Neutral90,
    tertiary = Amber60,
    onTertiary = Amber10,
    tertiaryContainer = Amber20,
    onTertiaryContainer = Amber90,
    error = Red80,
    onError = Red20,
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Red90,
    background = Neutral10,
    onBackground = Neutral90,
    surface = Neutral10,
    onSurface = Neutral90,
    surfaceVariant = Neutral30,
    onSurfaceVariant = Neutral80,
    outline = Neutral50,
    outlineVariant = Neutral30,
)

/** Priority accents, matching the web client's task rows. 1 = urgent. */
val PriorityColors: Map<Int, Color> = mapOf(
    1 to Color(0xFFD1453B),
    2 to Color(0xFFEB8909),
    3 to Color(0xFF246FE0),
    4 to Color(0xFF808080),
)
