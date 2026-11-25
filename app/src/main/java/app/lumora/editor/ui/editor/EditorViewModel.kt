package app.lumora.editor.ui.editor

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.lumora.editor.ai.Upscaler
import app.lumora.editor.data.Drafts
import app.lumora.editor.data.EditorDraft
import app.lumora.editor.data.PhotoStore
import app.lumora.editor.model.AutoEnhance
import app.lumora.editor.gl.Exporter
import app.lumora.editor.model.EditParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

data class EditorState(
    val sourceUri: Uri? = null,
    val preview: Bitmap? = null,
    val params: EditParams = EditParams(),
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val busy: Boolean = false,
    /** 0..1 while a long task (AI upscale) runs, else null. */
    val progress: Float? = null,
    val busyLabel: String? = null,
    /** True when the AI Enhance pass is applied to the photo being edited. */
    val aiEnhanced: Boolean = false,
    val message: String? = null,
)

class EditorViewModel(app: Application) : AndroidViewModel(app) {
    private val store = PhotoStore(app)
    private val drafts = Drafts(app)
    private val glThread = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state.asStateFlow()

    private var originalPreview: Bitmap? = null

    /** Small previews of each filter on the open photo, keyed by filter name. */
    private val _thumbs = MutableStateFlow<Map<String, Bitmap>>(emptyMap())
    val thumbs: StateFlow<Map<String, Bitmap>> = _thumbs.asStateFlow()
    private var thumbFor: Bitmap? = null
    private val thumbGroups = HashSet<String>()

    /** Renders the filter thumbnails for [group] on the current photo, once per photo. */
    fun loadThumbs(group: app.lumora.editor.model.FilterGroup) {
        val src = _state.value.preview ?: return
        if (thumbFor !== src) { thumbFor = src; thumbGroups.clear(); _thumbs.value = emptyMap() }
        if (!thumbGroups.add(group.name)) return
        viewModelScope.launch {
            val out = runCatching {
                withContext(glThread) {
                    val edge = 132f / maxOf(src.width, src.height)
                    val small = Bitmap.createScaledBitmap(src, maxOf(1, (src.width * edge).toInt()), maxOf(1, (src.height * edge).toInt()), true)
                    val crop = app.lumora.editor.model.CropSpec(aspect = 1f)
                    Exporter.renderAll(small, group.filters.map { it.second.copy(crop = crop) }).also { small.recycle() }
                }
            }.getOrNull() ?: run { thumbGroups.remove(group.name); return@launch }
            if (thumbFor === src) _thumbs.value = _thumbs.value + group.filters.map { it.first }.zip(out)
        }
    }

    /** Applies a filter by name at [strength], keeping the crop. */
    fun applyFilter(name: String, look: EditParams, strength: Float = 1f, commitNow: Boolean = true) {
        val p = _state.value.params.withLook(look, name, strength)
        if (commitNow) commit(p) else preview(p)
    }

    // Undo history holds committed edits; slider drags update `params` live and commit on release.
    private val history = ArrayList<EditParams>()
    private var cursor = -1

    private var pendingEnhance = false

    /** Opens a photo and runs AI Enhance straight away (the home screen's AI Enhance card). */
    fun openAndEnhance(uri: Uri) { pendingEnhance = true; open(uri) }

    /**
     * AI Enhance: the photo is shrunk to half size and rebuilt at 4x by the on-device ESRGAN
     * model, then fitted back to the original size. The model redraws edges and textures, so
     * soft or noisy photos come back crisper. Tapping again switches it off.
     */
    fun toggleAiEnhance() {
        val s = _state.value
        val current = s.preview ?: return
        if (s.busy) return
        if (s.aiEnhanced) {
            val orig = originalPreview ?: return
            originalPreview = null
            _state.update { it.copy(preview = orig, aiEnhanced = false, message = "AI Enhance off") }
            return
        }
        _state.update { it.copy(busy = true, busyLabel = "AI enhancing", progress = 0f) }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.Default) {
                    val half = maxOf(1, minOf(current.width / 2, AI_INPUT_EDGE * current.width / maxOf(current.width, current.height)))
                    val f = half.toFloat() / current.width
                    val small = Bitmap.createScaledBitmap(current, half, maxOf(1, (current.height * f).toInt()), true)
                    val big = Upscaler(getApplication()).use { up ->
                        up.upscale4x(small) { p -> _state.update { it.copy(progress = p) } }
                    }
                    small.recycle()
                    Bitmap.createScaledBitmap(big, current.width, current.height, true).also { if (it !== big) big.recycle() }
                }
            }.onSuccess { enhanced ->
                originalPreview = current
                _state.update { it.copy(preview = enhanced, aiEnhanced = true, busy = false, progress = null, busyLabel = null,
                    message = "AI enhanced. Tap AI again to compare or switch off.") }
            }.onFailure { e ->
                _state.update { it.copy(busy = false, progress = null, busyLabel = null, message = "AI Enhance failed: ${e.message}") }
            }
        }
    }

    /** True when leaving would lose changes. */
    val isDirty: Boolean get() = _state.value.let { it.preview != null && (it.params != EditParams() || it.aiEnhanced) }

    fun saveDraft() {
        val s = _state.value
        val uri = s.sourceUri ?: return
        drafts.saveEditor(EditorDraft(uri, s.params, System.currentTimeMillis()))
        s.preview?.let { p ->
            val thumb = runCatching {
                val f = 360f / maxOf(p.width, p.height)
                val small = Bitmap.createScaledBitmap(p, maxOf(1, (p.width * f).toInt()), maxOf(1, (p.height * f).toInt()), true)
                Exporter.render(small, s.params).also { if (small !== p) small.recycle() }
            }.getOrNull() ?: p
            drafts.saveThumb("editor", thumb)
        }
        close()
    }

    fun discard() { drafts.clearEditor(); close() }

    /** Forgets the current photo so the next visit starts fresh. */
    private fun close() {
        originalPreview = null
        history.clear(); cursor = -1
        _state.value = EditorState()
    }

    fun restoreDraft(d: EditorDraft) {
        open(d.uri, startParams = d.params)
    }

    fun open(uri: Uri) = open(uri, startParams = EditParams())

    private fun open(uri: Uri, startParams: EditParams) {
        drafts.keepAccess(uri)
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { store.decode(uri, PREVIEW_EDGE) } }
                .onSuccess { bmp ->
                    history.clear(); history += startParams; cursor = 0
                    originalPreview = null
                    _state.value = EditorState(sourceUri = uri, preview = bmp, params = startParams)
                    if (pendingEnhance) { pendingEnhance = false; toggleAiEnhance() }
                }
                .onFailure { e -> _state.update { it.copy(busy = false, message = "Couldn't open photo: ${e.message}") } }
        }
    }

    /** Live change while dragging; not yet in undo history. */
    fun preview(params: EditParams) = _state.update { it.copy(params = params) }

    /** Commits the current (or given) params as an undo step. */
    fun commit(params: EditParams = _state.value.params) {
        if (cursor >= 0 && history[cursor] == params) { preview(params); return }
        while (history.size > cursor + 1) history.removeAt(history.lastIndex)
        history += params; cursor = history.lastIndex
        if (history.size > MAX_HISTORY) { history.removeAt(0); cursor-- }
        publish()
    }

    /** Measures the photo and sets the sliders to a balanced starting point. */
    fun autoEnhance() {
        val bmp = _state.value.preview ?: return
        viewModelScope.launch {
            val p = withContext(Dispatchers.Default) {
                val small = Bitmap.createScaledBitmap(bmp, 256, (256f * bmp.height / bmp.width).toInt().coerceAtLeast(1), true)
                val px = IntArray(small.width * small.height)
                small.getPixels(px, 0, small.width, 0, 0, small.width, small.height)
                if (small !== bmp) small.recycle()
                AutoEnhance.analyze(px, _state.value.params)
            }
            commit(p.copy(lookName = null))
            _state.update { it.copy(message = "Auto enhanced. Every slider is still yours to tweak.") }
        }
    }

    fun undo() { if (cursor > 0) { cursor--; publish() } }
    fun redo() { if (cursor < history.lastIndex) { cursor++; publish() } }

    private fun publish() = _state.update {
        it.copy(params = history[cursor], canUndo = cursor > 0, canRedo = cursor < history.lastIndex)
    }

    /**
     * Saves the edit. [scale] 1 saves at full resolution; 2 or 4 runs the on-device AI upscaler.
     */
    fun export(scale: Int = 1) {
        val s = _state.value
        val uri = s.sourceUri ?: return
        if (s.busy) return
        _state.update { it.copy(busy = true, busyLabel = if (scale > 1) "AI upscaling ${scale}x" else "Saving", progress = null) }
        viewModelScope.launch {
            runCatching {
                var out = withContext(glThread) {
                    val maxEdge = minOf(Exporter.maxTextureSize(), EXPORT_EDGE_CAP)
                    if (s.aiEnhanced && s.preview != null) {
                        Exporter.render(s.preview, s.params)
                    } else {
                        val full = withContext(Dispatchers.IO) { store.decode(uri, maxEdge) }
                        Exporter.render(full, s.params).also { full.recycle() }
                    }
                }
                if (scale > 1) out = withContext(Dispatchers.Default) { aiUpscale(out, scale) }
                withContext(Dispatchers.IO) { store.saveJpeg(out) }
                "Saved ${out.width}×${out.height} to Pictures/Vannam".also { out.recycle() }
            }.onSuccess { msg -> _state.update { it.copy(busy = false, progress = null, busyLabel = null, message = msg) } }
             .onFailure { e ->
                 val why = if (e is OutOfMemoryError) "not enough memory, try 2x" else e.message
                 _state.update { it.copy(busy = false, progress = null, busyLabel = null, message = "Save failed: $why") }
             }
        }
    }

    /** Runs the 4x model on an input capped at [AI_INPUT_EDGE], then sizes the result to [scale]x. */
    private fun aiUpscale(edited: Bitmap, scale: Int): Bitmap {
        val long = maxOf(edited.width, edited.height)
        val input = if (long > AI_INPUT_EDGE) {
            val f = AI_INPUT_EDGE.toFloat() / long
            Bitmap.createScaledBitmap(edited, (edited.width * f).toInt().coerceAtLeast(1), (edited.height * f).toInt().coerceAtLeast(1), true)
                .also { if (it !== edited) edited.recycle() }
        } else edited
        val big = Upscaler(getApplication()).use { up ->
            up.upscale4x(input) { p -> _state.update { it.copy(progress = p) } }
        }
        val targetLong = minOf(long * scale, maxOf(big.width, big.height))
        val f = targetLong.toFloat() / maxOf(big.width, big.height)
        input.recycle()
        if (f >= 0.999f) return big
        return Bitmap.createScaledBitmap(big, (big.width * f).toInt(), (big.height * f).toInt(), true).also { big.recycle() }
    }

    fun messageShown() = _state.update { it.copy(message = null) }

    override fun onCleared() { glThread.close() }

    private companion object {
        const val PREVIEW_EDGE = 2048
        const val EXPORT_EDGE_CAP = 8192
        const val MAX_HISTORY = 100
        const val AI_INPUT_EDGE = 1200
    }
}
