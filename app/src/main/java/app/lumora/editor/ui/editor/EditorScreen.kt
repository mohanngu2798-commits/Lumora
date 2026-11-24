package app.lumora.editor.ui.editor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lumora.editor.gl.PreviewView
import app.lumora.editor.ui.theme.*

private enum class EditorPanel(val label: String, val icon: ImageVector) {
    Looks("Looks", Icons.Default.Palette),
    Tools("Tools", Icons.Default.AutoAwesome),
    Adjust("Adjust", Icons.Default.Tune),
    Curves("Curves", Icons.Default.Timeline),
    Crop("Crop", Icons.Default.Crop),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(vm: EditorViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(PickVisualMedia()) { uri -> uri?.let(vm::open) }
    val pickPhoto = { picker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) }
    val snackbar = remember { SnackbarHostState() }
    var panel by rememberSaveable { mutableStateOf(EditorPanel.Looks) }
    var comparing by remember { mutableStateOf(false) }
    var showSave by remember { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); vm.messageShown() }
    }

    Scaffold(
        containerColor = Ink,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Vannamayam",
                        style = TextStyle(brush = BrandGradient, fontSize = 22.sp, fontWeight = FontWeight.Bold),
                    )
                },
                actions = {
                    IconButton(onClick = pickPhoto) { Icon(Icons.Default.PhotoLibrary, "Open photo") }
                    IconButton(onClick = vm::undo, enabled = state.canUndo) { Icon(Icons.AutoMirrored.Filled.Undo, "Undo") }
                    IconButton(onClick = vm::redo, enabled = state.canRedo) { Icon(Icons.AutoMirrored.Filled.Redo, "Redo") }
                    GradientButton("Save", enabled = state.preview != null && !state.busy, onClick = { showSave = true })
                    Spacer(Modifier.width(8.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Ink),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Crossfade(targetState = state.preview == null, animationSpec = tween(400), label = "empty") { empty ->
                    if (empty) {
                        EmptyState(onOpen = pickPhoto)
                    } else {
                        Box(Modifier.fillMaxSize()) {
                            AndroidView(
                                factory = { PreviewView(it) },
                                update = { it.show(state.preview, if (comparing) state.params.originalLook() else state.params) },
                                modifier = Modifier.fillMaxSize(),
                            )
                            // Press and hold to see the photo without edits.
                            Box(
                                Modifier.align(Alignment.BottomEnd).padding(12.dp)
                                    .clip(CircleShape)
                                    .background(if (comparing) BrandGradient else Brush.linearGradient(listOf(PanelHigh, PanelHigh)))
                                    .pointerInput(Unit) {
                                        detectTapGestures(onPress = { comparing = true; tryAwaitRelease(); comparing = false })
                                    }
                                    .padding(10.dp)
                            ) { Icon(Icons.Default.Compare, "Hold to compare with original") }
                            AnimatedVisibility(
                                visible = comparing,
                                enter = fadeIn(), exit = fadeOut(),
                                modifier = Modifier.align(Alignment.TopCenter).padding(12.dp),
                            ) {
                                Text("Original", Modifier.background(PanelHigh, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 4.dp))
                            }
                        }
                    }
                }
                if (state.busy) BusyOverlay(state.busyLabel, state.progress)
            }

            AnimatedVisibility(
                visible = state.preview != null,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                Surface(color = PanelBg, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)) {
                    Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
                        Box(Modifier.fillMaxWidth().heightIn(min = 150.dp).animateContentSize(spring(stiffness = Spring.StiffnessMediumLow))) {
                            AnimatedContent(
                                targetState = panel,
                                transitionSpec = {
                                    val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                                    (slideInHorizontally(tween(260)) { it / 6 * dir } + fadeIn(tween(260)))
                                        .togetherWith(slideOutHorizontally(tween(200)) { -it / 6 * dir } + fadeOut(tween(160)))
                                },
                                label = "panel",
                            ) { p ->
                                when (p) {
                                    EditorPanel.Looks -> LooksPanel(state.params, vm)
                                    EditorPanel.Tools -> ToolsPanel(state.params, vm)
                                    EditorPanel.Adjust -> AdjustPanel(state.params, vm)
                                    EditorPanel.Curves -> CurvesPanel(state.params, vm)
                                    EditorPanel.Crop -> CropPanel(state.params, vm)
                                }
                            }
                        }
                        PanelTabs(panel) { panel = it }
                    }
                }
            }
        }
    }

    if (showSave) SaveSheet(onDismiss = { showSave = false }) { scale -> showSave = false; vm.export(scale) }
}

@Composable
private fun PanelTabs(current: EditorPanel, onSelect: (EditorPanel) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        EditorPanel.entries.forEachIndexed { i, p ->
            val selected = p == current
            val accent = Spectrum[i % Spectrum.size]
            val tint by animateColorAsState(if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant, label = "tab")
            val scale by animateFloatAsState(if (selected) 1.12f else 1f, spring(dampingRatio = 0.5f), label = "tabScale")
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clip(RoundedCornerShape(14.dp)).clickable { onSelect(p) }.padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Icon(p.icon, null, tint = tint, modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale })
                Text(p.label, color = tint, style = MaterialTheme.typography.labelSmall)
                Box(
                    Modifier.padding(top = 3.dp).height(3.dp).width(if (selected) 18.dp else 0.dp)
                        .clip(RoundedCornerShape(2.dp)).background(accent)
                )
            }
        }
    }
}

@Composable
internal fun GradientButton(text: String, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(if (enabled) BrandGradient else Brush.linearGradient(listOf(PanelHigh, PanelHigh)))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (enabled) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun BusyOverlay(label: String?, progress: Float?) {
    Box(Modifier.fillMaxSize().background(Ink.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.background(PanelHigh, RoundedCornerShape(20.dp)).padding(24.dp),
        ) {
            if (progress != null) {
                CircularProgressIndicator(progress = { progress }, color = Magenta, trackColor = PanelBg)
                Spacer(Modifier.height(12.dp))
                Text("${label ?: "Working"}  ${(progress * 100).toInt()}%")
                Text("Runs on your phone, nothing is uploaded", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                CircularProgressIndicator(color = Magenta)
                label?.let { Spacer(Modifier.height(12.dp)); Text(it) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SaveSheet(onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = PanelBg) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text("Save photo", style = MaterialTheme.typography.titleLarge.copy(brush = BrandGradient, fontWeight = FontWeight.Bold))
            Spacer(Modifier.height(12.dp))
            SaveOption(Icons.Default.Image, "Full resolution", "Your photo at its original size", Peacock) { onSave(1) }
            SaveOption(Icons.Default.Hd, "AI upscale 2x", "Twice the size, AI-sharpened details", Saffron) { onSave(2) }
            SaveOption(Icons.Default.AutoAwesome, "AI upscale 4x", "Four times the size. Takes a minute or two", Magenta) { onSave(4) }
        }
    }
}

@Composable
private fun SaveOption(icon: ImageVector, title: String, subtitle: String, accent: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(16.dp)).background(PanelHigh)
            .clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(accent.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = accent)
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EmptyState(onOpen: () -> Unit) {
    val t = rememberInfiniteTransition(label = "blobs")
    val drift by t.animateFloat(0f, 1f, infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Reverse), label = "drift")
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // Soft colour blobs drifting behind the welcome text.
        Box(Modifier.fillMaxSize().blur(70.dp)) {
            Spectrum.forEachIndexed { i, c ->
                val x = 0.15f + 0.7f * ((i * 0.37f + drift * (if (i % 2 == 0) 0.3f else -0.3f)) % 1f + 1f) % 1f
                val y = 0.2f + 0.6f * ((i * 0.53f + drift * 0.2f) % 1f)
                Box(
                    Modifier.fillMaxSize().wrapContentSize(Alignment.TopStart)
                        .offset { androidx.compose.ui.unit.IntOffset((x * 900).toInt() - 150, (y * 1400).toInt() - 150) }
                        .size(160.dp).clip(CircleShape).background(c.copy(alpha = 0.55f))
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Text("வண்ணமயம்", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(
                "Make it colourful.",
                style = MaterialTheme.typography.headlineMedium.copy(brush = FullGradient, fontWeight = FontWeight.Bold),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Every tool free. Edits and AI run on your phone. Nothing is uploaded.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(28.dp))
            GradientButton("Open a photo", onClick = onOpen, modifier = Modifier.border(0.dp, Color.Transparent, RoundedCornerShape(50)))
        }
    }
}
