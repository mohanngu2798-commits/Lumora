package app.lumora.editor.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Vannamayam means "colourful": a clean canvas with a vivid five-colour accent spectrum.
val Magenta = Color(0xFFE8336F)
val Saffron = Color(0xFFFF7A2F)
val Turmeric = Color(0xFFFFB800)
val Peacock = Color(0xFF14A99B)
val Violet = Color(0xFF6C4DFF)

/** The five brand colours, used for accents on tools and tabs in turn. */
val Spectrum = listOf(Magenta, Saffron, Turmeric, Peacock, Violet)

val BrandGradient = Brush.linearGradient(listOf(Magenta, Saffron))
val CoolGradient = Brush.linearGradient(listOf(Peacock, Violet))
val FullGradient = Brush.horizontalGradient(listOf(Magenta, Saffron, Turmeric, Peacock, Violet))

private val light = lightColorScheme(
    primary = Magenta, onPrimary = Color.White,
    secondary = Peacock, onSecondary = Color.White,
    tertiary = Violet, onTertiary = Color.White,
    background = Color(0xFFFFFFFF), onBackground = Color(0xFF17141D),
    surface = Color(0xFFFFFFFF), onSurface = Color(0xFF17141D),
    surfaceVariant = Color(0xFFF3F1F6), onSurfaceVariant = Color(0xFF6E6879),
    surfaceContainer = Color(0xFFF8F7FA), surfaceContainerHigh = Color(0xFFF1EFF4),
    outlineVariant = Color(0xFFE6E3EB),
)

private val dark = darkColorScheme(
    primary = Color(0xFFFF4F86), onPrimary = Color.White,
    secondary = Color(0xFF2EC4B6), onSecondary = Color(0xFF0E0B16),
    tertiary = Color(0xFF8B73FF), onTertiary = Color.White,
    background = Color(0xFF0F0D14), onBackground = Color(0xFFF2EFF6),
    surface = Color(0xFF0F0D14), onSurface = Color(0xFFF2EFF6),
    surfaceVariant = Color(0xFF221E2B), onSurfaceVariant = Color(0xFFADA6B9),
    surfaceContainer = Color(0xFF17141E), surfaceContainerHigh = Color(0xFF221E2B),
    outlineVariant = Color(0xFF2E2938),
)

// Theme-aware aliases used across the UI.
val Ink: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.background
val PanelBg: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.surfaceContainer
val PanelHigh: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.surfaceVariant
val OnInk: Color @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.onSurface
/** Where the photo sits while editing: a soft neutral so the photo stands out in either mode. */
val PhotoCanvas: Color @Composable @ReadOnlyComposable get() =
    if (isSystemInDarkTheme()) Color(0xFF0A090D) else Color(0xFFF1F0F4)

private val type = Typography().let { t ->
    t.copy(
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
    )
}

/** Light by default for a clean, premium feel; dark when the phone is in dark mode. */
@Composable
fun LumoraTheme(content: @Composable () -> Unit) =
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) dark else light, typography = type, content = content)
