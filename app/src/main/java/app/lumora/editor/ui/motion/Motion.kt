package app.lumora.editor.ui.motion

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/** Springs used across the app: soft and slightly bouncy, so motion feels liquid rather than mechanical. */
object Springs {
    fun <T> squish() = spring<T>(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium)
    fun <T> glide() = spring<T>(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)
}

/**
 * Clickable that squishes under the finger and springs back with a little overshoot, with a light
 * haptic tick on tap, so every touch is felt.
 */
fun Modifier.squishClickable(
    enabled: Boolean = true,
    pressedScale: Float = 0.9f,
    haptic: Boolean = true,
    onClick: () -> Unit,
): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && enabled) pressedScale else 1f, Springs.squish(), label = "squish")
    val haptics = LocalHapticFeedback.current
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = source, indication = ripple(), enabled = enabled) {
            if (haptic) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        }
}

/** Haptic ticks while dragging a slider: one at every tenth of the range and a firmer one at zero. */
class SliderTicks(private val range: ClosedFloatingPointRange<Float>) {
    private var lastStep: Int? = null
    fun onValue(v: Float, perform: (HapticFeedbackType) -> Unit) {
        val step = ((v - range.start) / (range.endInclusive - range.start) * 10f).toInt()
        val prev = lastStep
        lastStep = step
        if (prev != null && prev != step) {
            perform(if (range.start < 0f && kotlin.math.abs(v) < (range.endInclusive - range.start) * 0.03f)
                HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove)
        }
    }
    fun reset() { lastStep = null }
}

@Composable
fun rememberSliderTicks(range: ClosedFloatingPointRange<Float>) = remember(range) { SliderTicks(range) }
