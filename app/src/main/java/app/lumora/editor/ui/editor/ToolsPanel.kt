package app.lumora.editor.ui.editor

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.lumora.editor.model.BlurShape
import app.lumora.editor.model.EditParams
import app.lumora.editor.model.LeakStyles
import app.lumora.editor.ui.theme.*
import app.lumora.editor.ui.motion.Springs
import app.lumora.editor.ui.motion.rememberSliderTicks
import app.lumora.editor.ui.motion.squishClickable
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlin.math.roundToInt

private class EffectTool(
    val label: String,
    val icon: ImageVector,
    val accent: Color,
    val range: ClosedFloatingPointRange<Float>,
    val get: (EditParams) -> Float,
    val set: (EditParams, Float) -> EditParams,
)

private val effectTools = listOf(
    EffectTool("Clarity", Icons.Default.Details, Peacock, -1f..1f, { it.clarity }) { p, v -> p.copy(clarity = v) },
    EffectTool("Dehaze", Icons.Default.FilterDrama, Violet, -1f..1f, { it.dehaze }) { p, v -> p.copy(dehaze = v) },
    EffectTool("HDR", Icons.Default.HdrOn, Saffron, 0f..1f, { it.hdr }) { p, v -> p.copy(hdr = v) },
    EffectTool("Glow", Icons.Default.Flare, Turmeric, 0f..1f, { it.glow }) { p, v -> p.copy(glow = v) },
    EffectTool("Lens blur", Icons.Default.BlurOn, Peacock, 0f..1f, { it.lensBlur }) { p, v -> p.copy(lensBlur = v) },
    EffectTool("Light leak", Icons.Default.WbSunny, Magenta, 0f..1f, { it.lightLeak }) { p, v -> p.copy(lightLeak = v) },
)

/** Snapseed-style effect tools, plus one-tap Auto. */
@Composable
internal fun ToolsPanel(params: EditParams, vm: EditorViewModel) {
    var selected by rememberSaveable { mutableIntStateOf(-1) }
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ToolTile("Auto", Icons.Default.AutoFixHigh, Magenta, selected = false, changed = false, gradient = true) { vm.autoEnhance() }
            effectTools.forEachIndexed { i, t ->
                ToolTile(t.label, t.icon, t.accent, selected = i == selected, changed = t.get(params) != 0f) {
                    selected = if (selected == i) -1 else i
                }
            }
        }
        val tool = effectTools.getOrNull(selected)
        if (tool == null) {
            Text(
                "Tap Auto for a one-tap fix, or pick a tool to fine-tune.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            )
        } else {
            ValueSlider(tool.label, tool.accent, tool.range, tool.get(params),
                onChange = { vm.preview(tool.set(params, it)) },
                onDone = { vm.commit() },
                onReset = { vm.commit(tool.set(params, 0f)) })
            when (tool.label) {
                "Lens blur" -> ChipRow {
                    BlurShape.entries.forEach { s ->
                        ColorChip(s.label, tool.accent, params.blurShape == s) { vm.commit(params.copy(blurShape = s)) }
                    }
                }
                "Light leak" -> ChipRow {
                    LeakStyles.all.forEachIndexed { i, (name, colors) ->
                        ColorChip(name, Color(0xFF000000 or colors.first.toLong()), params.leakStyle == i,
                            swatch = Brush.linearGradient(listOf(Color(0xFF000000 or colors.first.toLong()), Color(0xFF000000 or colors.second.toLong())))) {
                            vm.commit(params.copy(leakStyle = i, lightLeak = if (params.lightLeak == 0f) 0.5f else params.lightLeak))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChipRow(content: @Composable RowScope.() -> Unit) = Row(
    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    content = content,
)

@Composable
private fun ToolTile(
    label: String, icon: ImageVector, accent: Color, selected: Boolean, changed: Boolean,
    gradient: Boolean = false, onClick: () -> Unit,
) {
    val scale by animateFloatAsState(if (selected) 1.1f else 1f, Springs.squish(), label = "tile")
    val ring by animateColorAsState(if (selected) accent else Color.Transparent, label = "ring")
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(68.dp).squishClickable(pressedScale = 0.85f, onClick = onClick)
            .graphicsLayer { scaleX = scale; scaleY = scale }.padding(vertical = 4.dp),
    ) {
        Box(
            Modifier.size(48.dp).clip(CircleShape)
                .background(if (gradient) BrandGradient else Brush.linearGradient(listOf(accent.copy(alpha = 0.28f), accent.copy(alpha = 0.12f))))
                .border(2.dp, ring, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = if (gradient) Color.White else accent) }
        Spacer(Modifier.height(4.dp))
        Text(if (changed) "$label •" else label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

/** A labelled slider with live value, tinted with the tool's colour. */
@Composable
internal fun ValueSlider(
    label: String, accent: Color, range: ClosedFloatingPointRange<Float>, value: Float,
    onChange: (Float) -> Unit, onDone: () -> Unit, onReset: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            val shown = if (label == "Exposure") "%+.2f".format(value) else "%+d".format((value * 100).roundToInt())
            val pop by animateFloatAsState(if (value != 0f) 1.12f else 1f, Springs.squish(), label = "pop")
            Text(shown, color = accent, fontWeight = FontWeight.Bold, modifier = Modifier.graphicsLayer { scaleX = pop; scaleY = pop })
            TextButton(onClick = onReset) { Text("Reset") }
        }
        val ticks = rememberSliderTicks(range)
        val haptics = LocalHapticFeedback.current
        Slider(
            value = value,
            onValueChange = { ticks.onValue(it) { t -> haptics.performHapticFeedback(t) }; onChange(it) },
            onValueChangeFinished = { ticks.reset(); onDone() },
            valueRange = range,
            colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent, inactiveTrackColor = PanelHigh),
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

@Composable
internal fun ColorChip(label: String, accent: Color, selected: Boolean, swatch: Brush? = null, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) accent.copy(alpha = 0.25f) else PanelHigh, label = "chip")
    Row(
        Modifier.squishClickable(pressedScale = 0.9f, onClick = onClick).clip(RoundedCornerShape(50)).background(bg)
            .border(1.dp, if (selected) accent else Color.Transparent, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (swatch != null) {
            Box(Modifier.size(14.dp).clip(CircleShape).background(swatch))
            Spacer(Modifier.width(6.dp))
        }
        Text(label, style = MaterialTheme.typography.labelLarge, color = if (selected) OnInk else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
