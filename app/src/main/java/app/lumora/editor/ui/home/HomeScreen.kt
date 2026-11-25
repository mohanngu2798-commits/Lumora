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
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lumora.editor.R
import app.lumora.editor.data.CollageDraft
import app.lumora.editor.data.EditorDraft
import app.lumora.editor.data.StudioDraft
import androidx.compose.foundation.horizontalScroll
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
fun HomeScreen(
    onEdit: (Uri) -> Unit,
    onEnhance: (Uri) -> Unit,
    onStudio: () -> Unit,
    onAi: () -> Unit,
    onCollage: () -> Unit,
    studioFirst: Boolean = false,
    premiumUntil: Long = 0L,
    editorDraft: EditorDraft? = null,
    studioDraft: StudioDraft? = null,
    collageDraft: CollageDraft? = null,
    onResumeEditor: (EditorDraft) -> Unit = {},
    onResumeStudio: (StudioDraft) -> Unit = {},
    onResumeCollage: (CollageDraft) -> Unit = {},
) {
    val editPicker = rememberLauncherForActivityResult(PickVisualMedia()) { it?.let(onEdit) }
    val enhancePicker = rememberLauncherForActivityResult(PickVisualMedia()) { it?.let(onEnhance) }
    val image = PickVisualMediaRequest(PickVisualMedia.ImageOnly)
    val edit = { editPicker.launch(image) }
    val enhance = { enhancePicker.launch(image) }

    Box(Modifier.fillMaxSize().background(Ink)) {
    val scroll = rememberScrollState()
    CompositionLocalProvider(LocalHomeScroll provides scroll) {
    Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
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
                Text("Vannam", maxLines = 1, softWrap = false,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp))
            }
            var plans by remember { mutableStateOf(false) }
            if (plans) app.lumora.editor.ui.pro.PlansSheet(onDismiss = { plans = false })
            run {
                val offer = app.lumora.editor.data.Pro.offerActive()
                Row(
                    Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(horizontal = 20.dp, vertical = 14.dp)
                        .clip(RoundedCornerShape(50)).clickable { plans = true }.background(Brush.linearGradient(listOf(Magenta, Saffron)))
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.WorkspacePremium, null, tint = Color.White, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(when {
                        !offer -> "Pro"
                        premiumUntil == 0L -> "Pro free at launch"
                        else -> "Pro free till ${app.lumora.editor.ui.pro.dateOf(premiumUntil)}"
                    }, color = Color.White, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                }
            }
            Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 24.dp)) {
                Text("Photo editor, feed, collage and AI studios", style = MaterialTheme.typography.bodyMedium,
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
            val drafts = listOfNotNull(
                editorDraft?.let { DraftItem("Photo edit", "editor", it.savedAt) { onResumeEditor(it) } },
                studioDraft?.let { DraftItem(STUDIO_NAME, "studio", it.savedAt) { onResumeStudio(it) } },
                collageDraft?.let { DraftItem("Collage", "collage", it.savedAt) { onResumeCollage(it) } },
            ).sortedByDescending { it.savedAt }
            if (drafts.isNotEmpty()) Appear(0) {
                Column {
                    Text("Your drafts", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        drafts.forEach { d -> DraftCard(d) }
                    }
                }
            }
            Appear(1) { if (studioFirst) studioCard() else editorCard() }
            Appear(2) { if (studioFirst) editorCard() else studioCard() }
            Appear(3) {
                SectionCard(
                    key = "collage", title = "Collage Studio",
                    subtitle = "Grid layouts and freestyle collages, up to 20 photos",
                    art = { CollageArt(it) }, onClick = onCollage,
                )
            }
            Appear(4) {
                SectionCard(
                    key = "ai", title = "AI Studio",
                    subtitle = "Restore faces, AI Enhance and AI upscale, all on your phone",
                    art = { AiArt(it) }, onClick = onAi,
                )
            }
            Appear(5) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FeaturePill(Icons.Default.AutoAwesome, "AI Enhance", enhance)
                    FeaturePill(Icons.Default.ViewCarousel, "Carousel", onStudio)
                    FeaturePill(Icons.Default.GridView, "Collage", onCollage)
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
    // Keeps scrolled content from running under the status bar icons.
    Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(Ink.copy(alpha = 0.92f)))
    }
}

private val LocalHomeScroll = androidx.compose.runtime.staticCompositionLocalOf<androidx.compose.foundation.ScrollState?> { null }

private class DraftItem(val label: String, val kind: String, val savedAt: Long, val open: () -> Unit)

/** A saved draft: its preview picture, what it is and when it was saved. Tap to continue. */
@Composable
private fun DraftCard(d: DraftItem) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val thumb = remember(d.kind, d.savedAt) { app.lumora.editor.data.Drafts(context).thumb(d.kind)?.asImageBitmap() }
    Column(
        Modifier.width(132.dp).squishClickable(pressedScale = 0.94f, onClick = d.open)
            .clip(RoundedCornerShape(18.dp)).background(PanelHigh),
    ) {
        Box(Modifier.fillMaxWidth().height(150.dp).background(PhotoCanvas), contentAlignment = Alignment.Center) {
            if (thumb != null) androidx.compose.foundation.Image(thumb, null, Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop)
            else Icon(Icons.Default.History, null, tint = OnInk)
            Text("Draft", color = Color.White, style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp).clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 8.dp, vertical = 3.dp))
        }
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(d.label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            Text(android.text.format.DateUtils.getRelativeTimeSpanString(d.savedAt).toString(),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
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
    val img = androidx.compose.ui.graphics.ImageBitmap.imageResource(app.lumora.editor.R.drawable.sample_editor)
    val dull = remember { androidx.compose.ui.graphics.ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix().apply { setToSaturation(0.25f) }) }
    Canvas(modifier.clipToBounds()) {
        drawPhoto(img, Offset.Zero, size, filter = dull)
        drawRect(Color.Black.copy(alpha = 0.12f))
        val x = size.width * split
        clipRect(left = x) { drawPhoto(img, Offset.Zero, size) }
        drawLine(Color.White, Offset(x, 0f), Offset(x, size.height), strokeWidth = 3.dp.toPx())
        drawCircle(Color.White, radius = 9.dp.toPx(), center = Offset(x, size.height / 2))
    }
}

/** Collage artwork: a grid whose cells slowly breathe between layouts. */
@Composable
private fun CollageArt(modifier: Modifier) {
    val t = rememberInfiniteTransition(label = "collageArt")
    val k by t.animateFloat(0f, 1f, infiniteRepeatable(tween(4200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "k")
    val imgs = rememberImages(Samples.collage)
    Canvas(modifier.clipToBounds().background(PhotoCanvas)) {
        val g = 6.dp.toPx(); val h = size.height - g * 2; val w = h * 1.6f; val x0 = (size.width - w) / 2
        val split = 0.45f + 0.15f * k; val splitY = 0.55f - 0.1f * k
        val cells = listOf(
            floatArrayOf(0f, 0f, split, 1f), floatArrayOf(split, 0f, 1f - split, splitY),
            floatArrayOf(split, splitY, (1f - split) / 2, 1f - splitY), floatArrayOf(split + (1f - split) / 2, splitY, (1f - split) / 2, 1f - splitY),
        )
        cells.forEachIndexed { i, c ->
            val o = Offset(x0 + c[0] * w + g / 2, g + c[1] * h + g / 2); val sz = Size(c[2] * w - g, c[3] * h - g)
            drawPhoto(imgs[i], o, sz, 10f)
        }
    }
}

/** AI artwork: a soft, blurred print sharpening as a scan line passes. */
@Composable
private fun AiArt(modifier: Modifier) {
    val t = rememberInfiniteTransition(label = "aiArt")
    val scan by t.animateFloat(0.15f, 0.85f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "scan")
    val sharp = androidx.compose.ui.graphics.ImageBitmap.imageResource(app.lumora.editor.R.drawable.sample_ai_face)
    // The "before": the same picture shrunk and blown back up, so it looks soft and old.
    val soft = remember(sharp) {
        val a = sharp.asAndroidBitmap()
        val tiny = android.graphics.Bitmap.createScaledBitmap(a, a.width / 10, a.height / 10, true)
        android.graphics.Bitmap.createScaledBitmap(tiny, a.width, a.height, true).asImageBitmap()
    }
    val faded = remember { androidx.compose.ui.graphics.ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix().apply { setToSaturation(0.4f) }) }
    Canvas(modifier.clipToBounds()) {
        drawPhoto(soft, Offset.Zero, size, filter = faded)
        val x = size.width * scan
        clipRect(right = x) { drawPhoto(sharp, Offset.Zero, size) }
        drawLine(Brush.verticalGradient(listOf(Color.Transparent, Color.White, Color.Transparent)), Offset(x, 0f), Offset(x, size.height), strokeWidth = 3.dp.toPx())
    }
}

/** Studio artwork: a carousel strip gliding sideways, with dashed slide seams. */
@Composable
private fun StudioArt(modifier: Modifier) {
    val t = rememberInfiniteTransition(label = "studioArt")
    val slide by t.animateFloat(0f, 1f, infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "slide")
    val wall = rememberImages(Samples.wall)
    Canvas(modifier.clipToBounds().background(PhotoCanvas)) {
        val h = size.height * 0.78f; val w = h * 0.8f; val top = (size.height - h) / 2
        val period = w * 4
        val shift = slide * period
        for (i in -1 until 6) {
            val x = i * w - shift % period + w * 0.2f
            drawRect(Color.White, Offset(x, top), Size(w, h))
        }
        for (i in 0 until 8) {
            val img = wall[(i + 1) % wall.size]
            val cx = i * w - shift % period + w * 0.2f
            val ph = h * 0.62f; val pw = ph * 0.8f
            val y = top + if (i % 2 == 0) h * 0.08f else h * 0.3f
            rotate(if (i % 2 == 0) -4f else 4f, pivot = Offset(cx, y + ph / 2)) {
                drawPhoto(img, Offset(cx - pw / 2, y), Size(pw, ph), 8f)
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
    // Jelly scroll: each card trails the scroll on its own spring, later cards a little looser, so a fling
    // ripples down the page and every card settles with a small wobble.
    val scroll = LocalHomeScroll.current
    val target = scroll?.value?.toFloat() ?: 0f
    val follow by animateFloatAsState(target, spring(dampingRatio = 0.5f, stiffness = (420f - index * 45f).coerceAtLeast(160f)), label = "jelly")
    Box(Modifier.graphicsLayer {
        alpha = a.value
        val lag = (target - follow).coerceIn(-90f, 90f) * (0.35f + index * 0.08f)
        translationY = (1f - a.value) * 36f + lag
    }) { content() }
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
    val wall = rememberImages(Samples.wall)
    Canvas(modifier.clipToBounds().background(PhotoCanvas)) {
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
                    val img = wall[(((c + 1) * 3 + r + 8) % wall.size + wall.size) % wall.size]
                    val border = cardW * 0.035f
                    drawRoundRect(frame, Offset(x - border, y - border), Size(cardW + border * 2, cardH + border * 2),
                        CornerRadius(cardW * 0.09f))
                    drawPhoto(img, Offset(x, y), Size(cardW, cardH), cardW * 0.07f)
                }
            }
        }
    }
}
