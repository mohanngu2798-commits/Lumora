package app.lumora.editor.ui.studio

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.lumora.editor.data.Drafts
import app.lumora.editor.data.PhotoStore
import app.lumora.editor.data.StudioDraft
import app.lumora.editor.gl.Blur
import app.lumora.editor.gl.Exporter
import app.lumora.editor.model.InstaLooks
import app.lumora.editor.model.FilterLibrary
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors
import app.lumora.editor.model.Background
import app.lumora.editor.model.Carousel
import app.lumora.editor.model.CarouselLayouts
import app.lumora.editor.model.Layer
import app.lumora.editor.model.SlideFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class StudioState(
    val carousel: Carousel = Carousel(),
    val photos: Map<Long, Bitmap> = emptyMap(),
    val uris: Map<Long, Uri> = emptyMap(),
    /** Photo ids in the order they were added, used by the auto layouts. */
    val order: List<Long> = emptyList(),
    val blurred: Bitmap? = null,
    /** Filtered copies of photos; the painter prefers these over the originals. */
    val filtered: Map<Long, Bitmap> = emptyMap(),
    /** Filter name per photo id. */
    val filters: Map<Long, String> = emptyMap(),
    val selected: Long? = null,
    val busy: Boolean = false,
    val message: String? = null,
)

enum class AutoLayout(val label: String) { Seams("Seamless"), Panorama("Panorama"), OnePerSlide("One per slide"), Dump("Photo dump") }

class StudioViewModel(app: Application) : AndroidViewModel(app) {
    private val store = PhotoStore(app)
    private val drafts = Drafts(app)
    private val _state = MutableStateFlow(StudioState())
    val state: StateFlow<StudioState> = _state.asStateFlow()
    private var nextId = 1L
    private var lastLayout = AutoLayout.Seams
    private val glThread = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    /** Applies a filter to the selected photo, or to every photo when [all] is true. */
    fun applyFilter(name: String, all: Boolean) {
        val s = _state.value
        val ids = if (all) s.order else listOfNotNull(s.selected)
        if (ids.isEmpty() || s.busy) return
        val look = (InstaLooks.all + FilterLibrary.all).firstOrNull { it.first == name }?.second ?: return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            runCatching {
                withContext(glThread) {
                    ids.associateWith { id -> if (name == "None") null else Exporter.render(s.photos.getValue(id), look) }
                }
            }.onSuccess { res ->
                _state.update { st ->
                    val f = st.filtered.toMutableMap(); val n = st.filters.toMutableMap()
                    res.forEach { (id, b) -> if (b == null) { f.remove(id); n.remove(id) } else { f[id] = b; n[id] = name } }
                    st.copy(filtered = f, filters = n, busy = false)
                }
            }.onFailure { e -> _state.update { it.copy(busy = false, message = "Filter failed: ${e.message}") } }
        }
    }

    override fun onCleared() { glThread.close() }

    fun addPhotos(uris: List<Uri>) {
        if (uris.isEmpty()) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                uris.take(10).mapNotNull { u ->
                    runCatching { drafts.keepAccess(u); Triple(nextId++, store.decode(u, PHOTO_EDGE), u) }.getOrNull()
                }
            }
            _state.update { s ->
                val photos = s.photos + loaded.map { it.first to it.second }
                val order = s.order + loaded.map { it.first }
                val blurred = s.blurred ?: loaded.firstOrNull()?.second?.let { Blur.downscale(it, 48) }
                s.copy(photos = photos, uris = s.uris + loaded.map { it.first to it.third }, order = order, blurred = blurred, busy = false,
                    message = if (loaded.size < uris.size) "Some photos couldn't be opened" else null)
            }
            // First photos in: arrange them so there's something good to start from.
            if (_state.value.carousel.layers.isEmpty() || loaded.isNotEmpty()) applyLayout(lastLayout)
        }
    }

    /** Chosen on the Feed Studio start screen: the layout used once photos are picked. */
    fun startWith(layout: AutoLayout) { lastLayout = layout }

    fun applyLayout(layout: AutoLayout) {
        lastLayout = layout
        _state.update { s ->
            val ids = s.order
            val aspects = ids.map { id -> s.photos[id]!!.let { it.width.toFloat() / it.height } }
            val c = s.carousel.copy(layers = emptyList())
            val out = when (layout) {
                AutoLayout.Seams -> CarouselLayouts.seams(c, aspects, ids)
                AutoLayout.Panorama -> CarouselLayouts.panorama(c, aspects, ids)
                AutoLayout.OnePerSlide -> CarouselLayouts.onePerSlide(c, aspects, ids)
                AutoLayout.Dump -> CarouselLayouts.dump(c, aspects, ids)
            }
            s.copy(carousel = out, selected = null)
        }
    }

    fun setFormat(f: SlideFormat) = _state.update { it.copy(carousel = it.carousel.copy(format = f)) }.also { reflowIfAuto() }
    fun setSlides(n: Int) = _state.update { it.copy(carousel = it.carousel.copy(slides = n.coerceIn(1, 10))) }
    fun setBackground(b: Background) = _state.update { it.copy(carousel = it.carousel.copy(background = b)) }
    fun select(id: Long?) = _state.update { it.copy(selected = id) }

    private fun reflowIfAuto() { if (_state.value.order.isNotEmpty()) applyLayout(lastLayout) }

    /** Moves, scales and rotates the selected photo. Pan is in canvas units. */
    fun transformSelected(panX: Float, panY: Float, zoom: Float, rotate: Float) = updateSelected { l ->
        l.copy(cx = l.cx + panX, cy = l.cy + panY, height = (l.height * zoom).coerceIn(0.05f, 6f), rotation = l.rotation + rotate)
    }

    fun toggleRounded() = updateSelected { it.copy(corner = if (it.corner > 0f) 0f else 0.05f) }
    fun straighten() = updateSelected { it.copy(rotation = 0f) }

    fun bringToFront() = _state.update { s ->
        val id = s.selected ?: return@update s
        val l = s.carousel.layers.firstOrNull { it.id == id } ?: return@update s
        s.copy(carousel = s.carousel.copy(layers = s.carousel.layers - l + l))
    }

    fun deleteSelected() = _state.update { s ->
        val id = s.selected ?: return@update s
        s.copy(carousel = s.carousel.copy(layers = s.carousel.layers.filterNot { it.id == id }), selected = null)
    }

    private fun updateSelected(f: (Layer) -> Layer) = _state.update { s ->
        val id = s.selected ?: return@update s
        s.copy(carousel = s.carousel.copy(layers = s.carousel.layers.map { if (it.id == id) f(it) else it }))
    }

    fun export() {
        val s = _state.value
        if (s.carousel.layers.isEmpty() || s.busy) return
        _state.update { it.copy(busy = true, selected = null) }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.Default) {
                    val slides = CarouselPainter.exportSlides(s.carousel, s.photos + s.filtered, s.blurred)
                    val stamp = System.currentTimeMillis()
                    withContext(Dispatchers.IO) {
                        slides.forEachIndexed { i, b ->
                            store.saveJpeg(b, quality = 95, name = "Vannam_carousel_${stamp}_${i + 1}")
                            Thread.sleep(30) // distinct save times keep gallery order = slide order
                        }
                    }
                    slides.size
                }
            }.onSuccess { n -> _state.update { it.copy(busy = false, message = "Saved $n slides to Pictures/Vannam. Post them in order 1 to $n.") } }
             .onFailure { e -> _state.update { it.copy(busy = false, message = "Couldn't save: ${e.message}") } }
        }
    }

    val isDirty: Boolean get() = _state.value.photos.isNotEmpty()

    fun saveDraft() {
        val s = _state.value
        drafts.saveStudio(StudioDraft(s.carousel, s.uris, s.order, s.filters, System.currentTimeMillis()))
        _state.value = StudioState()
    }

    fun discard() { drafts.clearStudio(); _state.value = StudioState() }

    /** Re-opens a saved carousel: decodes its photos again and re-applies their filters. */
    fun restoreDraft(d: StudioDraft) {
        _state.value = StudioState(busy = true)
        viewModelScope.launch {
            val photos = withContext(Dispatchers.IO) {
                d.uris.mapNotNull { (id, u) -> runCatching { id to store.decode(u, PHOTO_EDGE) }.getOrNull() }.toMap()
            }
            nextId = (d.uris.keys.maxOrNull() ?: 0L) + 1
            val carousel = d.carousel.copy(layers = d.carousel.layers.filter { it.id in photos })
            val filtered = withContext(glThread) {
                d.filters.filterKeys { it in photos }.mapNotNull { (id, name) ->
                    (InstaLooks.all + FilterLibrary.all).firstOrNull { it.first == name }?.let { id to Exporter.render(photos.getValue(id), it.second) }
                }.toMap()
            }
            _state.value = StudioState(
                carousel = carousel, photos = photos, uris = d.uris.filterKeys { it in photos },
                order = d.order.filter { it in photos }, filters = d.filters.filterKeys { it in photos }, filtered = filtered,
                blurred = d.order.firstNotNullOfOrNull { photos[it] }?.let { Blur.downscale(it, 48) },
                message = if (photos.size < d.uris.size) "Some photos are no longer available" else null,
            )
        }
    }

    fun messageShown() = _state.update { it.copy(message = null) }

    private companion object { const val PHOTO_EDGE = 1800 }
}
