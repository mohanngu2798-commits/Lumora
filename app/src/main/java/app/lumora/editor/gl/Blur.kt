package app.lumora.editor.gl

import android.graphics.Bitmap
import kotlin.math.max

object Blur {
    /**
     * Shrinks [src] so its long edge is about [edge] pixels by repeated halving with filtering.
     * Each halving averages neighbours, so the result is a smooth, wide blur once upsampled.
     */
    fun downscale(src: Bitmap, edge: Int): Bitmap {
        var cur = src
        while (max(cur.width, cur.height) > edge * 2) {
            val next = Bitmap.createScaledBitmap(cur, max(1, cur.width / 2), max(1, cur.height / 2), true)
            if (cur !== src) cur.recycle()
            cur = next
        }
        val s = edge.toFloat() / max(cur.width, cur.height)
        val out = if (s < 1f) Bitmap.createScaledBitmap(cur, max(1, (cur.width * s).toInt()), max(1, (cur.height * s).toInt()), true)
                  else cur.copy(Bitmap.Config.ARGB_8888, false)
        if (cur !== src && cur !== out) cur.recycle()
        return out
    }
}
