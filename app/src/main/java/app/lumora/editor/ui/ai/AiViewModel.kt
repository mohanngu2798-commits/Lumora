package app.lumora.editor.ui.ai

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.lumora.editor.ai.FaceRestorer
import app.lumora.editor.ai.Upscaler
import app.lumora.editor.data.PhotoStore
import app.lumora.editor.gl.Exporter
import app.lumora.editor.model.AutoEnhance
import kotlinx.coroutines.Job
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
    /** Area to zoom the compare view to (a restored face), in source pixels. */
    val focus: android.graphics.RectF? = null,
)

/** AI Studio: one photo in, one AI-processed photo out, compared with a before/after slider. */
class AiViewModel(app: Application) : AndroidViewModel(app) {
    private val store = PhotoStore(app)
    private val _state = MutableStateFlow(AiState())
    val state: StateFlow<AiState> = _state.asStateFlow()
    val facesAvailable = FaceRestorer.available(app)

    private var job: Job? = null
    private var generation = 0

    fun open(uri: Uri, mode: AiMode = _state.value.mode) {
        job?.cancel()
        val gen = ++generation
        _state.value = AiState(busy = true, mode = mode)
        job = viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { store.decode(uri, SOURCE_EDGE) } }
                .onSuccess { bmp -> if (gen == generation) { _state.update { it.copy(source = bmp, busy = false) }; run(mode) } }
                .onFailure { e -> if (gen == generation) _state.update { it.copy(busy = false, message = "Couldn't open photo: ${e.message}") } }
        }
    }

    /** Runs [mode] on the open photo. A new run replaces any run still in progress. */
    fun run(mode: AiMode) {
        val src = _state.value.source ?: return
        job?.cancel()
        val gen = ++generation
        _state.update { it.copy(mode = mode, busy = true, progress = 0f, label = mode.label, result = null, focus = null) }
        job = viewModelScope.launch {
            val me = coroutineContext[Job]
            val stop = { me?.isActive == false || gen != generation }
            // Progress only ever moves forward, and only for the current run.
            var shown = 0f
            var focus: android.graphics.RectF? = null
            val report = { p: Float -> if (!stop() && p >= shown) { shown = p; _state.update { it.copy(progress = p) } } }
            runCatching {
                withContext(Dispatchers.Default) {
                    val app = getApplication<Application>()
                    when (mode) {
                        AiMode.Enhance -> {
                            val detail = enhance(app, src, { report(it * 0.9f) }, stop)
                            tone(detail).also { report(1f) }
                        }
                        AiMode.Restore -> {
                            val base = enhance(app, src, { report(it * 0.5f) }, stop).let { b ->
                                if (b.isMutable) b else b.copy(Bitmap.Config.ARGB_8888, true).also { b.recycle() }
                            }
                            var n = 0
                            if (facesAvailable) FaceRestorer(app).use { fr ->
                                n = fr.restoreFaces(src, base) { p -> report(0.5f + p * 0.4f) }
                                fr.lastFaces.maxByOrNull { (it.right - it.left) * (it.bottom - it.top) }?.let { f ->
                                    val cx = (f.left + f.right) / 2; val cy = (f.top + f.bottom) / 2
                                    val r = maxOf(f.right - f.left, f.bottom - f.top) * 1.6f
                                    focus = android.graphics.RectF(cx - r, cy - r, cx + r, cy + r)
                                }
                            }
                            if (stop()) throw java.util.concurrent.CancellationException()
                            if (n == 0 && facesAvailable) post("No faces found, so the whole photo was enhanced")
                            else if (n > 0) post("Restored $n face" + if (n > 1) "s" else "")
                            tone(base).also { report(1f) }
                        }
                        AiMode.Upscale2, AiMode.Upscale4 -> {
                            val scale = if (mode == AiMode.Upscale2) 2 else 4
                            val input = fit(src, UPSCALE_INPUT_EDGE)
                            val big = Upscaler(app).use { it.upscale4x(input, report, stop) }
                            if (input !== src) input.recycle()
                            val targetW = src.width * scale; val targetH = src.height * scale
                            if (big.width <= targetW) big
                            else Bitmap.createScaledBitmap(big, targetW, targetH, true).also { big.recycle() }
                        }
                    }
                }
            }.onSuccess { out ->
                if (gen == generation) _state.update { it.copy(result = out, busy = false, progress = null, label = null, focus = focus) }
                else out.recycle()
            }.onFailure { e ->
                if (gen != generation || e is java.util.concurrent.CancellationException) return@onFailure
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

    fun clear() { job?.cancel(); generation++; _state.value = AiState() }
    fun messageShown() = _state.update { it.copy(message = null) }

    private fun post(m: String) = _state.update { it.copy(message = m) }

    private fun fit(b: Bitmap, edge: Int): Bitmap {
        val long = maxOf(b.width, b.height)
        if (long <= edge) return b
        val f = edge.toFloat() / long
        return Bitmap.createScaledBitmap(b, (b.width * f).toInt().coerceAtLeast(1), (b.height * f).toInt().coerceAtLeast(1), true)
    }

    /**
     * The visible half of Enhance: measured auto-tone plus extra clarity, dehaze and colour, rendered on the GPU.
     * The AI pass rebuilds fine detail; this makes the improvement obvious at phone-screen size, as people expect.
     */
    private fun tone(b: Bitmap): Bitmap {
        val small = Bitmap.createScaledBitmap(b, 256, maxOf(1, 256 * b.height / b.width), true)
        val px = IntArray(small.width * small.height).also { small.getPixels(it, 0, small.width, 0, 0, small.width, small.height) }
        small.recycle()
        val p = AutoEnhance.analyze(px).let {
            it.copy(
                clarity = 0.3f, sharpen = 0.35f, dehaze = maxOf(it.dehaze, 0.18f), vibrance = it.vibrance + 0.12f,
                contrast = it.contrast + 0.06f, shadows = it.shadows + 0.1f, highlights = it.highlights - 0.1f,
            )
        }
        return runCatching { Exporter.render(b, p) }.getOrDefault(b).also { if (it !== b) b.recycle() }
    }

    companion object {
        private const val SOURCE_EDGE = 2048
        private const val UPSCALE_INPUT_EDGE = 1200

        /** Same-size AI detail rebuild: shrink to half, run the 4x model, fit back. */
        fun enhance(app: Application, src: Bitmap, progress: (Float) -> Unit, cancelled: () -> Boolean = { false }): Bitmap {
            val half = maxOf(1, minOf(src.width / 2, 1200 * src.width / maxOf(src.width, src.height)))
            val f = half.toFloat() / src.width
            val small = Bitmap.createScaledBitmap(src, half, maxOf(1, (src.height * f).toInt()), true)
            val big = Upscaler(app).use { it.upscale4x(small, progress, cancelled) }
            small.recycle()
            return Bitmap.createScaledBitmap(big, src.width, src.height, true).also { if (it !== big) big.recycle() }
        }
    }
}
