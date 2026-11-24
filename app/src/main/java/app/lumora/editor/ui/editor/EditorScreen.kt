package app.lumora.editor.ui.editor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lumora.editor.gl.PreviewView

private enum class Panel(val label: String) { Looks("Looks"), Adjust("Adjust"), Curves("Curves"), Crop("Crop") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(vm: EditorViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(PickVisualMedia()) { uri -> uri?.let(vm::open) }
    val pickPhoto = { picker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) }
    val snackbar = remember { SnackbarHostState() }
    var panel by rememberSaveable { mutableStateOf(Panel.Looks) }
    var comparing by remember { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); vm.messageShown() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Vannamayam", fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = pickPhoto) { Icon(Icons.Default.PhotoLibrary, "Open photo") }
                    IconButton(onClick = vm::undo, enabled = state.canUndo) { Icon(Icons.AutoMirrored.Filled.Undo, "Undo") }
                    IconButton(onClick = vm::redo, enabled = state.canRedo) { Icon(Icons.AutoMirrored.Filled.Redo, "Redo") }
                    TextButton(onClick = vm::export, enabled = state.preview != null && !state.busy) { Text("Save") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (state.preview == null) {
                    EmptyState(onOpen = pickPhoto)
                } else {
                    AndroidView(
                        factory = { PreviewView(it) },
                        update = { it.show(state.preview, if (comparing) state.params.originalLook() else state.params) },
                        modifier = Modifier.fillMaxSize(),
                    )
                    // Press and hold to see the photo without tonal edits.
                    Box(
                        Modifier.align(Alignment.BottomEnd).padding(12.dp)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f), RoundedCornerShape(50))
                            .pointerInput(Unit) {
                                detectTapGestures(onPress = { comparing = true; tryAwaitRelease(); comparing = false })
                            }
                            .padding(10.dp)
                    ) { Icon(Icons.Default.Compare, "Hold to compare with original") }
                }
                if (state.busy) CircularProgressIndicator()
            }

            if (state.preview != null) {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
                        Box(Modifier.fillMaxWidth().heightIn(min = 140.dp)) {
                            when (panel) {
                                Panel.Looks -> LooksPanel(state.params, vm)
                                Panel.Adjust -> AdjustPanel(state.params, vm)
                                Panel.Curves -> CurvesPanel(state.params, vm)
                                Panel.Crop -> CropPanel(state.params, vm)
                            }
                        }
                        TabRow(selectedTabIndex = panel.ordinal, containerColor = MaterialTheme.colorScheme.surface) {
                            Panel.entries.forEach { p ->
                                Tab(selected = panel == p, onClick = { panel = p }, text = { Text(p.label) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(onOpen: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
        Text("Every tool, free.", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Edits stay on your phone. Nothing is uploaded.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onOpen) { Text("Open a photo") }
    }
}
