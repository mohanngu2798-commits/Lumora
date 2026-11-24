package app.lumora.editor.model

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/**
 * One-tap enhancement. Measures the photo's brightness spread and colour cast, then picks
 * values for the regular sliders, so the result stays fully editable afterwards.
 */
object AutoEnhance {

    /** [pixels] are ARGB ints (as from Bitmap.getPixels), ideally a small downscaled copy. */
    fun analyze(pixels: IntArray, base: EditParams = EditParams()): EditParams {
        if (pixels.isEmpty()) return base
        val hist = IntArray(256)
        var rSum = 0.0; var gSum = 0.0; var bSum = 0.0
        for (p in pixels) {
            val r = (p shr 16) and 0xFF; val g = (p shr 8) and 0xFF; val b = p and 0xFF
            hist[((r * 54 + g * 183 + b * 19) shr 8).coerceIn(0, 255)]++
            rSum += r; gSum += g; bSum += b
        }
        val n = pixels.size
        fun percentile(q: Double): Float {
            val target = (q * n).toLong()
            var acc = 0L
            for (i in 0 until 256) { acc += hist[i]; if (acc > target) return i / 255f }
            return 1f
        }
        val p02 = percentile(0.02); val p10 = percentile(0.10)
        val p50 = percentile(0.50); val p98 = percentile(0.98)

        // Bring the median towards mid grey, gently.
        val median = max(p50, 0.02f)
        val exposure = (ln(0.46f / median) / ln(2f) * 0.6f).coerceIn(-0.8f, 0.8f)
        // Flat photos get more contrast; already punchy ones get none.
        val spread = p98 - p02
        val contrast = ((0.8f - spread) * 0.5f).coerceIn(0f, 0.3f)
        val shadows = if (p10 < 0.12f) ((0.12f - p10) * 2.5f).coerceIn(0f, 0.4f) else 0f
        val highlights = if (p98 > 0.95f) -((p98 - 0.95f) * 6f).coerceIn(0f, 0.4f) else 0f

        // Grey-world white balance: a blue-heavy photo gets warmer and vice versa.
        val r = rSum / n; val g = gSum / n; val b = bSum / n
        val warmth = (((b - r) / max(r + b, 1.0)) * 1.6).toFloat().coerceIn(-0.3f, 0.3f)
        val tint = (((g - (r + b) / 2) / max(g, 1.0)) * 1.2).toFloat().coerceIn(-0.2f, 0.2f)

        // Duller photos get more vibrance.
        val chroma = (abs(r - g) + abs(g - b) + abs(b - r)) / 3.0 / 255.0
        val vibrance = (0.35f - chroma.toFloat() * 2f).coerceIn(0.1f, 0.35f)

        return base.copy(
            exposure = exposure, contrast = contrast, shadows = shadows, highlights = highlights,
            warmth = warmth, tint = tint, vibrance = vibrance,
            clarity = max(base.clarity, 0.15f), sharpen = max(base.sharpen, 0.15f),
            dehaze = if (p02 > 0.12f) min(0.3f, (p02 - 0.12f) * 2f) else base.dehaze,
        )
    }
}
