package app.lumora.editor.ui.pro

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.lumora.editor.data.Pro
import app.lumora.editor.ui.theme.*

/** The Vannam Pro plans. During the launch offer everything is free and no payment is taken. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlansSheet(onDismiss: () -> Unit, reason: String? = null) {
    val context = LocalContext.current
    val offer = Pro.offerActive()
    var chosen by remember { mutableStateOf(Pro.plans.last().id) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Ink) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(listOf(Magenta, Saffron))),
                    contentAlignment = Alignment.Center) { Icon(Icons.Default.WorkspacePremium, null, tint = Color.White) }
                Spacer(Modifier.width(12.dp))
                Text("Vannam Pro", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            }
            if (reason != null) {
                Spacer(Modifier.height(8.dp))
                Text(reason, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(16.dp))
            Pro.perks.forEach { line ->
                Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Check, null, tint = OnInk, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(line)
                }
            }
            Spacer(Modifier.height(16.dp))
            Pro.plans.forEach { p ->
                val on = p.id == chosen
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(18.dp))
                        .border(if (on) 2.dp else 0.dp, if (on) OnInk else Color.Transparent, RoundedCornerShape(18.dp))
                        .background(PanelHigh).clickable { chosen = p.id }.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(p.title, fontWeight = FontWeight.SemiBold)
                            if (p.badge != null) {
                                Spacer(Modifier.width(8.dp))
                                Text(p.badge, style = MaterialTheme.typography.labelSmall, color = Ink,
                                    modifier = Modifier.clip(RoundedCornerShape(50)).background(OnInk).padding(horizontal = 8.dp, vertical = 2.dp))
                            }
                        }
                        Text(p.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(p.price, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(12.dp))
            if (offer) {
                val ends = Pro.offerEnds
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Brush.linearGradient(listOf(Magenta, Saffron))).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.CardGiftcard, null, tint = Color.White)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(if (ends == 0L) "Launch offer: every Pro feature is free" else "Launch offer: Pro is free till ${dateOf(ends)}",
                            color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text("No payment details needed. Nothing is charged.", color = Color.White.copy(alpha = 0.9f),
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Enjoy Pro for free") }
            } else {
                val left = Pro.aiLeft(context)
                Text(if (left == 0) "You've used today's ${Pro.FREE_AI_PER_DAY} free AI edits. They refill tomorrow."
                    else "Free plan: ${Pro.FREE_AI_PER_DAY} AI edits a day ($left left today).",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(12.dp))
                // Store billing is connected in the release that follows the launch offer.
                Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Payments open soon") }
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Not now") }
            }
        }
    }
}

fun dateOf(millis: Long): String = java.text.SimpleDateFormat("d MMM", java.util.Locale.getDefault()).format(java.util.Date(millis))
