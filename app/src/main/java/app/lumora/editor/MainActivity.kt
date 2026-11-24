package app.lumora.editor

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.IntentCompat
import app.lumora.editor.ui.editor.EditorScreen
import app.lumora.editor.ui.editor.EditorViewModel
import app.lumora.editor.ui.home.HomeScreen
import app.lumora.editor.ui.studio.StudioScreen
import app.lumora.editor.ui.studio.StudioViewModel
import app.lumora.editor.ui.theme.LumoraTheme

private enum class Screen { Home, Editor, Studio }

class MainActivity : ComponentActivity() {
    private val vm: EditorViewModel by viewModels()
    private val studio: StudioViewModel by viewModels()
    private var openFromIntent by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) incomingImage(intent)?.let { vm.open(it); openFromIntent = true }
        setContent {
            LumoraTheme {
                var screen by rememberSaveable { mutableStateOf(if (openFromIntent) Screen.Editor else Screen.Home) }
                if (openFromIntent) { screen = Screen.Editor; openFromIntent = false }
                BackHandler(enabled = screen != Screen.Home) { screen = Screen.Home }
                AnimatedContent(
                    targetState = screen,
                    transitionSpec = { (fadeIn(tween(260)) + scaleIn(tween(260), initialScale = 0.97f)) togetherWith fadeOut(tween(180)) },
                    label = "screen",
                ) { s ->
                    when (s) {
                        Screen.Home -> HomeScreen(
                            onEdit = { vm.open(it); screen = Screen.Editor },
                            onEnhance = { vm.openAndEnhance(it); screen = Screen.Editor },
                            onStudio = { screen = Screen.Studio },
                        )
                        Screen.Editor -> EditorScreen(vm, onHome = { screen = Screen.Home })
                        Screen.Studio -> StudioScreen(studio, onBack = { screen = Screen.Home })
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        incomingImage(intent)?.let { vm.open(it); openFromIntent = true }
    }

    private fun incomingImage(intent: Intent?): Uri? = when (intent?.action) {
        Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
        Intent.ACTION_EDIT -> intent.data
        else -> null
    }
}
