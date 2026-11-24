package app.lumora.editor.ui.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import app.lumora.editor.ui.theme.*
import app.lumora.editor.ui.motion.Springs
import app.lumora.editor.ui.motion.squishClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.lumora.editor.model.CurveChannel
import app.lumora.editor.model.CurvePoints
import app.lumora.editor.model.EditParams
import app.lumora.editor.model.Looks
import kotlin.math.roundToInt

@Composable
internal fun LooksPanel(params: EditParams, vm: EditorViewModel) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        itemsIndexed(Looks.all) { i, (name, look) ->
            val selected = (params.lookName ?: "Original") == name
            val a = Spectrum[i % Spectrum.size]; val b = Spectrum[(i + 2) % Spectrum.size]
            val scale by animateFloatAsState(if (selected) 1.1f else 1f, Springs.squish(), label = "look")
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.squishClickable(pressedScale = 0.86f) { vm.commit(params.withLook(look, name)) }
                    .graphicsLayer { scaleX = scale; scaleY = scale },
            ) {
                Box(
                    Modifier.size(64.dp).clip(RoundedCornerShape(16.dp))
                        .background(if (name == "Original") Brush.linearGradient(listOf(PanelHigh, PanelHigh)) else Brush.linearGradient(listOf(a, b)))
                        .border(2.dp, if (selected) Color.White else Color.Transparent, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center,
                ) { Text(name.take(1), color = Color.White, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.height(6.dp))
                Text(name, style = MaterialTheme.typography.labelMedium,
                    color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private class AdjustTool(
    val label: String,
    val range: ClosedFloatingPointRange<Float>,
    val get: (EditParams) -> Float,
    val set: (EditParams, Float) -> EditParams,
)

private val adjustTools = listOf(
    AdjustTool("Exposure", -2f..2f, { it.exposure }) { p, v -> p.copy(exposure = v) },
    AdjustTool("Contrast", -1f..1f, { it.contrast }) { p, v -> p.copy(contrast = v) },
    AdjustTool("Highlights", -1f..1f, { it.highlights }) { p, v -> p.copy(highlights = v) },
    AdjustTool("Shadows", -1f..1f, { it.shadows }) { p, v -> p.copy(shadows = v) },
    AdjustTool("Saturation", -1f..1f, { it.saturation }) { p, v -> p.copy(saturation = v) },
    AdjustTool("Vibrance", -1f..1f, { it.vibrance }) { p, v -> p.copy(vibrance = v) },
    AdjustTool("Warmth", -1f..1f, { it.warmth }) { p, v -> p.copy(warmth = v) },
    AdjustTool("Tint", -1f..1f, { it.tint }) { p, v -> p.copy(tint = v) },
    AdjustTool("Fade", 0f..1f, { it.fade }) { p, v -> p.copy(fade = v) },
    AdjustTool("Sharpen", 0f..1f, { it.sharpen }) { p, v -> p.copy(sharpen = v) },
    AdjustTool("Vignette", -1f..1f, { it.vignette }) { p, v -> p.copy(vignette = v) },
    AdjustTool("Grain", 0f..1f, { it.grain }) { p, v -> p.copy(grain = v) },
)

@Composable
internal fun AdjustPanel(params: EditParams, vm: EditorViewModel) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val tool = adjustTools[selected]
    val accent = Spectrum[selected % Spectrum.size]
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        ValueSlider(tool.label, accent, tool.range, tool.get(params),
            onChange = { vm.preview(tool.set(params, it)) },
            onDone = { vm.commit() },
            onReset = { vm.commit(tool.set(params, 0f)) })
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            adjustTools.forEachIndexed { i, t ->
                val changed = t.get(params) != 0f
                ColorChip(if (changed) "${t.label} •" else t.label, Spectrum[i % Spectrum.size], i == selected) { selected = i }
            }
        }
    }
}

@Composable
internal fun CurvesPanel(params: EditParams, vm: EditorViewModel) {
    var channel by rememberSaveable { mutableStateOf(CurveChannel.Master) }
    val color = when (channel) {
        CurveChannel.Master -> Color.White
        CurveChannel.Red -> Color(0xFFFF6B6B)
        CurveChannel.Green -> Color(0xFF6BE38A)
        CurveChannel.Blue -> Color(0xFF6BA8FF)
    }
    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        CurveEditor(
            points = params.curves[channel],
            color = color,
            onChange = { vm.preview(params.copy(curves = params.curves.with(channel, it))) },
            onCommit = { vm.commit() },
            modifier = Modifier.size(180.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            CurveChannel.entries.forEach { c ->
                ColorChip(c.name, color, c == channel) { channel = c }
            }
            TextButton(onClick = { vm.commit(params.copy(curves = params.curves.with(channel, CurvePoints()))) }) {
                Text("Reset")
            }
            Text(
                "Drag to shape. Double-tap a point to remove it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val aspects = listOf<Pair<String, Float?>>(
    "Original" to null, "1:1" to 1f, "4:5" to 4f / 5f, "3:2" to 3f / 2f, "16:9" to 16f / 9f, "9:16" to 9f / 16f,
)

@Composable
internal fun CropPanel(params: EditParams, vm: EditorViewModel) {
    val crop = params.crop
    Column(Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            aspects.forEach { (label, a) ->
                ColorChip(label, Peacock, crop.aspect == a) { vm.commit(params.copy(crop = crop.copy(aspect = a))) }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                vm.commit(params.copy(crop = crop.copy(quarterTurns = (crop.quarterTurns + 1) % 4)))
            }) { Icon(Icons.Default.RotateRight, null); Spacer(Modifier.width(6.dp)); Text("Rotate") }
            OutlinedButton(onClick = {
                vm.commit(params.copy(crop = crop.copy(flipHorizontal = !crop.flipHorizontal)))
            }) { Icon(Icons.Default.Flip, null); Spacer(Modifier.width(6.dp)); Text("Flip") }
        }
    }
}
