package app.lumora.editor

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.content.IntentCompat
import app.lumora.editor.ui.editor.EditorScreen
import app.lumora.editor.ui.editor.EditorViewModel
import app.lumora.editor.ui.theme.LumoraTheme

class MainActivity : ComponentActivity() {
    private val vm: EditorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) incomingImage(intent)?.let(vm::open)
        setContent { LumoraTheme { EditorScreen(vm) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        incomingImage(intent)?.let(vm::open)
    }

    private fun incomingImage(intent: Intent?): Uri? = when (intent?.action) {
        Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
        Intent.ACTION_EDIT -> intent.data
        else -> null
    }
}
