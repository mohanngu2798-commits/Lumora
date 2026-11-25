package app.lumora.editor

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
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
import app.lumora.editor.ui.intro.IntroScreen
import app.lumora.editor.data.Prefs
import app.lumora.editor.data.Drafts
import app.lumora.editor.ui.ai.AiStudioScreen
import app.lumora.editor.ui.ai.AiViewModel
import app.lumora.editor.ui.collage.CollageScreen
import app.lumora.editor.ui.collage.CollageViewModel
import app.lumora.editor.ui.studio.StudioScreen
import app.lumora.editor.ui.studio.StudioViewModel
import app.lumora.editor.ui.theme.LumoraTheme

private enum class Screen { Intro, Home, Editor, Studio, Ai, Collage }

@OptIn(ExperimentalSharedTransitionApi::class)
class MainActivity : ComponentActivity() {
    private val vm: EditorViewModel by viewModels()
    private val studio: StudioViewModel by viewModels()
    private val ai: AiViewModel by viewModels()
    private val collage: CollageViewModel by viewModels()
    private val drafts by lazy { Drafts(this) }
    private var openFromIntent by mutableStateOf(false)
    private val prefs by lazy { Prefs(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) incomingImage(intent)?.let { vm.open(it); openFromIntent = true }
        setContent {
            LumoraTheme {
                // Surface sets the default text and icon colour for the theme (white in dark mode).
                androidx.compose.material3.Surface(color = androidx.compose.material3.MaterialTheme.colorScheme.background) {
                var screen by rememberSaveable { mutableStateOf(
                    when { openFromIntent -> Screen.Editor; !prefs.onboarded -> Screen.Intro; else -> Screen.Home }) }
                if (openFromIntent) { screen = Screen.Editor; openFromIntent = false }
                var studioFirst by rememberSaveable { mutableStateOf(prefs.studioFirst) }
                SharedTransitionLayout {
                AnimatedContent(
                    targetState = screen,
                    transitionSpec = { fadeIn(tween(300, delayMillis = 60)) togetherWith fadeOut(tween(220)) },
                    label = "screen",
                ) { s ->
                    CompositionLocalProvider(LocalSharedScope provides this@SharedTransitionLayout, LocalScreenScope provides this) {
                    when (s) {
                        Screen.Intro -> IntroScreen { a ->
                            prefs.use = a.use; prefs.interests = a.interests; prefs.postTypes = a.postTypes
                            prefs.onboarded = true
                            studioFirst = prefs.studioFirst
                            screen = Screen.Home
                        }
                        Screen.Home -> HomeScreen(
                            studioFirst = studioFirst,
                            premiumUntil = app.lumora.editor.data.Pro.offerEnds,
                            onEdit = { vm.open(it); screen = Screen.Editor },
                            onEnhance = { vm.openAndEnhance(it); screen = Screen.Editor },
                            onStudio = { screen = Screen.Studio },
                            onAi = { screen = Screen.Ai },
                            editorDraft = drafts.editor(),
                            studioDraft = drafts.studio(),
                            onResumeEditor = { d -> vm.restoreDraft(d); screen = Screen.Editor },
                            onResumeStudio = { d -> studio.restoreDraft(d); screen = Screen.Studio },
                            onCollage = { screen = Screen.Collage },
                            collageDraft = drafts.collage(),
                            onResumeCollage = { d -> collage.restoreDraft(d); screen = Screen.Collage },
                        )
                        Screen.Editor -> EditorScreen(vm, onHome = { screen = Screen.Home })
                        Screen.Studio -> StudioScreen(studio, onBack = { screen = Screen.Home }, onCollage = { screen = Screen.Collage })
                        Screen.Ai -> AiStudioScreen(ai, onBack = { screen = Screen.Home })
                        Screen.Collage -> CollageScreen(collage, onBack = { screen = Screen.Home })
                    }
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
