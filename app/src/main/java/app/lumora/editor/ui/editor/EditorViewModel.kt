package app.lumora.editor.ui.editor

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.lumora.editor.data.PhotoStore
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
    val message: String? = null,
)

class EditorViewModel(app: Application) : AndroidViewModel(app) {
    private val store = PhotoStore(app)
    private val glThread = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state.asStateFlow()

    // Undo history holds committed edits; slider drags update `params` live and commit on release.
    private val history = ArrayList<EditParams>()
    private var cursor = -1

    fun open(uri: Uri) {
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { store.decode(uri, PREVIEW_EDGE) } }
                .onSuccess { bmp ->
                    history.clear(); history += EditParams(); cursor = 0
                    _state.value = EditorState(sourceUri = uri, preview = bmp)
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

    fun undo() { if (cursor > 0) { cursor--; publish() } }
    fun redo() { if (cursor < history.lastIndex) { cursor++; publish() } }

    private fun publish() = _state.update {
        it.copy(params = history[cursor], canUndo = cursor > 0, canRedo = cursor < history.lastIndex)
    }

    fun export() {
        val s = _state.value
        val uri = s.sourceUri ?: return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            runCatching {
                withContext(glThread) {
                    val maxEdge = minOf(Exporter.maxTextureSize(), EXPORT_EDGE_CAP)
                    val full = withContext(Dispatchers.IO) { store.decode(uri, maxEdge) }
                    val out = Exporter.render(full, s.params)
                    full.recycle()
                    withContext(Dispatchers.IO) { store.saveJpeg(out) }.also { out.recycle() }
                }
            }.onSuccess { _state.update { it.copy(busy = false, message = "Saved to Pictures/Vannamayam") } }
             .onFailure { e -> _state.update { it.copy(busy = false, message = "Export failed: ${e.message}") } }
        }
    }

    fun messageShown() = _state.update { it.copy(message = null) }

    override fun onCleared() { glThread.close() }

    private companion object {
        const val PREVIEW_EDGE = 2048
        const val EXPORT_EDGE_CAP = 8192
        const val MAX_HISTORY = 100
    }
}
