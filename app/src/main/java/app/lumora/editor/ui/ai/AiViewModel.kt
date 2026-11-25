package app.lumora.editor.ui.ai

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.lumora.editor.ai.FaceRestorer
import app.lumora.editor.ai.Upscaler
import app.lumora.editor.data.PhotoStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AiMode(val label: String, val blurb: String) {
    Restore("Restore", "Sharpens the photo and rebuilds faces with realistic detail"),
    Enhance("Enhance", "Recovers detail and cleans up noise, same size"),
    Upscale2("Upscale 2x", "Twice the resolution, AI-sharpened"),
    Upscale4("Upscale 4x", "Four times the resolution. Takes longer"),
}

data class AiState(
    val source: Bitmap? = null,
    val result: Bitmap? = null,
    val mode: AiMode = AiMode.Restore,
    val busy: Boolean = false,
    val progress: Float? = null,
    val label: String? = null,
    val message: String? = null,
)

/** AI Studio: one photo in, one AI-processed photo out, compared with a before/after slider. */
class AiViewModel(app: Application) : AndroidViewModel(app) {
    private val store = PhotoStore(app)
    private val _state = MutableStateFlow(AiState())
    val state: StateFlow<AiState> = _state.asStateFlow()
    val facesAvailable = FaceRestorer.available(app)

    fun open(uri: Uri, mode: AiMode = _state.value.mode) {
        _state.value = AiState(busy = true, mode = mode)
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { store.decode(uri, SOURCE_EDGE) } }
                .onSuccess { bmp -> _state.update { it.copy(source = bmp, busy = false) }; run(mode) }
                .onFailure { e -> _state.update { it.copy(busy = false, message = "Couldn't open photo: ${e.message}") } }
        }
    }

    fun run(mode: AiMode) {
        val src = _state.value.source ?: return
        if (_state.value.busy && _state.value.result != null) return
        _state.update { it.copy(mode = mode, busy = true, progress = 0f, label = mode.label, result = null) }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.Default) {
                    val app = getApplication<Application>()
                    when (mode) {
                        AiMode.Enhance -> enhance(app, src) { p -> report(p) }
                        AiMode.Restore -> {
                            val base = enhance(app, src) { p -> report(p * 0.6f) }.copy(Bitmap.Config.ARGB_8888, true)
                            val n = FaceRestorer(app).use { it.restoreFaces(src, base) { p -> report(0.6f + p * 0.4f) } }
                            if (n == 0 && facesAvailable) post("No faces found, so only the photo was enhanced")
                            if (!facesAvailable) post("Face restoration model isn't in this build yet. The photo was enhanced.")
                            base
                        }
                        AiMode.Upscale2, AiMode.Upscale4 -> {
                            val scale = if (mode == AiMode.Upscale2) 2 else 4
                            val input = fit(src, UPSCALE_INPUT_EDGE)
                            val big = Upscaler(app).use { it.upscale4x(input) { p -> report(p) } }
                            if (input !== src) input.recycle()
                            val targetW = src.width * scale; val targetH = src.height * scale
                            if (big.width <= targetW) big
                            else Bitmap.createScaledBitmap(big, targetW, targetH, true).also { big.recycle() }
                        }
                    }
                }
            }.onSuccess { out -> _state.update { it.copy(result = out, busy = false, progress = null, label = null) } }
             .onFailure { e ->
                 val why = if (e is OutOfMemoryError) "not enough memory, try a smaller photo" else e.message
                 _state.update { it.copy(busy = false, progress = null, label = null, message = "AI failed: $why") }
             }
        }
    }

    fun save() {
        val out = _state.value.result ?: return
        _state.update { it.copy(busy = true, label = "Saving") }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { store.saveJpeg(out, quality = 96) } }
                .onSuccess { _state.update { it.copy(busy = false, label = null, message = "Saved ${out.width}×${out.height} to Pictures/Vannam") } }
                .onFailure { e -> _state.update { it.copy(busy = false, label = null, message = "Save failed: ${e.message}") } }
        }
    }

    fun clear() { _state.value = AiState() }
    fun messageShown() = _state.update { it.copy(message = null) }

    private fun report(p: Float) = _state.update { it.copy(progress = p) }
    private fun post(m: String) = _state.update { it.copy(message = m) }

    private fun fit(b: Bitmap, edge: Int): Bitmap {
        val long = maxOf(b.width, b.height)
        if (long <= edge) return b
        val f = edge.toFloat() / long
        return Bitmap.createScaledBitmap(b, (b.width * f).toInt().coerceAtLeast(1), (b.height * f).toInt().coerceAtLeast(1), true)
    }

    companion object {
        private const val SOURCE_EDGE = 2048
        private const val UPSCALE_INPUT_EDGE = 1200

        /** Same-size AI detail rebuild: shrink to half, run the 4x model, fit back. */
        fun enhance(app: Application, src: Bitmap, progress: (Float) -> Unit): Bitmap {
            val half = maxOf(1, minOf(src.width / 2, 1200 * src.width / maxOf(src.width, src.height)))
            val f = half.toFloat() / src.width
            val small = Bitmap.createScaledBitmap(src, half, maxOf(1, (src.height * f).toInt()), true)
            val big = Upscaler(app).use { it.upscale4x(small, progress) }
            small.recycle()
            return Bitmap.createScaledBitmap(big, src.width, src.height, true).also { if (it !== big) big.recycle() }
        }
    }
}
