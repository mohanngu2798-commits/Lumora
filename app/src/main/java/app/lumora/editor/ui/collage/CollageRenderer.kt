package app.lumora.editor.ui.collage

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import app.lumora.editor.model.CellPhoto
import app.lumora.editor.model.CollageMode
import app.lumora.editor.model.CollageSpec
import app.lumora.editor.model.FreeItem
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Draws a collage onto an Android canvas. The same code paints the live preview and the saved file. */
object CollageRenderer {
    private val photoPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val selPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0xFFE8336F.toInt() }

    fun draw(c: Canvas, w: Float, h: Float, s: CollageSpec, photos: List<Bitmap>, selected: Int? = null) {
        c.drawColor(s.background)
        if (photos.isEmpty()) return
        val short = min(w, h)
        selPaint.strokeWidth = short * 0.008f
        when (s.mode) {
            CollageMode.Grid -> {
                val gap = s.spacing * short
                val r = s.radius * short
                s.layout(s.cells.size).forEachIndexed { i, cell ->
                    val cp = s.cells.getOrNull(i) ?: return@forEachIndexed
                    val rect = cellRect(cell.x, cell.y, cell.w, cell.h, w, h, gap)
                    val bmp = photos.getOrNull(cp.photo) ?: return@forEachIndexed
                    val path = Path().apply { addRoundRect(rect, r, r, Path.Direction.CW) }
                    c.save(); c.clipPath(path)
                    c.drawBitmap(bmp, cellMatrix(bmp, rect, cp), photoPaint)
                    c.restore()
                    if (selected == i) c.drawRoundRect(rect, r, r, selPaint)
                }
            }
            CollageMode.Free -> s.items.forEachIndexed { i, it ->
                val bmp = photos.getOrNull(it.photo) ?: return@forEachIndexed
                drawItem(c, w, h, it, bmp, s, selected == i)
            }
        }
    }

    fun cellRect(x: Float, y: Float, cw: Float, ch: Float, w: Float, h: Float, gap: Float): RectF {
        val half = gap / 2
        // Outer gap equals inner gap: pad the canvas by half a gap, then inset each cell by half a gap.
        val iw = w - gap; val ih = h - gap
        return RectF(half + x * iw + half, half + y * ih + half, half + (x + cw) * iw - half, half + (y + ch) * ih - half)
    }

    /** Centre-crop the photo into [rect], then apply the cell's zoom and pan. */
    fun cellMatrix(bmp: Bitmap, rect: RectF, cp: CellPhoto): Matrix {
        val scale = max(rect.width() / bmp.width, rect.height() / bmp.height) * cp.zoom
        val sw = bmp.width * scale; val sh = bmp.height * scale
        val dx = rect.left + (rect.width() - sw) / 2 + cp.panX * (sw - rect.width()) / 2
        val dy = rect.top + (rect.height() - sh) / 2 + cp.panY * (sh - rect.height()) / 2
        return Matrix().apply { setScale(scale, scale); postTranslate(dx, dy) }
    }

    private fun drawItem(c: Canvas, w: Float, h: Float, it: FreeItem, bmp: Bitmap, s: CollageSpec, selected: Boolean) {
        val pw = it.width * w
        val ph = pw * bmp.height / bmp.width
        val border = if (s.frames) pw * 0.04f else 0f
        c.save()
        c.translate(it.cx * w, it.cy * h)
        c.rotate(it.rotation)
        val outer = RectF(-pw / 2 - border, -ph / 2 - border, pw / 2 + border, ph / 2 + border * if (s.frames) 3.2f else 1f)
        val r = s.radius * min(w, h)
        if (s.shadows) {
            framePaint.setShadowLayer(pw * 0.04f, 0f, pw * 0.015f, 0x55000000)
            framePaint.color = if (s.frames) Color.WHITE else s.background
            c.drawRoundRect(outer, r, r, framePaint)
            framePaint.clearShadowLayer()
        } else if (s.frames) {
            framePaint.color = Color.WHITE
            c.drawRoundRect(outer, r, r, framePaint)
        }
        val inner = RectF(-pw / 2, -ph / 2, pw / 2, ph / 2)
        c.save()
        c.clipPath(Path().apply { val ir = if (s.frames) r * 0.5f else r; addRoundRect(inner, ir, ir, Path.Direction.CW) })
        c.drawBitmap(bmp, null, inner, photoPaint)
        c.restore()
        if (selected) c.drawRoundRect(outer, r, r, selPaint)
        c.restore()
    }

    /** Topmost freestyle item under (px, py) in canvas pixels, or null. */
    fun hitItem(s: CollageSpec, photos: List<Bitmap>, w: Float, h: Float, px: Float, py: Float): Int? {
        for (i in s.items.indices.reversed()) {
            val it = s.items[i]; val bmp = photos.getOrNull(it.photo) ?: continue
            val pw = it.width * w; val ph = pw * bmp.height / bmp.width
            val a = Math.toRadians(-it.rotation.toDouble())
            val dx = px - it.cx * w; val dy = py - it.cy * h
            val lx = dx * cos(a) - dy * sin(a); val ly = dx * sin(a) + dy * cos(a)
            val pad = pw * 0.06f
            if (lx in (-pw / 2 - pad).toDouble()..(pw / 2 + pad).toDouble() && ly in (-ph / 2 - pad).toDouble()..(ph / 2 + pad * 2).toDouble()) return i
        }
        return null
    }

    fun hitCell(s: CollageSpec, w: Float, h: Float, px: Float, py: Float): Int? =
        s.layout(s.cells.size).indexOfFirst { it.contains(px / w, py / h) }.takeIf { it >= 0 }

    fun export(s: CollageSpec, photos: List<Bitmap>, longEdge: Int = 2400): Bitmap {
        val (w, h) = if (s.aspect >= 1f) longEdge to (longEdge / s.aspect).toInt() else (longEdge * s.aspect).toInt() to longEdge
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { draw(Canvas(it), w.toFloat(), h.toFloat(), s, photos) }
    }
}
