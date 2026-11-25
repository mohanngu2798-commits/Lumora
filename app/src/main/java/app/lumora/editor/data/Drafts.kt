package app.lumora.editor.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import app.lumora.editor.model.*
import org.json.JSONArray
import org.json.JSONObject

/** A paused photo edit. */
data class EditorDraft(val uri: Uri, val params: EditParams, val savedAt: Long)

/** A paused Feed Studio carousel. Photos are referenced by Uri and re-opened on restore. */
data class StudioDraft(
    val carousel: Carousel,
    val uris: Map<Long, Uri>,
    val order: List<Long>,
    val filters: Map<Long, String>,
    val savedAt: Long,
)

/** A paused collage. */
data class CollageDraft(val spec: CollageSpec, val uris: List<Uri>, val savedAt: Long)

/** One draft per section, stored on the phone as JSON. */
class Drafts(private val context: Context) {
    private val sp = context.getSharedPreferences("drafts", Context.MODE_PRIVATE)

    /** Keeps read access to a picked photo after the app restarts, where Android allows it. */
    fun keepAccess(uri: Uri) {
        runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }

    fun saveEditor(d: EditorDraft) {
        keepAccess(d.uri)
        sp.edit().putString("editor", JSONObject().put("uri", d.uri.toString()).put("params", params(d.params))
            .put("at", d.savedAt).toString()).apply()
    }

    fun editor(): EditorDraft? = runCatching {
        val o = JSONObject(sp.getString("editor", null) ?: return null)
        EditorDraft(Uri.parse(o.getString("uri")), params(o.getJSONObject("params")), o.getLong("at"))
    }.getOrNull()

    fun clearEditor() = sp.edit().remove("editor").apply()

    fun saveStudio(d: StudioDraft) {
        d.uris.values.forEach(::keepAccess)
        val c = d.carousel
        val o = JSONObject()
            .put("format", c.format.name).put("slides", c.slides).put("gap", c.gap.toDouble())
            .put("background", background(c.background))
            .put("layers", JSONArray(c.layers.map { l ->
                JSONObject().put("id", l.id).put("aspect", l.photoAspect.toDouble()).put("cx", l.cx.toDouble())
                    .put("cy", l.cy.toDouble()).put("h", l.height.toDouble()).put("rot", l.rotation.toDouble())
                    .put("corner", l.corner.toDouble())
            }))
            .put("uris", JSONObject(d.uris.mapKeys { it.key.toString() }.mapValues { it.value.toString() }))
            .put("order", JSONArray(d.order))
            .put("filters", JSONObject(d.filters.mapKeys { it.key.toString() }))
            .put("at", d.savedAt)
        sp.edit().putString("studio", o.toString()).apply()
    }

    fun studio(): StudioDraft? = runCatching {
        val o = JSONObject(sp.getString("studio", null) ?: return null)
        val layers = o.getJSONArray("layers").let { a ->
            (0 until a.length()).map { i ->
                val l = a.getJSONObject(i)
                Layer(l.getLong("id"), l.getDouble("aspect").toFloat(), l.getDouble("cx").toFloat(), l.getDouble("cy").toFloat(),
                    l.getDouble("h").toFloat(), l.getDouble("rot").toFloat(), l.getDouble("corner").toFloat())
            }
        }
        val carousel = Carousel(SlideFormat.valueOf(o.getString("format")), o.getInt("slides"), layers,
            background(o.getJSONObject("background")), o.getDouble("gap").toFloat())
        val u = o.getJSONObject("uris")
        val uris = u.keys().asSequence().associate { it.toLong() to Uri.parse(u.getString(it)) }
        val ord = o.getJSONArray("order").let { a -> (0 until a.length()).map { a.getLong(it) } }
        val f = o.getJSONObject("filters")
        val filters = f.keys().asSequence().associate { it.toLong() to f.getString(it) }
        StudioDraft(carousel, uris, ord, filters, o.getLong("at"))
    }.getOrNull()

    fun clearStudio() = sp.edit().remove("studio").apply()

    fun saveCollage(d: CollageDraft) {
        d.uris.forEach(::keepAccess)
        val s = d.spec
        val o = JSONObject().put("mode", s.mode.name).put("aspect", s.aspect.toDouble()).put("template", s.template)
            .put("spacing", s.spacing.toDouble()).put("radius", s.radius.toDouble()).put("bg", s.background)
            .put("frames", s.frames).put("shadows", s.shadows)
            .put("cells", JSONArray(s.cells.map { JSONObject().put("p", it.photo).put("x", it.panX.toDouble()).put("y", it.panY.toDouble()).put("z", it.zoom.toDouble()) }))
            .put("items", JSONArray(s.items.map { JSONObject().put("p", it.photo).put("x", it.cx.toDouble()).put("y", it.cy.toDouble()).put("w", it.width.toDouble()).put("r", it.rotation.toDouble()) }))
            .put("uris", JSONArray(d.uris.map { it.toString() })).put("at", d.savedAt)
        sp.edit().putString("collage", o.toString()).apply()
    }

    fun collage(): CollageDraft? = runCatching {
        val o = JSONObject(sp.getString("collage", null) ?: return null)
        fun JSONObject.f(k: String) = getDouble(k).toFloat()
        val cells = o.getJSONArray("cells").let { a -> (0 until a.length()).map { a.getJSONObject(it) }.map { CellPhoto(it.getInt("p"), it.f("x"), it.f("y"), it.f("z")) } }
        val items = o.getJSONArray("items").let { a -> (0 until a.length()).map { a.getJSONObject(it) }.map { FreeItem(it.getInt("p"), it.f("x"), it.f("y"), it.f("w"), it.f("r")) } }
        val spec = CollageSpec(CollageMode.valueOf(o.getString("mode")), o.f("aspect"), o.getInt("template"), o.f("spacing"), o.f("radius"),
            o.getInt("bg"), cells, items, o.getBoolean("frames"), o.getBoolean("shadows"))
        val uris = o.getJSONArray("uris").let { a -> (0 until a.length()).map { Uri.parse(a.getString(it)) } }
        CollageDraft(spec, uris, o.getLong("at"))
    }.getOrNull()

    fun clearCollage() = sp.edit().remove("collage").apply()

    private fun background(b: Background): JSONObject = when (b) {
        is Background.Solid -> JSONObject().put("t", "solid").put("c", b.argb)
        is Background.Gradient -> JSONObject().put("t", "grad").put("a", b.from).put("b", b.to)
        Background.BlurredPhoto -> JSONObject().put("t", "blur")
    }

    private fun background(o: JSONObject): Background = when (o.getString("t")) {
        "solid" -> Background.Solid(o.getInt("c"))
        "grad" -> Background.Gradient(o.getInt("a"), o.getInt("b"))
        else -> Background.BlurredPhoto
    }

    private fun curve(c: CurvePoints) = JSONArray(c.points.flatMap { listOf(it.first.toDouble(), it.second.toDouble()) })
    private fun curve(a: JSONArray) = CurvePoints((0 until a.length() / 2).map { a.getDouble(it * 2).toFloat() to a.getDouble(it * 2 + 1).toFloat() })

    private fun params(p: EditParams): JSONObject = JSONObject().apply {
        put("exposure", p.exposure.toDouble()); put("contrast", p.contrast.toDouble())
        put("highlights", p.highlights.toDouble()); put("shadows", p.shadows.toDouble())
        put("saturation", p.saturation.toDouble()); put("vibrance", p.vibrance.toDouble())
        put("warmth", p.warmth.toDouble()); put("tint", p.tint.toDouble())
        put("fade", p.fade.toDouble()); put("sharpen", p.sharpen.toDouble())
        put("vignette", p.vignette.toDouble()); put("grain", p.grain.toDouble())
        put("clarity", p.clarity.toDouble()); put("dehaze", p.dehaze.toDouble())
        put("hdr", p.hdr.toDouble()); put("glow", p.glow.toDouble())
        put("lensBlur", p.lensBlur.toDouble()); put("blurShape", p.blurShape.name)
        put("lightLeak", p.lightLeak.toDouble()); put("leakStyle", p.leakStyle)
        put("curves", JSONObject().put("m", curve(p.curves.master)).put("r", curve(p.curves.red))
            .put("g", curve(p.curves.green)).put("b", curve(p.curves.blue)))
        put("crop", JSONObject().put("aspect", p.crop.aspect?.toDouble() ?: JSONObject.NULL)
            .put("turns", p.crop.quarterTurns).put("flip", p.crop.flipHorizontal))
        put("look", p.lookName ?: JSONObject.NULL); put("lookStrength", p.lookStrength.toDouble())
        put("toneS", p.toneShadow); put("toneH", p.toneHighlight); put("tone", p.toneAmount.toDouble())
    }

    private fun params(o: JSONObject): EditParams {
        fun f(k: String) = o.optDouble(k, 0.0).toFloat()
        val c = o.getJSONObject("curves"); val cr = o.getJSONObject("crop")
        return EditParams(
            exposure = f("exposure"), contrast = f("contrast"), highlights = f("highlights"), shadows = f("shadows"),
            saturation = f("saturation"), vibrance = f("vibrance"), warmth = f("warmth"), tint = f("tint"),
            fade = f("fade"), sharpen = f("sharpen"), vignette = f("vignette"), grain = f("grain"),
            clarity = f("clarity"), dehaze = f("dehaze"), hdr = f("hdr"), glow = f("glow"), lensBlur = f("lensBlur"),
            blurShape = runCatching { BlurShape.valueOf(o.getString("blurShape")) }.getOrDefault(BlurShape.Radial),
            lightLeak = f("lightLeak"), leakStyle = o.optInt("leakStyle", 0),
            curves = Curves(curve(c.getJSONArray("m")), curve(c.getJSONArray("r")), curve(c.getJSONArray("g")), curve(c.getJSONArray("b"))),
            crop = CropSpec(if (cr.isNull("aspect")) null else cr.getDouble("aspect").toFloat(), cr.getInt("turns"), cr.getBoolean("flip")),
            lookName = if (o.isNull("look")) null else o.getString("look"),
            lookStrength = o.optDouble("lookStrength", 1.0).toFloat(),
            toneShadow = o.optInt("toneS", 0x808080), toneHighlight = o.optInt("toneH", 0x808080), toneAmount = f("tone"),
        )
    }
}
