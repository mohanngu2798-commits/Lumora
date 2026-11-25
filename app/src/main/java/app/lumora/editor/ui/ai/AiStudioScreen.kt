package app.lumora.editor.ui.ai

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
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
                    s.source?.let { BeforeAfter(it, s.result) }
                    if (s.busy) Progress(s.label, s.progress)
                }
                Surface(color = PanelBg, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)) {
                    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical = 14.dp)) {
                        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AiMode.entries.forEach { m -> ColorChip(m.label, Magenta, s.mode == m) { if (!s.busy) vm.run(m) } }
                        }
                        Text(s.mode.blurb, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
                    }
                }
            }
        }
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
                    Text(if (m == AiMode.Restore && !facesAvailable) "Coming in the next update" else m.blurb,
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** Before/after view: drag anywhere to move the divider. */
@Composable
private fun BeforeAfter(before: android.graphics.Bitmap, after: android.graphics.Bitmap?) {
    val b = remember(before) { before.asImageBitmap() }
    val a = remember(after) { after?.asImageBitmap() }
    var split by remember { mutableFloatStateOf(0.5f) }
    Canvas(
        Modifier.fillMaxSize().padding(12.dp)
            .pointerInput(Unit) {
                detectDragGestures { change, _ -> split = (change.position.x / size.width).coerceIn(0f, 1f) }
            }
            .pointerInput(Unit) { detectTapGestures { split = (it.x / size.width).coerceIn(0f, 1f) } }
    ) {
        val scale = min(size.width / b.width, size.height / b.height)
        val w = b.width * scale; val h = b.height * scale
        val x = (size.width - w) / 2; val y = (size.height - h) / 2
        val dst = IntSize(w.roundToInt(), h.roundToInt()); val at = IntOffset(x.roundToInt(), y.roundToInt())
        drawImage(b, dstOffset = at, dstSize = dst)
        if (a != null) {
            val cut = x + w * split
            clipRect(left = cut) { drawImage(a, dstOffset = at, dstSize = dst) }
            drawLine(Color.White, Offset(cut, y), Offset(cut, y + h), strokeWidth = 2.dp.toPx())
            drawCircle(Color.White, 12.dp.toPx(), Offset(cut, y + h / 2))
        }
    }
    if (a != null) Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Tag("Before"); Tag("After")
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
