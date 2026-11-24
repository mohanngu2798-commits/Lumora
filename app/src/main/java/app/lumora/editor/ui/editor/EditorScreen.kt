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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.graphics.toArgb
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
import app.lumora.editor.ui.motion.Springs
import app.lumora.editor.ui.motion.morph
import app.lumora.editor.ui.motion.squishClickable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

private enum class EditorPanel(val label: String, val icon: ImageVector) {
    Looks("Looks", Icons.Default.Palette),
    Tools("Tools", Icons.Default.AutoAwesome),
    Adjust("Adjust", Icons.Default.Tune),
    Curves("Curves", Icons.Default.Timeline),
    Crop("Crop", Icons.Default.Crop),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(vm: EditorViewModel, onHome: () -> Unit) {
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
        modifier = Modifier.morph("editor"),
        containerColor = Ink,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onHome) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Home") } },
                title = {},
                actions = {
                    AiPill(active = state.aiEnhanced, enabled = state.preview != null && !state.busy, onClick = vm::toggleAiEnhance)
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
            val canvasArgb = PhotoCanvas.toArgb()
            Box(Modifier.weight(1f).fillMaxWidth().background(PhotoCanvas), contentAlignment = Alignment.Center) {
                Crossfade(targetState = state.preview == null, animationSpec = tween(400), label = "empty") { empty ->
                    if (empty) {
                        EmptyState(onOpen = pickPhoto)
                    } else {
                        Box(Modifier.fillMaxSize()) {
                            AndroidView(
                                factory = { PreviewView(it) },
                                update = {
                                    it.setCanvasColor(canvasArgb)
                                    it.show(state.preview, if (comparing) state.params.originalLook() else state.params)
                                },
                                modifier = Modifier.fillMaxSize(),
                            )
                            // Press and hold to see the photo without edits.
                            val holdScale by animateFloatAsState(if (comparing) 1.18f else 1f, Springs.squish(), label = "hold")
                            Box(
                                Modifier.align(Alignment.BottomEnd).padding(12.dp)
                                    .graphicsLayer { scaleX = holdScale; scaleY = holdScale }
                                    .clip(CircleShape)
                                    .background(if (comparing) OnInk else Ink.copy(alpha = 0.85f))
                                    .pointerInput(Unit) {
                                        detectTapGestures(onPress = { comparing = true; tryAwaitRelease(); comparing = false })
                                    }
                                    .padding(10.dp)
                            ) { Icon(Icons.Default.Compare, "Hold to compare with original", tint = if (comparing) Ink else OnInk) }
                            val labelAlpha by animateFloatAsState(if (comparing) 1f else 0f, tween(180), label = "origLabel")
                            Text(
                                "Original",
                                Modifier.align(Alignment.TopCenter).padding(12.dp).graphicsLayer { alpha = labelAlpha }
                                    .background(PanelHigh, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 4.dp),
                            )
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
                        Box(Modifier.fillMaxWidth().heightIn(min = 150.dp).animateContentSize(Springs.glide())) {
                            AnimatedContent(
                                targetState = panel,
                                transitionSpec = {
                                    val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                                    (slideInHorizontally(Springs.glide()) { it / 4 * dir } + fadeIn(tween(220)) +
                                        scaleIn(Springs.glide(), initialScale = 0.96f))
                                        .togetherWith(slideOutHorizontally(Springs.glide()) { -it / 4 * dir } + fadeOut(tween(140)) +
                                            scaleOut(tween(180), targetScale = 0.96f))
                                        .using(SizeTransform(clip = false) { _, _ -> Springs.glide() })
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
    val count = EditorPanel.entries.size
    val index = current.ordinal
    // Liquid indicator: the leading edge races ahead and the trailing edge catches up,
    // so the pill stretches while it glides and settles with a soft bounce.
    var last by remember { mutableIntStateOf(index) }
    val movingRight = index >= last
    SideEffect { last = index }
    val fast = spring<Float>(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium)
    val slow = spring<Float>(dampingRatio = 0.75f, stiffness = Spring.StiffnessLow)
    val left by animateFloatAsState(index.toFloat(), if (movingRight) slow else fast, label = "left")
    val right by animateFloatAsState(index + 1f, if (movingRight) fast else slow, label = "right")
    val accent = OnInk
    val haptics = LocalHapticFeedback.current

    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)) {
        val slot = maxWidth / count
        Box(
            Modifier
                .offset(x = slot * left + 6.dp)
                .width(slot * (right - left) - 12.dp)
                .height(56.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(PanelHigh)
        )
        Row(Modifier.fillMaxWidth()) {
            EditorPanel.entries.forEachIndexed { i, p ->
                val selected = p == current
                val tint by animateColorAsState(
                    if (selected) OnInk else MaterialTheme.colorScheme.onSurfaceVariant, tween(250), label = "tab")
                val lift by animateFloatAsState(if (selected) 1.15f else 1f, Springs.squish(), label = "lift")
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(20.dp))
                        .squishClickable(haptic = false) {
                            if (!selected) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelect(p)
                        },
                ) {
                    Icon(p.icon, null, tint = tint,
                        modifier = Modifier.graphicsLayer { scaleX = lift; scaleY = lift })
                    Text(p.label, color = tint, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

/** Primary action: a solid pill in the text colour (black in light mode, white in dark). */
@Composable
internal fun GradientButton(text: String, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val bg = if (enabled) OnInk else PanelHigh
    val fg = if (enabled) Ink else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier
            .squishClickable(enabled = enabled, onClick = onClick)
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = 18.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = fg, fontWeight = FontWeight.SemiBold, maxLines = 1)
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
            Text("Save photo", style = MaterialTheme.typography.titleLarge)
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
        Modifier.fillMaxWidth().padding(vertical = 5.dp).squishClickable(pressedScale = 0.96f, onClick = onClick)
            .clip(RoundedCornerShape(16.dp)).background(PanelHigh).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(Ink), contentAlignment = Alignment.Center) {
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
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
        Text("No photo open", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))
        GradientButton("Choose a photo", onClick = onOpen)
    }
}

/** The AI Enhance toggle: outlined when off, filled with the brand gradient when on. */
@Composable
private fun AiPill(active: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.padding(end = 4.dp).squishClickable(enabled = enabled, onClick = onClick)
            .clip(RoundedCornerShape(50))
            .background(if (active) OnInk else PanelHigh)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.AutoAwesome, null, tint = Magenta, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(5.dp))
        Text("AI", fontWeight = FontWeight.SemiBold, color = if (active) Ink else OnInk)
    }
}
