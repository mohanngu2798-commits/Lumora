package app.lumora.editor.ui.intro

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lumora.editor.R
import app.lumora.editor.ui.editor.GradientButton
import app.lumora.editor.ui.home.PrintWall
import app.lumora.editor.ui.motion.Springs
import app.lumora.editor.ui.motion.squishClickable
import app.lumora.editor.ui.theme.*
import kotlinx.coroutines.delay

data class IntroAnswers(val use: String?, val interests: Set<String>, val postTypes: Set<String>)

private val uses = listOf(
    Icons.Default.Person to "For myself",
    Icons.Default.Star to "As a creator",
    Icons.Default.Storefront to "For my business",
    Icons.Default.MoreHoriz to "Something else",
)
private val interestOptions = listOf(
    Icons.Default.ViewCarousel to "Seamless carousels",
    Icons.Default.AddPhotoAlternate to "Edit photos",
    Icons.Default.AutoAwesome to "AI Enhance",
    Icons.Default.Hd to "AI upscale",
    Icons.Default.Tune to "Feed filters",
    Icons.Default.Dashboard to "Photo collages",
    Icons.Default.TextFields to "Designer fonts",
    Icons.Default.Panorama to "Panorama posts",
    Icons.Default.ContentCut to "Remove background",
)
private val postOptions = listOf(
    "Travel" to listOf(Color(0xFF8FD3F4), Color(0xFF14A99B)),
    "Food" to listOf(Color(0xFFFFC59E), Color(0xFFFF7A2F)),
    "Photo dump" to listOf(Color(0xFFFFD1DC), Color(0xFFE8336F)),
    "Wedding" to listOf(Color(0xFFF3EAD9), Color(0xFFB89B72)),
    "Fashion" to listOf(Color(0xFFC7B8FF), Color(0xFF6C4DFF)),
    "Daily life" to listOf(Color(0xFFFFE29A), Color(0xFFFFB800)),
    "Landscape" to listOf(Color(0xFFB8E1C8), Color(0xFF4E9F72)),
    "Business" to listOf(Color(0xFFDCE6F2), Color(0xFF2E3B4E)),
    "Festivals" to listOf(Color(0xFFFF8A65), Color(0xFF6C2A5A)),
)

/**
 * First-launch flow: the monogram animates in, glides up into a welcome page, then three quick
 * questions. Pages slide and fade into each other; the print wall keeps drifting behind them.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IntroScreen(onDone: (IntroAnswers) -> Unit) {
    var page by remember { mutableIntStateOf(-1) }   // -1 = logo splash
    var use by remember { mutableStateOf<String?>(null) }
    var interests by remember { mutableStateOf(setOf<String>()) }
    var posts by remember { mutableStateOf(setOf<String>()) }
    LaunchedEffect(Unit) { delay(1500); page = 0 }

    Box(Modifier.fillMaxSize().background(Ink)) {
        // The drifting wall rises in once the splash is done.
        val wall by animateFloatAsState(if (page >= 0) 1f else 0f, tween(900, easing = FastOutSlowInEasing), label = "wall")
        Box(Modifier.fillMaxWidth().fillMaxHeight(0.55f).graphicsLayer { alpha = wall; translationY = (1f - wall) * 120f }) {
            PrintWall(Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Transparent, 0.4f to Color.Transparent, 1f to Ink)))
        }

        AnimatedContent(
            targetState = page,
            transitionSpec = {
                val forward = targetState > initialState
                (slideInVertically(Springs.glide()) { if (forward) it / 5 else -it / 5 } + fadeIn(tween(320, delayMillis = 80)))
                    .togetherWith(slideOutVertically(tween(260)) { if (forward) -it / 8 else it / 8 } + fadeOut(tween(200)))
            },
            label = "intro",
        ) { p ->
            when (p) {
                -1 -> Splash()
                0 -> Page(
                    eyebrow = "Welcome to Vannamayam",
                    title = "Make photos that\nstand out",
                    body = "Pro editing, AI enhance and a studio for your feed. Free, and everything stays on your phone.",
                    button = "Get started", enabled = true, onNext = { page = 1 },
                ) {}
                1 -> Page("Personalise your experience", "How will you use\nVannamayam?", null, "Continue", use != null, { page = 2 }) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        uses.forEach { (icon, label) -> OptionRow(icon, label, use == label) { use = label } }
                    }
                }
                2 -> Page("Personalise your experience", "Which features excite\nyou most?", null, "Continue", interests.isNotEmpty(), { page = 3 }) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        interestOptions.forEach { (icon, label) ->
                            val on = label in interests
                            ToggleChip(icon, label, on) { interests = if (on) interests - label else interests + label }
                        }
                    }
                }
                else -> Page("Almost there", "What do you\npost about?", "Pick a few and we'll tune your home screen.", "Start creating",
                    posts.isNotEmpty(), { onDone(IntroAnswers(use, interests, posts)) }) {
                    PostGrid(posts) { label -> posts = if (label in posts) posts - label else posts + label }
                }
            }
        }
    }
}

@Composable
private fun Splash() {
    val s = remember { Animatable(0.6f) }
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        a.animateTo(1f, tween(400))
    }
    LaunchedEffect(Unit) { s.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessLow)) }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.graphicsLayer { scaleX = s.value; scaleY = s.value; alpha = a.value }) {
            Image(painterResource(R.drawable.ic_logo), null, Modifier.size(96.dp))
            Spacer(Modifier.height(8.dp))
            Text("Vannamayam", style = MaterialTheme.typography.titleLarge.copy(letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold))
        }
    }
}

@Composable
private fun Page(
    eyebrow: String, title: String, body: String?, button: String, enabled: Boolean, onNext: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 22.dp)) {
        Spacer(Modifier.weight(if (body != null && eyebrow.startsWith("Welcome")) 1f else 0.42f))
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            Text(eyebrow, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Text(title, style = MaterialTheme.typography.headlineMedium.copy(fontSize = 30.sp, lineHeight = 36.sp))
            if (body != null) {
                Spacer(Modifier.height(10.dp))
                Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(22.dp))
            content()
        }
        Spacer(Modifier.height(20.dp))
        GradientButton(button, enabled = enabled, modifier = Modifier.fillMaxWidth(), onClick = onNext)
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun OptionRow(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val border by animateColorAsState(if (selected) OnInk else Color.Transparent, label = "optBorder")
    Row(
        Modifier.fillMaxWidth().squishClickable(pressedScale = 0.97f, onClick = onClick)
            .clip(RoundedCornerShape(18.dp)).background(PanelHigh).border(1.5.dp, border, RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = OnInk, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Box(
            Modifier.size(22.dp).clip(CircleShape).background(if (selected) OnInk else Color.Transparent)
                .border(1.5.dp, if (selected) OnInk else MaterialTheme.colorScheme.onSurfaceVariant, CircleShape),
            contentAlignment = Alignment.Center,
        ) { if (selected) Icon(Icons.Default.Check, null, tint = Ink, modifier = Modifier.size(14.dp)) }
    }
}

@Composable
private fun ToggleChip(icon: ImageVector, label: String, on: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (on) OnInk else PanelHigh, label = "tBg")
    val fg by animateColorAsState(if (on) Ink else OnInk, label = "tFg")
    Row(
        Modifier.squishClickable(pressedScale = 0.92f, onClick = onClick).clip(RoundedCornerShape(50)).background(bg)
            .padding(horizontal = 15.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = fg, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
    }
}

@Composable
private fun PostGrid(selected: Set<String>, onToggle: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        postOptions.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { (label, colors) ->
                    val on = label in selected
                    val scale by animateFloatAsState(if (on) 0.94f else 1f, Springs.squish(), label = "post")
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.fillMaxWidth().aspectRatio(0.85f).graphicsLayer { scaleX = scale; scaleY = scale }
                                .squishClickable(pressedScale = 0.92f) { onToggle(label) }
                                .clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(colors))
                                .border(2.dp, if (on) OnInk else Color.Transparent, RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.BottomEnd,
                        ) {
                            if (on) Box(
                                Modifier.padding(8.dp).size(22.dp).clip(CircleShape).background(Color.White),
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Default.Check, null, tint = Color.Black, modifier = Modifier.size(14.dp)) }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                    }
                }
            }
        }
    }
}
