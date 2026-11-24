package app.lumora.editor.ui.motion

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Name of the social media studio section, kept in one place so it is easy to rename. */
const val STUDIO_NAME = "Feed Studio"

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalScreenScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * Container-transform "morph": an element tagged with the same [key] on two screens grows or
 * shrinks into the other during navigation, with a soft spring and rounded clip.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.morph(key: String, cornerDp: Int = 28): Modifier {
    val shared = LocalSharedScope.current ?: return this
    val screen = LocalScreenScope.current ?: return this
    return with(shared) {
        this@morph.sharedBounds(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = screen,
            boundsTransform = BoundsTransform { _, _ -> spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow) },
            clipInOverlayDuringTransition = OverlayClip(RoundedCornerShape(cornerDp.dp)),
        )
    }
}
