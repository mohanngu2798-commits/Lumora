package app.lumora.editor.ui.editor

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.lumora.editor.ui.theme.*

/** Asked when leaving with unsaved work: keep it as a draft, throw it away, or stay. */
@Composable
fun ExitDialog(onSaveDraft: () -> Unit, onDiscard: () -> Unit, onStay: () -> Unit) {
    AlertDialog(
        onDismissRequest = onStay,
        containerColor = PanelBg,
        title = { Text("Leave this edit?") },
        text = { Text("Save it as a draft to pick up later from the home screen, or discard your changes.") },
        confirmButton = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GradientButton("Save draft", modifier = Modifier.fillMaxWidth(), onClick = onSaveDraft)
                OutlinedButton(onClick = onDiscard, modifier = Modifier.fillMaxWidth()) {
                    Text("Discard", color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onStay, modifier = Modifier.fillMaxWidth()) { Text("Keep editing", color = OnInk) }
            }
        },
    )
}
