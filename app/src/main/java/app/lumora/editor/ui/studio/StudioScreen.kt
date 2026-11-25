package app.lumora.editor.ui.studio

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lumora.editor.model.Background
import app.lumora.editor.model.Carousel
import app.lumora.editor.model.InstaLooks
import app.lumora.editor.model.FilterLibrary
import app.lumora.editor.model.Layer
import app.lumora.editor.model.SlideFormat
import app.lumora.editor.ui.editor.ColorChip
import app.lumora.editor.ui.editor.GradientButton
import app.lumora.editor.ui.motion.Springs
import app.lumora.editor.ui.motion.STUDIO_NAME
import app.lumora.editor.ui.motion.morph
import app.lumora.editor.ui.motion.squishClickable
import app.lumora.editor.ui.theme.*
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private val backgrounds: List<Pair<String, Background>> = listOf(
    "White" to Background.Solid(0xFFFFFFFF.toInt()),
    "Cream" to Background.Solid(0xFFF3EAD9.toInt()),
    "Black" to Background.Solid(0xFF0E0B16.toInt()),
    "Blur" to Background.BlurredPhoto,
    "Sunset" to Background.Gradient(0xFFFF3D7F.toInt(), 0xFFFFD23F.toInt()),
    "Lagoon" to Background.Gradient(0xFF2EC4B6.toInt(), 0xFF7B5CFF.toInt()),
    "Blush" to Background.Solid(0xFFF7D6DC.toInt()),
    "Sage" to Background.Solid(0xFFC9D6C1.toInt()),
)

private enum class StudioTab(val label: String) { Layout("Layout"), Filters("Filters"), Background("Background"), Photo("Photo") }

/**
 * Instagram Studio: a seamless carousel canvas. Photos can sit across slide edges, and the
 * strip is exported as perfectly aligned slides in posting order.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioScreen(vm: StudioViewModel, onBack: () -> Unit, onCollage: () -> Unit = {}) {
    val s by vm.state.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(PickMultipleVisualMedia(10)) { vm.addPhotos(it) }
    val pick = { picker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) }
    val snackbar = remember { SnackbarHostState() }
    var tab by remember { mutableStateOf(StudioTab.Layout) }
    var askExit by remember { mutableStateOf(false) }
    val leave = { if (vm.isDirty) { askExit = true } else { onBack() } }
    BackHandler(onBack = leave)
    if (askExit) app.lumora.editor.ui.editor.ExitDialog(
        onSaveDraft = { askExit = false; vm.saveDraft(); onBack() },
        onDiscard = { askExit = false; vm.discard(); onBack() },
        onStay = { askExit = false },
    )
    LaunchedEffect(s.message) { s.message?.let { snackbar.showSnackbar(it); vm.messageShown() } }
    LaunchedEffect(s.selected) { if (s.selected != null && tab != StudioTab.Filters) tab = StudioTab.Photo }

    Scaffold(
        modifier = Modifier.morph("studio"),
        containerColor = Ink,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = leave) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to editor") } },
                title = { Text(STUDIO_NAME, maxLines = 1, softWrap = false, style = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.SemiBold)) },
                actions = {
                    IconButton(onClick = pick) { Icon(Icons.Default.AddPhotoAlternate, "Add photos") }
                    GradientButton("Save", enabled = s.carousel.layers.isNotEmpty() && !s.busy, onClick = vm::export)
                    Spacer(Modifier.width(8.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Ink),
            )
        },
    ) { pad ->
        if (s.photos.isEmpty() && !s.busy) {
            StudioHub(Modifier.padding(pad), onPick = { layout, t -> vm.startWith(layout); tab = t; pick() }, onCollage = onCollage)
            return@Scaffold
        }
        Column(Modifier.fillMaxSize().padding(pad)) {
            // Format and slide count.
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SlideFormat.entries.forEach { f -> ColorChip(f.label, Peacock, s.carousel.format == f) { vm.setFormat(f) } }
                Spacer(Modifier.weight(1f))
                CircleBtn(Icons.Default.Remove, "Fewer slides", enabled = s.carousel.slides > 1) { vm.setSlides(s.carousel.slides - 1) }
                Text("${s.carousel.slides}", fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp))
                CircleBtn(Icons.Default.Add, "More slides", enabled = s.carousel.slides < 10) { vm.setSlides(s.carousel.slides + 1) }
            }

            Box(Modifier.weight(1f).fillMaxWidth().background(PhotoCanvas), contentAlignment = Alignment.Center) {
                if (s.photos.isEmpty()) {
                    StudioEmpty(pick)
                } else {
                    CarouselCanvas(s, vm)
                }
                if (s.busy) CircularProgressIndicator(color = Magenta)
            }

            if (s.photos.isNotEmpty()) {
                Surface(color = PanelBg, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)) {
                    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical = 10.dp)) {
                        Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            StudioTab.entries.forEach { t ->
                                if (t != StudioTab.Photo || s.selected != null) ColorChip(t.label, Magenta, tab == t) { tab = t }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        AnimatedContent(targetState = if (s.selected == null && tab == StudioTab.Photo) StudioTab.Layout else tab,
                            transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "studioTab") { t ->
                            when (t) {
                                StudioTab.Layout -> ChipScroll {
                                    AutoLayout.entries.forEachIndexed { i, l -> ColorChip(l.label, Spectrum[i % Spectrum.size], false) { vm.applyLayout(l) } }
                                }
                                StudioTab.Filters -> Column {
                                    ChipScroll {
                                        (InstaLooks.all + FilterLibrary.exclusive.filters).forEachIndexed { i, (name, _) ->
                                            val current = s.selected?.let { s.filters[it] ?: "None" }
                                            ColorChip(name, Spectrum[i % Spectrum.size], current == name) {
                                                vm.applyFilter(name, all = s.selected == null)
                                            }
                                        }
                                    }
                                    Text(
                                        if (s.selected == null) "Applies to every photo. Tap a photo first to filter just that one."
                                        else "Applies to the selected photo.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                    )
                                }
                                StudioTab.Background -> ChipScroll {
                                    backgrounds.forEach { (name, b) ->
                                        ColorChip(name, Saffron, s.carousel.background == b, swatch = swatchOf(b)) { vm.setBackground(b) }
                                    }
                                }
                                StudioTab.Photo -> ChipScroll {
                                    ColorChip("Bring to front", Peacock, false) { vm.bringToFront() }
                                    ColorChip("Rounded", Turmeric, s.carousel.layers.firstOrNull { it.id == s.selected }?.corner?.let { it > 0f } == true) { vm.toggleRounded() }
                                    ColorChip("Straighten", Violet, false) { vm.straighten() }
                                    ColorChip("Remove", Magenta, false) { vm.deleteSelected() }
                                    ColorChip("Done", Peacock, false) { vm.select(null) }
                                }
                            }
                        }
                        Text(
                            if (s.selected == null) "Tap a photo to move, pinch or rotate it. Photos can cross slide edges."
                            else "Drag to move, pinch to resize, twist to rotate.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChipScroll(content: @Composable RowScope.() -> Unit) = Row(
    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
    horizontalArrangement = Arrangement.spacedBy(6.dp), content = content,
)

private fun swatchOf(b: Background): Brush = when (b) {
    is Background.Solid -> Brush.linearGradient(listOf(Color(b.argb), Color(b.argb)))
    is Background.Gradient -> Brush.linearGradient(listOf(Color(b.from), Color(b.to)))
    Background.BlurredPhoto -> Brush.linearGradient(listOf(Color(0xFF8A7F99), Color(0xFF4A4257)))
}

@Composable
private fun CircleBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(34.dp).squishClickable(enabled = enabled, onClick = onClick).clip(CircleShape).background(PanelHigh),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, desc, tint = if (enabled) OnInk else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)) }
}

@Composable
private fun CarouselCanvas(s: StudioState, vm: StudioViewModel) {
    val c = s.carousel
    val scroll = rememberScrollState()
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
        // Fit the strip's height to the space, but keep each slide at most ~80% of screen width.
        val hByHeight = maxHeight
        val hByWidth = maxWidth * 0.8f / c.format.aspect
        val stripH = minOf(hByHeight, hByWidth)
        val stripW = stripH * c.width
        val px = with(density) { stripH.toPx() }
        val selectedScale by animateFloatAsState(if (s.selected != null) 1f else 0f, Springs.glide(), label = "sel")

        Row(Modifier.fillMaxWidth().horizontalScroll(scroll, enabled = s.selected == null)) {
            Spacer(Modifier.width(16.dp))
            Canvas(
                Modifier.size(stripW, stripH).clip(RoundedCornerShape(6.dp))
                    .pointerInput(c.layers, px) {
                        detectTapGestures { pos ->
                            val hit = c.layers.lastOrNull { hits(it, pos.x / px, pos.y / px) }
                            if (hit != null) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            vm.select(hit?.id)
                        }
                    }
                    .pointerInput(s.selected, px) {
                        if (s.selected == null) return@pointerInput
                        detectTransformGestures { _, pan, zoom, rotation ->
                            vm.transformSelected(pan.x / px, pan.y / px, zoom, rotation)
                        }
                    }
            ) {
                drawIntoCanvas { CarouselPainter.draw(it.nativeCanvas, c, s.photos + s.filtered, s.blurred, px) }
                // Slide seams (not exported).
                for (i in 1 until c.slides) {
                    val x = c.slideWidth * i * px
                    drawLine(Color.White.copy(alpha = 0.85f), Offset(x, 0f), Offset(x, size.height), strokeWidth = 2f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)))
                }
                // Selection outline.
                c.layers.firstOrNull { it.id == s.selected }?.let { l ->
                    translate(l.cx * px, l.cy * px) {
                        rotate(l.rotation, pivot = Offset.Zero) {
                            val w = l.width * px; val h = l.height * px
                            drawRect(
                                brush = Brush.linearGradient(listOf(Magenta, Turmeric, Peacock)),
                                topLeft = Offset(-w / 2, -h / 2), size = androidx.compose.ui.geometry.Size(w, h),
                                style = Stroke(width = 3.dp.toPx() * selectedScale),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.width(16.dp))
        }
    }
}

/** Point-in-rotated-rectangle test in canvas units. */
private fun hits(l: Layer, x: Float, y: Float): Boolean {
    val a = Math.toRadians(-l.rotation.toDouble())
    val dx = x - l.cx; val dy = y - l.cy
    val rx = dx * cos(a) - dy * sin(a); val ry = dx * sin(a) + dy * cos(a)
    return abs(rx) <= l.width / 2 && abs(ry) <= l.height / 2
}

private class HubItem(val title: String, val body: String, val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val layout: AutoLayout?, val tab: StudioTab = StudioTab.Layout)

private val hubItems = listOf(
    HubItem("Seamless carousel", "Photos flow across slides, so every swipe continues the picture", Icons.Default.ViewCarousel, AutoLayout.Seams),
    HubItem("Panorama post", "One wide photo split into perfectly aligned slides", Icons.Default.Panorama, AutoLayout.Panorama),
    HubItem("Photo dump", "Up to 10 photos tossed across slides like prints on a table", Icons.Default.Collections, AutoLayout.Dump),
    HubItem("Fit to post", "Each photo on its own slide with clean borders, no cropping", Icons.Default.CropPortrait, AutoLayout.OnePerSlide),
    HubItem("Feed filters", "Give all your photos one matching look before posting", Icons.Default.AutoAwesome, AutoLayout.OnePerSlide, StudioTab.Filters),
    HubItem("Collage", "Grid and freestyle collages in Collage Studio", Icons.Default.GridView, null),
)

/** Feed Studio start screen: pick what to make, then choose photos. */
@Composable
private fun StudioHub(modifier: Modifier, onPick: (AutoLayout, StudioTab) -> Unit, onCollage: () -> Unit) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(8.dp))
        Text("What are you posting?", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(6.dp))
        Text("Pick a style, then choose your photos. Everything exports in Instagram sizes.",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))
        hubItems.forEach { item ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp)
                    .squishClickable(pressedScale = 0.97f) { if (item.layout == null) onCollage() else onPick(item.layout, item.tab) }
                    .clip(RoundedCornerShape(22.dp)).background(PanelHigh).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(OnInk), contentAlignment = Alignment.Center) {
                    Icon(item.icon, null, tint = Ink)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(item.body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun StudioEmpty(onAdd: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
        Icon(Icons.Default.ViewCarousel, null, tint = Magenta, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(12.dp))
        Text("Seamless carousels", style = MaterialTheme.typography.headlineSmall.copy(brush = FullGradient, fontWeight = FontWeight.Bold))
        Spacer(Modifier.height(8.dp))
        Text(
            "Pick up to 10 photos. Place them across slides so every swipe flows into the next, then save perfectly aligned slides for Instagram.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        GradientButton("Choose photos", onClick = onAdd)
    }
}
