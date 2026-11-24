package app.lumora.editor.ui.studio

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import app.lumora.editor.model.Background
import app.lumora.editor.model.Carousel
import kotlin.math.max
import kotlin.math.min

/**
 * Draws a carousel with plain Android graphics. The editor preview and the export both use
 * this, so what you see is exactly what gets saved. [px] is pixels per canvas unit (canvas height).
 */
object CarouselPainter {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    fun draw(canvas: Canvas, c: Carousel, photos: Map<Long, Bitmap>, blurred: Bitmap?, px: Float) {
        val w = c.width * px; val h = px
        drawBackground(canvas, c, blurred, w, h)
        for (l in c.layers) {
            val bmp = photos[l.id] ?: continue
            val lw = l.width * px; val lh = l.height * px
            canvas.save()
            canvas.translate(l.cx * px, l.cy * px)
            canvas.rotate(l.rotation)
            val rect = RectF(-lw / 2, -lh / 2, lw / 2, lh / 2)
            if (l.corner > 0f) {
                val shader = BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
                shader.setLocalMatrix(Matrix().apply {
                    setScale(lw / bmp.width, lh / bmp.height); postTranslate(-lw / 2, -lh / 2)
                })
                paint.shader = shader
                val r = l.corner * min(lw, lh)
                canvas.drawRoundRect(rect, r, r, paint)
                paint.shader = null
            } else {
                canvas.drawBitmap(bmp, null, rect, paint)
            }
            canvas.restore()
        }
    }

    private fun drawBackground(canvas: Canvas, c: Carousel, blurred: Bitmap?, w: Float, h: Float) {
        when (val bg = c.background) {
            is Background.Solid -> canvas.drawColor(bg.argb)
            is Background.Gradient -> {
                paint.shader = LinearGradient(0f, 0f, w, h, bg.from, bg.to, Shader.TileMode.CLAMP)
                canvas.drawRect(0f, 0f, w, h, paint)
                paint.shader = null
            }
            Background.BlurredPhoto -> {
                if (blurred == null) { canvas.drawColor(0xFF222222.toInt()); return }
                // Cover the strip, keeping the blurred photo's shape.
                val s = max(w / blurred.width, h / blurred.height)
                val bw = blurred.width * s; val bh = blurred.height * s
                canvas.drawBitmap(blurred, null, RectF((w - bw) / 2, (h - bh) / 2, (w + bw) / 2, (h + bh) / 2), paint)
                canvas.drawColor(0x33000000)
            }
        }
    }

    /** Renders the full strip at Instagram resolution and cuts it into one bitmap per slide. */
    fun exportSlides(c: Carousel, photos: Map<Long, Bitmap>, blurred: Bitmap?): List<Bitmap> {
        val (w, h) = c.pixelSize()
        val strip = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        draw(Canvas(strip), c, photos, blurred, h.toFloat())
        val sw = c.format.pxW
        return (0 until c.slides).map { i -> Bitmap.createBitmap(strip, i * sw, 0, sw, h) }
            .also { /* slices are copies only when needed; keep strip alive until saved */ }
    }
}
