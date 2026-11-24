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
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.runtime.CompositionLocalProvider
import app.lumora.editor.ui.motion.LocalScreenScope
import app.lumora.editor.ui.motion.LocalSharedScope
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

@OptIn(ExperimentalSharedTransitionApi::class)
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
                SharedTransitionLayout {
                AnimatedContent(
                    targetState = screen,
                    transitionSpec = { fadeIn(tween(300, delayMillis = 60)) togetherWith fadeOut(tween(220)) },
                    label = "screen",
                ) { s ->
                    CompositionLocalProvider(LocalSharedScope provides this@SharedTransitionLayout, LocalScreenScope provides this) {
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
