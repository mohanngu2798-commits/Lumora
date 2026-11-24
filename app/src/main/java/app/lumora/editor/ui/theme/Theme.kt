package app.lumora.editor.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// "Vannamayam" means colourful in Tamil: the palette borrows from kolam powders and festival lights.
val Magenta = Color(0xFFFF3D7F)
val Saffron = Color(0xFFFF8A3D)
val Turmeric = Color(0xFFFFD23F)
val Peacock = Color(0xFF2EC4B6)
val Violet = Color(0xFF7B5CFF)
val Ink = Color(0xFF0E0B16)
val PanelBg = Color(0xFF17131F)
val PanelHigh = Color(0xFF221C2E)

/** The five brand colours, used for accents on tools and tabs in turn. */
val Spectrum = listOf(Magenta, Saffron, Turmeric, Peacock, Violet)

val BrandGradient = Brush.linearGradient(listOf(Magenta, Saffron, Turmeric))
val CoolGradient = Brush.linearGradient(listOf(Peacock, Violet))
val FullGradient = Brush.horizontalGradient(listOf(Magenta, Saffron, Turmeric, Peacock, Violet))

private val scheme = darkColorScheme(
    primary = Magenta, onPrimary = Color.White,
    secondary = Peacock, onSecondary = Ink,
    tertiary = Turmeric, onTertiary = Ink,
    background = Ink, onBackground = Color(0xFFF1ECF7),
    surface = PanelBg, onSurface = Color(0xFFF1ECF7),
    surfaceVariant = PanelHigh, onSurfaceVariant = Color(0xFFB4AAC4),
    surfaceContainer = PanelBg, surfaceContainerHigh = PanelHigh,
    secondaryContainer = Color(0xFF3A1F38), onSecondaryContainer = Color(0xFFFFD7E6),
)

/** Photo editors read best on a dark canvas, so the app is always dark, with colourful accents. */
@Composable
fun LumoraTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = scheme, content = content)
