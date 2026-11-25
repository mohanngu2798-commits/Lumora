package app.lumora.editor.ui.collage

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.lumora.editor.data.CollageDraft
import app.lumora.editor.data.Drafts
import app.lumora.editor.data.PhotoStore
import app.lumora.editor.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

data class CollageState(
    val spec: CollageSpec = CollageSpec(),
    val uris: List<Uri> = emptyList(),
    val photos: List<Bitmap> = emptyList(),
    val selected: Int? = null,
    val busy: Boolean = false,
    val message: String? = null,
)

class CollageViewModel(app: Application) : AndroidViewModel(app) {
    private val store = PhotoStore(app)
    private val drafts = Drafts(app)
    private val _state = MutableStateFlow(CollageState())
    val state: StateFlow<CollageState> = _state.asStateFlow()
    private var savedSpec: CollageSpec? = null
    private var seed = 7

    val isDirty: Boolean get() = _state.value.photos.isNotEmpty() && _state.value.spec != savedSpec

    fun start(mode: CollageMode, uris: List<Uri>) {
        if (uris.isEmpty()) return
        val limit = if (mode == CollageMode.Grid) GridTemplates.MAX_PHOTOS else MAX_FREE
        load(uris.take(limit)) { photos -> fresh(mode, photos.size) }
    }

    private fun fresh(mode: CollageMode, n: Int, base: CollageSpec = CollageSpec(mode = mode,
        spacing = if (mode == CollageMode.Grid) 0.02f else 0f, background = if (mode == CollageMode.Grid) WHITE else 0xFFF3EEE6.toInt())) =
        base.copy(cells = (0 until n).map { CellPhoto(it) }, items = GridTemplates.scatter(n, seed))

    private fun load(uris: List<Uri>, append: Boolean = false, spec: (List<Bitmap>) -> CollageSpec) {
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            runCatching {
                // Keep memory in check: big collages hold many photos, so each is decoded smaller.
                val total = uris.size + if (append) _state.value.photos.size else 0
                val edge = if (total > 9) 1000 else PHOTO_EDGE
                withContext(Dispatchers.IO) { uris.map { store.decode(it, edge) } }
            }.onSuccess { bmps ->
                uris.forEach(drafts::keepAccess)
                _state.update {
                    val photos = if (append) it.photos + bmps else bmps
                    val allUris = if (append) it.uris + uris else uris
                    it.copy(photos = photos, uris = allUris, spec = spec(photos), busy = false, selected = null)
                }
            }.onFailure { e -> _state.update { it.copy(busy = false, message = "Couldn't open photos: ${e.message}") } }
        }
    }

    fun addPhotos(uris: List<Uri>) {
        val s = _state.value
        val limit = (if (s.spec.mode == CollageMode.Grid) GridTemplates.MAX_PHOTOS else MAX_FREE) - s.photos.size
        if (limit <= 0) { _state.update { it.copy(message = "That's the most photos this collage can hold.") }; return }
        load(uris.take(limit), append = true) { photos ->
            val old = s.spec
            val start = s.photos.size
            old.copy(
                template = 0,
                cells = old.cells + (start until photos.size).map { CellPhoto(it) },
                items = old.items + GridTemplates.scatter(photos.size, seed).drop(start),
            )
        }
    }

    private fun edit(f: (CollageSpec) -> CollageSpec) = _state.update { it.copy(spec = f(it.spec)) }

    fun setMode(m: CollageMode) = edit { it.copy(mode = m) }.also { _state.update { it.copy(selected = null) } }
    fun setTemplate(i: Int) = edit { it.copy(template = i) }
    fun setAspect(a: Float) = edit { it.copy(aspect = a) }
    fun setSpacing(v: Float) = edit { it.copy(spacing = v) }
    fun setRadius(v: Float) = edit { it.copy(radius = v) }
    fun setBackground(c: Int) = edit { it.copy(background = c) }
    fun toggleFrames() = edit { it.copy(frames = !it.frames) }
    fun toggleShadows() = edit { it.copy(shadows = !it.shadows) }

    /** Grid: rotate which photo sits in which cell. Freestyle: toss the prints again. */
    fun shuffle() = edit { s ->
        if (s.mode == CollageMode.Grid) s.copy(cells = s.cells.drop(1) + s.cells.take(1))
        else { seed++; s.copy(items = GridTemplates.scatter(s.items.size, seed).mapIndexed { i, f -> f.copy(photo = s.items[i].photo) }) }
    }

    fun select(i: Int?) {
        _state.update { st ->
            if (i != null && st.spec.mode == CollageMode.Free && i in st.spec.items.indices && i != st.spec.items.lastIndex) {
                // Bring the touched print to the front.
                val items = st.spec.items.toMutableList(); val it = items.removeAt(i); items += it
                st.copy(spec = st.spec.copy(items = items), selected = items.lastIndex)
            } else st.copy(selected = i)
        }
    }

    /** Applies a pinch/drag/twist to the selected cell or print. [pan] is in canvas pixels. */
    fun transform(w: Float, h: Float, panX: Float, panY: Float, zoom: Float, rotation: Float) {
        val st = _state.value; val i = st.selected ?: return
        val s = st.spec
        if (s.mode == CollageMode.Grid) {
            val cp = s.cells.getOrNull(i) ?: return
            val bmp = st.photos.getOrNull(cp.photo) ?: return
            val cell = s.layout(s.cells.size).getOrNull(i) ?: return
            val rect = CollageRenderer.cellRect(cell.x, cell.y, cell.w, cell.h, w, h, s.spacing * minOf(w, h))
            val nz = (cp.zoom * zoom).coerceIn(1f, 5f)
            val scale = max(rect.width() / bmp.width, rect.height() / bmp.height) * nz
            val slackX = (bmp.width * scale - rect.width()) / 2; val slackY = (bmp.height * scale - rect.height()) / 2
            val nx = if (slackX > 0.5f) (cp.panX + panX / slackX).coerceIn(-1f, 1f) else 0f
            val ny = if (slackY > 0.5f) (cp.panY + panY / slackY).coerceIn(-1f, 1f) else 0f
            edit { it.copy(cells = it.cells.toMutableList().also { l -> l[i] = cp.copy(panX = nx, panY = ny, zoom = nz) }) }
        } else {
            val it0 = s.items.getOrNull(i) ?: return
            val n = it0.copy(
                cx = (it0.cx + panX / w).coerceIn(-0.2f, 1.2f), cy = (it0.cy + panY / h).coerceIn(-0.2f, 1.2f),
                width = (it0.width * zoom).coerceIn(0.08f, 1.6f), rotation = it0.rotation + rotation,
            )
            edit { it.copy(items = it.items.toMutableList().also { l -> l[i] = n }) }
        }
    }

    /** Grid: swap the selected cell's photo with the next cell's. */
    fun swapWithNext() {
        val i = _state.value.selected ?: return
        edit { s ->
            if (s.cells.size < 2) return@edit s
            val j = (i + 1) % s.cells.size
            val l = s.cells.toMutableList(); val a = l[i]; l[i] = l[j].copy(panX = 0f, panY = 0f, zoom = 1f); l[j] = a.copy(panX = 0f, panY = 0f, zoom = 1f)
            s.copy(cells = l)
        }
        _state.update { it.copy(selected = (i + 1) % it.spec.cells.size) }
    }

    fun removeSelected() {
        val st = _state.value; val i = st.selected ?: return
        val s = st.spec
        val photo = if (s.mode == CollageMode.Grid) s.cells.getOrNull(i)?.photo else s.items.getOrNull(i)?.photo
        photo ?: return
        if (st.photos.size <= 1) return
        // Drop the photo everywhere and renumber the rest.
        fun re(p: Int) = if (p > photo) p - 1 else p
        _state.update {
            it.copy(
                photos = it.photos.filterIndexed { k, _ -> k != photo }, uris = it.uris.filterIndexed { k, _ -> k != photo },
                spec = s.copy(template = 0,
                    cells = s.cells.filter { c -> c.photo != photo }.map { c -> c.copy(photo = re(c.photo)) },
                    items = s.items.filter { f -> f.photo != photo }.map { f -> f.copy(photo = re(f.photo)) }),
                selected = null,
            )
        }
    }

    fun save() {
        val st = _state.value
        if (st.photos.isEmpty() || st.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.Default) {
                    val out = CollageRenderer.export(st.spec, st.photos)
                    try { store.saveJpeg(out, 95, "Vannam_Collage_${System.currentTimeMillis()}") } finally { out.recycle() }
                }
            }.onSuccess {
                savedSpec = st.spec; drafts.clearCollage()
                _state.update { it.copy(busy = false, message = "Saved to Pictures/Vannam") }
            }.onFailure { e -> _state.update { it.copy(busy = false, message = "Couldn't save: ${e.message}") } }
        }
    }

    fun saveDraft() {
        val st = _state.value
        if (st.photos.isNotEmpty()) drafts.saveCollage(CollageDraft(st.spec, st.uris, System.currentTimeMillis()))
        close()
    }

    fun discard() { drafts.clearCollage(); close() }

    fun close() { savedSpec = null; _state.value = CollageState() }

    fun restoreDraft(d: CollageDraft) {
        load(d.uris) { photos ->
            val n = photos.size
            d.spec.copy(cells = d.spec.cells.filter { it.photo < n }, items = d.spec.items.filter { it.photo < n })
        }
    }

    fun messageShown() = _state.update { it.copy(message = null) }

    companion object {
        const val PHOTO_EDGE = 1400
        const val MAX_FREE = 20
        const val WHITE = 0xFFFFFFFF.toInt()
    }
}
