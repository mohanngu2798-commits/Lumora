package app.lumora.editor.ui.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lumora.editor.R
import app.lumora.editor.ui.editor.GradientButton
import app.lumora.editor.ui.motion.squishClickable
import app.lumora.editor.ui.motion.STUDIO_NAME
import app.lumora.editor.ui.motion.morph
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.ui.graphics.drawscope.clipRect
import app.lumora.editor.ui.theme.*
import kotlinx.coroutines.delay

/** Start screen: a slowly drifting wall of colourful prints, one bold line, and clear ways in. */
@Composable
fun HomeScreen(onEdit: (Uri) -> Unit, onEnhance: (Uri) -> Unit, onStudio: () -> Unit, studioFirst: Boolean = false) {
    val editPicker = rememberLauncherForActivityResult(PickVisualMedia()) { it?.let(onEdit) }
    val enhancePicker = rememberLauncherForActivityResult(PickVisualMedia()) { it?.let(onEnhance) }
    val image = PickVisualMediaRequest(PickVisualMedia.ImageOnly)
    val edit = { editPicker.launch(image) }
    val enhance = { enhancePicker.launch(image) }

    Column(Modifier.fillMaxSize().background(Ink).verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().height(430.dp)) {
            PrintWall(Modifier.fillMaxSize())
            // Fade the wall into the page.
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
                0f to Color.Transparent, 0.45f to Color.Transparent, 1f to Ink)))
            Row(
                Modifier.statusBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)
                    .clip(RoundedCornerShape(50)).background(Ink.copy(alpha = 0.85f))
                    .padding(start = 6.dp, end = 14.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(painterResource(R.drawable.ic_logo), contentDescription = null, modifier = Modifier.size(30.dp))
                Spacer(Modifier.width(4.dp))
                Text("Vannamayam", maxLines = 1, softWrap = false,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp))
            }
            Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 24.dp)) {
                Text("Photo editor and feed studio", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Text("Make every photo\ncolourful.", style = MaterialTheme.typography.headlineMedium.copy(fontSize = 32.sp, lineHeight = 38.sp))
            }
        }

        Spacer(Modifier.height(24.dp))
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            val editorCard: @Composable () -> Unit = {
                SectionCard(
                    key = "editor", title = "Photo Editor",
                    subtitle = "Looks, pro tools, curves, crop, AI Enhance and AI upscale",
                    art = { EditorArt(it) }, onClick = edit,
                )
            }
            val studioCard: @Composable () -> Unit = {
                SectionCard(
                    key = "studio", title = STUDIO_NAME,
                    subtitle = "Seamless carousels, panorama posts, collages and feed filters",
                    art = { StudioArt(it) }, onClick = onStudio,
                )
            }
            Appear(0) { if (studioFirst) studioCard() else editorCard() }
            Appear(1) { if (studioFirst) editorCard() else studioCard() }
            Appear(2) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FeaturePill(Icons.Default.AutoAwesome, "AI Enhance", enhance)
                    FeaturePill(Icons.Default.ViewCarousel, "Carousel", onStudio)
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp), horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(6.dp))
            Text("Free and private. Your photos never leave your phone.",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A big entry card that morphs into its screen. */
@Composable
private fun SectionCard(key: String, title: String, subtitle: String, art: @Composable (Modifier) -> Unit, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().morph(key).squishClickable(pressedScale = 0.97f, onClick = onClick)
            .clip(RoundedCornerShape(28.dp)).background(PanelHigh),
    ) {
        art(Modifier.fillMaxWidth().height(150.dp))
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1)
                Spacer(Modifier.height(4.dp))
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(12.dp))
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(50)).background(OnInk), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Ink)
            }
        }
    }
}

/** Editor artwork: one print split by a before/after line. */
@Composable
private fun EditorArt(modifier: Modifier) {
    val t = rememberInfiniteTransition(label = "editorArt")
    val split by t.animateFloat(0.3f, 0.7f, infiniteRepeatable(tween(3200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "split")
    Canvas(modifier) {
        val p = prints[0]
        drawRect(Brush.linearGradient(listOf(Color(0xFF9A8F86), Color(0xFF6E6660), Color(0xFF3F3A37)), end = Offset(size.width, size.height)))
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.35f), Color.Transparent),
            center = Offset(size.width * 0.72f, size.height * 0.3f), radius = size.height * 0.7f),
            radius = size.height * 0.7f, center = Offset(size.width * 0.72f, size.height * 0.3f))
        val x = size.width * split
        clipRect(left = x) {
            drawRect(Brush.linearGradient(p, end = Offset(size.width, size.height)))
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.55f), Color.Transparent),
                center = Offset(size.width * 0.72f, size.height * 0.3f), radius = size.height * 0.7f),
                radius = size.height * 0.7f, center = Offset(size.width * 0.72f, size.height * 0.3f))
        }
        drawLine(Color.White, Offset(x, 0f), Offset(x, size.height), strokeWidth = 3.dp.toPx())
        drawCircle(Color.White, radius = 9.dp.toPx(), center = Offset(x, size.height / 2))
    }
}

/** Studio artwork: a carousel strip gliding sideways, with dashed slide seams. */
@Composable
private fun StudioArt(modifier: Modifier) {
    val t = rememberInfiniteTransition(label = "studioArt")
    val slide by t.animateFloat(0f, 1f, infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "slide")
    Canvas(modifier.background(PhotoCanvas)) {
        val h = size.height * 0.78f; val w = h * 0.8f; val top = (size.height - h) / 2
        val period = w * 4
        val shift = slide * period
        for (i in -1 until 6) {
            val x = i * w - shift % period + w * 0.2f
            drawRect(Color.White, Offset(x, top), Size(w, h))
        }
        for (i in 0 until 8) {
            val p = prints[(i + 1) % prints.size]
            val cx = i * w - shift % period + w * 0.2f
            val ph = h * 0.62f; val pw = ph * 0.8f
            val y = top + if (i % 2 == 0) h * 0.08f else h * 0.3f
            rotate(if (i % 2 == 0) -4f else 4f, pivot = Offset(cx, y + ph / 2)) {
                drawRoundRect(Brush.linearGradient(p, start = Offset(cx - pw / 2, y), end = Offset(cx + pw / 2, y + ph)),
                    Offset(cx - pw / 2, y), Size(pw, ph), CornerRadius(8f))
            }
        }
        for (i in -1 until 6) {
            val x = i * w - shift % period + w * 0.2f
            drawLine(Color(0x55000000), Offset(x, top), Offset(x, top + h), strokeWidth = 2f,
                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
        }
    }
}

@Composable
private fun FeaturePill(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.squishClickable(pressedScale = 0.93f, onClick = onClick).clip(RoundedCornerShape(50))
            .background(PanelHigh).padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, modifier = Modifier.size(19.dp), tint = OnInk)
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
    }
}

/** Items rise and fade in one after another on first show. */
@Composable
private fun Appear(index: Int, content: @Composable () -> Unit) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { delay(60L * index + 150); a.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow)) }
    Box(Modifier.graphicsLayer { alpha = a.value; translationY = (1f - a.value) * 36f }) { content() }
}

// Colour stories for the drifting prints: each is a little abstract "photo" in brand-adjacent tones.
private val prints = listOf(
    listOf(Color(0xFFFF8A65), Color(0xFFE8336F), Color(0xFF6C2A5A)),
    listOf(Color(0xFF8FD3F4), Color(0xFF14A99B), Color(0xFF0B4F5C)),
    listOf(Color(0xFFFFE29A), Color(0xFFFFB800), Color(0xFFB4571E)),
    listOf(Color(0xFFC7B8FF), Color(0xFF6C4DFF), Color(0xFF221B52)),
    listOf(Color(0xFFFFD1DC), Color(0xFFFF7A9C), Color(0xFF8C2F4C)),
    listOf(Color(0xFFB8E1C8), Color(0xFF4E9F72), Color(0xFF1E3D2C)),
    listOf(Color(0xFFFFC59E), Color(0xFFFF7A2F), Color(0xFF5B2A12)),
    listOf(Color(0xFFDCE6F2), Color(0xFF8AA1BD), Color(0xFF2E3B4E)),
)

/** A tilted wall of rounded prints that drifts upward forever. Pure drawing, no image assets. */
@Composable
fun PrintWall(modifier: Modifier) {
    val t = rememberInfiniteTransition(label = "wall")
    val drift by t.animateFloat(0f, 1f, infiniteRepeatable(tween(28000, easing = LinearEasing)), label = "drift")
    val frame = PanelHigh
    Canvas(modifier.background(PhotoCanvas)) {
        val cardW = size.width * 0.42f
        val cardH = cardW * 1.25f
        val gap = cardW * 0.08f
        val colStep = cardW + gap
        val rowStep = cardH + gap
        val cols = 4
        val rows = 5
        rotate(-14f, pivot = Offset(size.width / 2, size.height / 2)) {
            for (c in -1 until cols) {
                // Alternate columns drift at different offsets for depth.
                val colShift = (drift * rows * rowStep + if (c % 2 == 0) 0f else rowStep / 2) % (rows * rowStep)
                for (r in -1 until rows + 1) {
                    val x = c * colStep - cardW * 0.4f
                    val y = r * rowStep - colShift + rowStep * 0.2f
                    val p = prints[((c + 1) * 3 + r + 8) % prints.size]
                    val border = cardW * 0.035f
                    drawRoundRect(frame, Offset(x - border, y - border), Size(cardW + border * 2, cardH + border * 2),
                        CornerRadius(cardW * 0.09f))
                    drawRoundRect(
                        Brush.linearGradient(p, start = Offset(x, y), end = Offset(x + cardW, y + cardH)),
                        Offset(x, y), Size(cardW, cardH), CornerRadius(cardW * 0.07f),
                    )
                    // A soft "sun" for a photographic feel.
                    drawCircle(
                        Brush.radialGradient(listOf(Color.White.copy(alpha = 0.55f), Color.Transparent),
                            center = Offset(x + cardW * 0.7f, y + cardH * 0.3f), radius = cardW * 0.45f),
                        radius = cardW * 0.45f, center = Offset(x + cardW * 0.7f, y + cardH * 0.3f),
                    )
                }
            }
        }
    }
}
