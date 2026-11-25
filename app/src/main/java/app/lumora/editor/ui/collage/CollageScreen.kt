package app.lumora.editor.ui.collage

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import app.lumora.editor.ui.home.drawPhoto
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lumora.editor.model.CollageMode
import app.lumora.editor.model.GridTemplates
import app.lumora.editor.ui.editor.ColorChip
import app.lumora.editor.ui.editor.ExitDialog
import app.lumora.editor.ui.editor.GradientButton
import app.lumora.editor.ui.editor.ValueSlider
import app.lumora.editor.ui.motion.morph
import app.lumora.editor.ui.motion.squishClickable
import app.lumora.editor.ui.theme.*

private enum class CollageTab(val label: String) { Layout("Layout"), Ratio("Ratio"), Border("Border"), Background("Background"), Photo("Photo") }

private val ratios = listOf("4:5" to 4f / 5f, "1:1" to 1f, "9:16" to 9f / 16f, "3:4" to 3f / 4f, "16:9" to 16f / 9f, "3:2" to 3f / 2f)
private val backgrounds = listOf(
    0xFFFFFFFF, 0xFFF3EEE6, 0xFF111014, 0xFFFFD9E4, 0xFFFFE7C2, 0xFFD8F1EC, 0xFFDCD4FF, 0xFFE8336F, 0xFFFF7A2F, 0xFF14A99B, 0xFF6C4DFF,
).map { it.toInt() }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollageScreen(vm: CollageViewModel, onBack: () -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    var pendingMode by remember { mutableStateOf(CollageMode.Grid) }
    val gridPicker = rememberLauncherForActivityResult(PickMultipleVisualMedia(GridTemplates.MAX_PHOTOS)) { vm.start(pendingMode, it) }
    val freePicker = rememberLauncherForActivityResult(PickMultipleVisualMedia(CollageViewModel.MAX_FREE)) { vm.start(pendingMode, it) }
    val addPicker = rememberLauncherForActivityResult(PickMultipleVisualMedia(CollageViewModel.MAX_FREE)) { vm.addPhotos(it) }
    val image = PickVisualMediaRequest(PickVisualMedia.ImageOnly)
    val pick = { m: CollageMode -> pendingMode = m; if (m == CollageMode.Grid) gridPicker.launch(image) else freePicker.launch(image) }
    val snackbar = remember { SnackbarHostState() }
    var askExit by remember { mutableStateOf(false) }
    val leave = { if (vm.isDirty) { askExit = true } else { vm.close(); onBack() } }
    BackHandler(onBack = leave)
    LaunchedEffect(s.message) { s.message?.let { snackbar.showSnackbar(it); vm.messageShown() } }
    if (askExit) ExitDialog(
        onSaveDraft = { askExit = false; vm.saveDraft(); onBack() },
        onDiscard = { askExit = false; vm.discard(); onBack() },
        onStay = { askExit = false },
    )

    Scaffold(
        modifier = Modifier.morph("collage"),
        containerColor = Ink,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = leave) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Home") } },
                title = { Text("Collage Studio", maxLines = 1) },
                actions = {
                    if (s.photos.isNotEmpty()) {
                        IconButton(onClick = { addPicker.launch(image) }) { Icon(Icons.Default.AddPhotoAlternate, "Add photos") }
                        GradientButton("Save", enabled = !s.busy, onClick = vm::save)
                        Spacer(Modifier.width(8.dp))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Ink),
            )
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            if (s.photos.isEmpty()) CollageHome(pick) else Editor(s, vm)
            if (s.busy) CircularProgressIndicator(Modifier.align(Alignment.Center), color = Magenta)
        }
    }
}

@Composable
private fun CollageHome(onPick: (CollageMode) -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(8.dp))
        Text("Make a collage", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(6.dp))
        Text("Pick a style, then choose your photos.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        ModeCard("Grid collage", "Up to 9 photos in neat layouts. Adjust gaps, corners and ratio.", Icons.Default.GridView) { onPick(CollageMode.Grid) }
        Spacer(Modifier.height(12.dp))
        ModeCard("Freestyle collage", "Up to 20 prints you can move, resize and twist anywhere.", Icons.Default.Collections) { onPick(CollageMode.Free) }
    }
}

@Composable
private fun ModeCard(title: String, body: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().squishClickable(pressedScale = 0.97f, onClick = onClick)
            .clip(RoundedCornerShape(24.dp)).background(PanelHigh).padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(OnInk), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Ink)
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Editor(s: CollageState, vm: CollageViewModel) {
    var tab by remember { mutableStateOf(CollageTab.Layout) }
    val thumbs = remember(s.photos) {
        s.photos.map { b ->
            val f = 120f / maxOf(b.width, b.height)
            android.graphics.Bitmap.createScaledBitmap(b, maxOf(1, (b.width * f).toInt()), maxOf(1, (b.height * f).toInt()), true).asImageBitmap()
        }
    }
    val current by rememberUpdatedState(s)
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth().background(PhotoCanvas).padding(16.dp), contentAlignment = Alignment.Center) {
            Canvas(
                Modifier.aspectRatio(s.spec.aspect, matchHeightConstraintsFirst = s.spec.aspect < 0.9f)
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val st = current
                            val w = size.width.toFloat(); val h = size.height.toFloat()
                            val hit = if (st.spec.mode == CollageMode.Grid) CollageRenderer.hitCell(st.spec, w, h, down.position.x, down.position.y)
                            else CollageRenderer.hitItem(st.spec, st.photos, w, h, down.position.x, down.position.y)
                            vm.select(hit)
                            if (hit != null) tab = CollageTab.Photo
                            if (hit == null) return@awaitEachGesture
                            do {
                                val e = awaitPointerEvent()
                                val pan = e.calculatePan(); val zoom = e.calculateZoom(); val rot = e.calculateRotation()
                                if (pan != Offset.Zero || zoom != 1f || rot != 0f) vm.transform(w, h, pan.x, pan.y, zoom, rot)
                                e.changes.forEach { if (it.positionChanged()) it.consume() }
                            } while (e.changes.any { it.pressed })
                        }
                    }
            ) {
                drawIntoCanvas { CollageRenderer.draw(it.nativeCanvas, size.width, size.height, s.spec, s.photos, s.selected) }
            }
        }
        Surface(color = PanelBg, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical = 12.dp)) {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ColorChip(if (s.spec.mode == CollageMode.Grid) "Grid" else "Freestyle", Saffron, false) {
                        vm.setMode(if (s.spec.mode == CollageMode.Grid) CollageMode.Free else CollageMode.Grid)
                    }
                    CollageTab.entries.forEach { t ->
                        if (t == CollageTab.Layout && s.spec.mode == CollageMode.Free) return@forEach
                        if (t == CollageTab.Photo && s.selected == null) return@forEach
                        ColorChip(t.label, Magenta, tab == t) { tab = t }
                    }
                }
                Spacer(Modifier.height(10.dp))
                val shown = when {
                    tab == CollageTab.Layout && s.spec.mode == CollageMode.Free -> CollageTab.Border
                    tab == CollageTab.Photo && s.selected == null -> CollageTab.Ratio
                    else -> tab
                }
                AnimatedContent(shown, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "collageTab") { t ->
                    when (t) {
                        CollageTab.Layout -> Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            GridTemplates.forCount(s.spec.cells.size).forEachIndexed { i, cells ->
                                val sel = i == s.spec.template
                                Canvas(Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)).background(PanelHigh)
                                    .border(2.dp, if (sel) OnInk else Color.Transparent, RoundedCornerShape(10.dp))
                                    .squishClickable(pressedScale = 0.88f) { vm.setTemplate(i) }.padding(6.dp)) {
                                    cells.forEachIndexed { k, c ->
                                        val g = 2.dp.toPx()
                                        // Show the user's own photos in each layout preview.
                                        run {
                                            drawPhoto(thumbs[k % thumbs.size], Offset(c.x * size.width + g / 2, c.y * size.height + g / 2),
                                                Size(c.w * size.width - g, c.h * size.height - g), 3.dp.toPx())
                                        }
                                    }
                                }
                            }
                        }
                        CollageTab.Ratio -> Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ratios.forEach { (label, a) -> ColorChip(label, Peacock, kotlin.math.abs(s.spec.aspect - a) < 0.01f) { vm.setAspect(a) } }
                        }
                        CollageTab.Border -> Column {
                            ValueSlider("Spacing", Magenta, 0f..1f, s.spec.spacing / 0.06f,
                                onChange = { vm.setSpacing(it * 0.06f) }, onDone = {}, onReset = { vm.setSpacing(0.02f) })
                            ValueSlider("Corners", Saffron, 0f..1f, s.spec.radius / 0.08f,
                                onChange = { vm.setRadius(it * 0.08f) }, onDone = {}, onReset = { vm.setRadius(0f) })
                            if (s.spec.mode == CollageMode.Free) Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ColorChip("White frames", Peacock, s.spec.frames) { vm.toggleFrames() }
                                ColorChip("Shadows", Violet, s.spec.shadows) { vm.toggleShadows() }
                                ColorChip("Toss again", Saffron, false) { vm.shuffle() }
                            }
                        }
                        CollageTab.Background -> Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            backgrounds.forEach { c ->
                                val sel = s.spec.background == c
                                Box(Modifier.size(40.dp).clip(CircleShape).background(Color(c))
                                    .border(if (sel) 3.dp else 1.dp, if (sel) OnInk else MaterialTheme.colorScheme.outlineVariant, CircleShape)
                                    .squishClickable(pressedScale = 0.85f) { vm.setBackground(c) })
                            }
                        }
                        CollageTab.Photo -> Column {
                            Text(if (s.spec.mode == CollageMode.Grid) "Drag to reposition, pinch to zoom inside the cell."
                                else "Drag to move, pinch to resize, twist with two fingers to rotate.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp))
                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (s.spec.mode == CollageMode.Grid) {
                                    ColorChip("Swap with next", Peacock, false) { vm.swapWithNext() }
                                    ColorChip("Shuffle all", Saffron, false) { vm.shuffle() }
                                }
                                if (s.photos.size > 1) ColorChip("Remove", Magenta, false) { vm.removeSelected() }
                                ColorChip("Done", Violet, false) { vm.select(null); tab = CollageTab.Ratio }
                            }
                        }
                    }
                }
            }
        }
    }
}
