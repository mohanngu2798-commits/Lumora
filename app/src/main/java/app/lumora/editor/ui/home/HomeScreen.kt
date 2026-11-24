package app.lumora.editor.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lumora.editor.R
import app.lumora.editor.ui.motion.squishClickable
import app.lumora.editor.ui.theme.*
import kotlinx.coroutines.delay

/** Start screen: three clear ways in, lots of air, nothing else. */
@Composable
fun HomeScreen(onEdit: (android.net.Uri) -> Unit, onEnhance: (android.net.Uri) -> Unit, onStudio: () -> Unit) {
    val editPicker = rememberLauncherForActivityResult(PickVisualMedia()) { it?.let(onEdit) }
    val enhancePicker = rememberLauncherForActivityResult(PickVisualMedia()) { it?.let(onEnhance) }
    val image = PickVisualMediaRequest(PickVisualMedia.ImageOnly)

    Column(
        Modifier.fillMaxSize().background(Ink).statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.ic_logo), contentDescription = null, modifier = Modifier.size(34.dp))
            Spacer(Modifier.width(10.dp))
            Text("Vannamayam", maxLines = 1, softWrap = false,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold))
        }
        Spacer(Modifier.height(36.dp))
        Text("What are we\ncreating today?", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text("Pro editing tools, all free.", style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(28.dp))

        Appear(0) {
            HeroCard(onClick = { editPicker.launch(image) })
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Appear(1, Modifier.weight(1f)) {
                FeatureCard(Icons.Default.ViewCarousel, "Instagram Studio", "Seamless carousels and filters", CoolGradient, onStudio)
            }
            Appear(2, Modifier.weight(1f)) {
                FeatureCard(Icons.Default.AutoAwesome, "AI Enhance", "Sharper, cleaner photos", BrandGradient) { enhancePicker.launch(image) }
            }
        }
        Spacer(Modifier.height(40.dp))
        Row(Modifier.fillMaxWidth().padding(vertical = 20.dp), horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text("Private by design. Your photos never leave your phone.",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Cards rise and fade in one after another on first show. */
@Composable
private fun Appear(index: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { delay(70L * index); a.animateTo(1f, tween(450)) }
    Box(modifier.graphicsLayer { alpha = a.value; translationY = (1f - a.value) * 40f }) { content() }
}

@Composable
private fun HeroCard(onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(180.dp).squishClickable(pressedScale = 0.97f, onClick = onClick)
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(Magenta, Saffron, Turmeric)))
            .padding(22.dp),
    ) {
        Column(Modifier.align(Alignment.BottomStart)) {
            Text("Edit a photo", color = Color.White, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(4.dp))
            Text("Looks, tools, curves, crop and AI upscale", color = Color.White.copy(alpha = 0.9f),
                style = MaterialTheme.typography.bodyMedium)
        }
        Box(
            Modifier.align(Alignment.TopEnd).size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.25f)),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.AutoMirrored.Filled.ArrowForward, "Open a photo", tint = Color.White) }
    }
}

@Composable
private fun FeatureCard(icon: ImageVector, title: String, subtitle: String, accent: Brush, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().height(170.dp).squishClickable(pressedScale = 0.95f, onClick = onClick)
            .clip(RoundedCornerShape(24.dp)).background(PanelHigh).padding(18.dp),
    ) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(accent), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White)
        }
        Spacer(Modifier.weight(1f))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Spacer(Modifier.height(2.dp))
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
    }
}
