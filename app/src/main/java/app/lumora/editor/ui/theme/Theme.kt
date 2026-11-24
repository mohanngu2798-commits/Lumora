package app.lumora.editor.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Amber = Color(0xFFFFB547)
val Sky = Color(0xFF7AD7F0)
val Ink = Color(0xFF0F1115)
val Panel = Color(0xFF181B22)

private val scheme = darkColorScheme(
    primary = Amber, onPrimary = Ink,
    secondary = Sky, onSecondary = Ink,
    background = Ink, onBackground = Color(0xFFE8EAF0),
    surface = Panel, onSurface = Color(0xFFE8EAF0),
    surfaceVariant = Color(0xFF232733), onSurfaceVariant = Color(0xFFA7ADBD),
)

/** Photo editors read best on a dark canvas, so the app is always dark. */
@Composable
fun LumoraTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = scheme, content = content)
