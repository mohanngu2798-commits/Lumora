package app.lumora.editor.ui.ai

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lumora.editor.ui.editor.ColorChip
import app.lumora.editor.ui.editor.GradientButton
import app.lumora.editor.ui.motion.morph
import app.lumora.editor.ui.motion.squishClickable
import app.lumora.editor.ui.theme.*
import kotlin.math.min
import kotlin.math.roundToInt

private val modeIcons = mapOf(
    AiMode.Restore to Icons.Default.Face,
    AiMode.Enhance to Icons.Default.AutoAwesome,
    AiMode.Upscale2 to Icons.Default.Hd,
    AiMode.Upscale4 to Icons.Default.ZoomOutMap,
    AiMode.Background to Icons.Default.ContentCut,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiStudioScreen(vm: AiViewModel, onBack: () -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    var pendingMode by remember { mutableStateOf(AiMode.Restore) }
    val picker = rememberLauncherForActivityResult(PickVisualMedia()) { it?.let { u -> vm.open(u, pendingMode) } }
    val pick = { m: AiMode -> pendingMode = m; picker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) }
    val snackbar = remember { SnackbarHostState() }
    val leave = { vm.clear(); onBack() }
    BackHandler(onBack = leave)
    LaunchedEffect(s.message) { s.message?.let { snackbar.showSnackbar(it); vm.messageShown() } }
    val bgPicker = rememberLauncherForActivityResult(PickVisualMedia()) { it?.let { u -> vm.setBackdrop(app.lumora.editor.ai.Backdrop.Photo, u) } }
    if (s.needPro) app.lumora.editor.ui.pro.PlansSheet(onDismiss = vm::plansShown,
        reason = "You've used today's free AI edits. Go Pro for unlimited AI.")

    Scaffold(
        modifier = Modifier.morph("ai"),
        containerColor = Ink,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = leave) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Home") } },
                title = { Text("AI Studio", maxLines = 1, style = MaterialTheme.typography.titleLarge) },
                actions = {
                    if (s.source != null) IconButton(onClick = { pick(s.mode) }) { Icon(Icons.Default.PhotoLibrary, "Choose another photo") }
                    GradientButton("Save", enabled = s.result != null && !s.busy, onClick = vm::save)
                    Spacer(Modifier.width(8.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Ink),
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            if (s.source == null && !s.busy) {
                AiHome(facesAvailable = vm.facesAvailable, onPick = pick)
            } else {
                Box(Modifier.weight(1f).fillMaxWidth().background(PhotoCanvas), contentAlignment = Alignment.Center) {
                    s.source?.let { BeforeAfter(it, s.result, s.focus, checker = s.mode == AiMode.Background) }
                    if (s.busy) Progress(s.label, s.progress)
                }
                Surface(color = PanelBg, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)) {
                    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical = 14.dp)) {
                        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AiMode.entries.forEach { m -> ColorChip(m.label, Magenta, s.mode == m) { if (!s.busy) vm.run(m) } }
                        }
                        if (s.mode == AiMode.Background && s.cutout != null) {
                            Spacer(Modifier.height(10.dp))
                            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                app.lumora.editor.ai.Backdrop.entries.forEach { b ->
                                    BackdropSwatch(b, s.backdrop == b) {
                                        if (b == app.lumora.editor.ai.Backdrop.Photo) bgPicker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
                                        else vm.setBackdrop(b)
                                    }
                                }
                            }
                        }
                        val sizes = s.source?.let { src -> s.result?.let { r -> "  ${src.width}×${src.height} → ${r.width}×${r.height}" } } ?: ""
                        Text(s.mode.blurb + sizes + if (s.result != null) ". Pinch to zoom, double-tap for a close-up." else "", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun BackdropSwatch(b: app.lumora.editor.ai.Backdrop, selected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.squishClickable(pressedScale = 0.9f, onClick = onClick)) {
        Box(
            Modifier.size(44.dp).clip(CircleShape)
                .then(if (selected) Modifier.background(OnInk).padding(2.dp).clip(CircleShape).background(Ink).padding(2.dp).clip(CircleShape) else Modifier)
                .background(if (b.colour == android.graphics.Color.TRANSPARENT) PanelHigh else Color(b.colour))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            when (b) {
                app.lumora.editor.ai.Backdrop.Clear -> Canvas(Modifier.fillMaxSize()) { checkerboard(8.dp.toPx()) }
                app.lumora.editor.ai.Backdrop.Blur -> Icon(Icons.Default.BlurOn, null, tint = OnInk)
                app.lumora.editor.ai.Backdrop.Photo -> Icon(Icons.Default.Image, null, tint = OnInk)
                else -> {}
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(b.label, style = MaterialTheme.typography.labelSmall)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.checkerboard(cell: Float,
    from: Offset = Offset.Zero, to: Offset = Offset(size.width, size.height)) {
    drawRect(Color.White, from, androidx.compose.ui.geometry.Size(to.x - from.x, to.y - from.y))
    var y = from.y; var row = 0
    while (y < to.y) {
        var x = from.x + if (row % 2 == 0) 0f else cell
        while (x < to.x) {
            drawRect(Color(0xFFDDDDDD), Offset(x, y), androidx.compose.ui.geometry.Size(minOf(cell, to.x - x), minOf(cell, to.y - y)))
            x += cell * 2
        }
        y += cell; row++
    }
}

@Composable
private fun AiHome(facesAvailable: Boolean, onPick: (AiMode) -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(8.dp))
        Text("Bring photos back to life", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(6.dp))
        Text("Runs on your phone. Nothing is uploaded.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        AiMode.entries.forEach { m ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp).squishClickable(pressedScale = 0.97f) { onPick(m) }
                    .clip(RoundedCornerShape(20.dp)).background(PanelHigh).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(OnInk), contentAlignment = Alignment.Center) {
                    Icon(modeIcons.getValue(m), null, tint = Ink)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (m == AiMode.Restore) "Restore faces" else m.label, fontWeight = FontWeight.SemiBold)
                    Text(if (m == AiMode.Restore && !facesAvailable) "Coming in the next update" else if (m == AiMode.Background) "Cut out people, pets and products in one tap" else m.blurb,
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/**
 * Before/after view. One finger moves the divider; two fingers zoom and pan; double-tap jumps between
 * the whole photo and a close-up (of the restored face when there is one).
 */
@Composable
private fun BeforeAfter(before: android.graphics.Bitmap, after: android.graphics.Bitmap?, focus: android.graphics.RectF?, checker: Boolean = false) {
    val b = remember(before) { before.asImageBitmap() }
    val a = remember(after) { after?.asImageBitmap() }
    var split by remember { mutableFloatStateOf(0.5f) }
    var zoom by remember(before) { mutableFloatStateOf(1f) }
    var pan by remember(before) { mutableStateOf(Offset.Zero) }
    var box by remember { mutableStateOf(IntSize.Zero) }
    // Fit rectangle of the photo inside the view at zoom 1.
    fun fit(): Pair<Offset, androidx.compose.ui.geometry.Size> {
        val s = min(box.width.toFloat() / b.width, box.height.toFloat() / b.height)
        val w = b.width * s; val h = b.height * s
        return Offset((box.width - w) / 2, (box.height - h) / 2) to androidx.compose.ui.geometry.Size(w, h)
    }
    fun zoomTo(r: android.graphics.RectF?) {
        if (r == null || box == IntSize.Zero) { zoom = 1f; pan = Offset.Zero; return }
        val (o, sz) = fit(); val s = sz.width / b.width
        val z = (min(box.width / (r.width() * s), box.height / (r.height() * s))).coerceIn(1f, 6f)
        val cx = o.x + r.centerX() * s; val cy = o.y + r.centerY() * s
        zoom = z; pan = Offset(box.width / 2f - cx * z, box.height / 2f - cy * z)
        split = 0.5f
    }
    // Jump to the restored face when a result arrives.
    LaunchedEffect(after, focus, box) { if (after != null && focus != null && box != IntSize.Zero) zoomTo(focus) }
    androidx.compose.foundation.Canvas(
        Modifier.fillMaxSize().padding(12.dp).onSizeChanged { box = it }
            .pointerInput(before) {
                detectTapGestures(onDoubleTap = { if (zoom > 1.05f) zoomTo(null) else zoomTo(focus ?: run {
                    val (o, sz) = fit(); val s = sz.width / b.width
                    val px = (it.x - pan.x) / zoom; val py = (it.y - pan.y) / zoom
                    val sx = (px - o.x) / s; val sy = (py - o.y) / s; val r = b.width / 6f
                    android.graphics.RectF(sx - r, sy - r, sx + r, sy + r)
                }) })
            }
            .pointerInput(before) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val e = awaitPointerEvent()
                        val down = e.changes.count { it.pressed }
                        if (down >= 2) {
                            val z = e.calculateZoom(); val c = e.calculateCentroid(useCurrent = true)
                            val nz = (zoom * z).coerceIn(1f, 8f)
                            pan = if (nz <= 1.001f) Offset.Zero else (pan - c) * (nz / zoom) + c + e.calculatePan()
                            zoom = nz
                            e.changes.forEach { it.consume() }
                        } else if (down == 1) {
                            val ch = e.changes.first { it.pressed }
                            if (ch.positionChanged()) {
                                split = (ch.position.x / size.width).coerceIn(0f, 1f)
                                ch.consume()
                            }
                        }
                    } while (e.changes.any { it.pressed })
                }
            }
    ) {
        val (o, sz) = fit()
        val dst = IntSize(sz.width.roundToInt(), sz.height.roundToInt()); val at = IntOffset(o.x.roundToInt(), o.y.roundToInt())
        val cut = size.width * split
        clipRect {
            withTransform({ translate(pan.x, pan.y); scale(zoom, zoom, Offset.Zero) }) {
                drawImage(b, dstOffset = at, dstSize = dst)
            }
            if (a != null) clipRect(left = cut) {
                withTransform({ translate(pan.x, pan.y); scale(zoom, zoom, Offset.Zero) }) {
                    // A cut-out may be transparent: show the usual checkerboard behind it, not the original photo.
                    if (checker) checkerboard(10.dp.toPx() / zoom, Offset(at.x.toFloat(), at.y.toFloat()),
                        Offset((at.x + dst.width).toFloat(), (at.y + dst.height).toFloat()))
                    drawImage(a, dstOffset = at, dstSize = dst)
                }
            }
        }
        if (a != null) {
            drawLine(Color.White, Offset(cut, 0f), Offset(cut, size.height), strokeWidth = 2.dp.toPx())
            drawCircle(Color.White, 12.dp.toPx(), Offset(cut, size.height / 2))
        }
    }
    if (a != null) Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Tag("Before"); Tag(if (zoom > 1.05f) "After · ${"%.1f".format(zoom)}×" else "After")
    }
}

@Composable
private fun Tag(t: String) = Text(t, color = Color.White, style = MaterialTheme.typography.labelMedium,
    modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.5f)).padding(horizontal = 10.dp, vertical = 4.dp))

@Composable
private fun Progress(label: String?, p: Float?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(PanelHigh).padding(24.dp)) {
        if (p != null) CircularProgressIndicator(progress = { p }, color = Magenta, trackColor = PanelBg)
        else CircularProgressIndicator(color = Magenta)
        Spacer(Modifier.height(10.dp))
        Text(listOfNotNull(label, p?.let { "${(it * 100).toInt()}%" }).joinToString("  "))
    }
}
