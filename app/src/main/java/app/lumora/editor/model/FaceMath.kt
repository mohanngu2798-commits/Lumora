package app.lumora.editor.model

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/** A detected face in normalised (0..1) coordinates of the square model input. */
data class Face(
    val left: Float, val top: Float, val right: Float, val bottom: Float,
    /** Image-left eye, image-right eye, nose tip, mouth centre: (x, y) pairs. */
    val keypoints: List<Pair<Float, Float>>,
    val score: Float,
)

object FaceMath {
    /**
     * Decodes the full-range BlazeFace output (one anchor per cell on a [grid]×[grid] map over a
     * [size]-pixel input) and removes overlapping boxes.
     */
    fun decode(reg: FloatArray, cls: FloatArray, size: Int = 192, grid: Int = 48, minScore: Float = 0.6f): List<Face> {
        val n = grid * grid
        val cand = ArrayList<Face>()
        for (i in 0 until n) {
            val s = 1f / (1f + exp(-cls[i].coerceIn(-100f, 100f)))
            if (s < minScore) continue
            val ax = (i % grid + 0.5f) / grid; val ay = (i / grid + 0.5f) / grid
            val o = i * 16
            val cx = reg[o] / size + ax; val cy = reg[o + 1] / size + ay
            val w = reg[o + 2] / size; val h = reg[o + 3] / size
            val kp = (0 until 4).map { k -> (reg[o + 4 + 2 * k] / size + ax) to (reg[o + 5 + 2 * k] / size + ay) }
            cand += Face(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2, kp, s)
        }
        val kept = ArrayList<Face>()
        for (f in cand.sortedByDescending { it.score }) if (kept.none { iou(it, f) > 0.3f }) kept += f
        return kept
    }

    fun iou(a: Face, b: Face): Float {
        val ix = max(0f, min(a.right, b.right) - max(a.left, b.left))
        val iy = max(0f, min(a.bottom, b.bottom) - max(a.top, b.top))
        val inter = ix * iy
        val union = (a.right - a.left) * (a.bottom - a.top) + (b.right - b.left) * (b.bottom - b.top) - inter
        return if (union <= 0f) 0f else inter / union
    }

    /** FFHQ 512 alignment template: image-left eye, image-right eye, nose, mouth centre. */
    val FFHQ_512 = listOf(192.98138f to 239.94708f, 318.90277f to 240.1936f, 256.63416f to 314.01935f, 257.17511f to 371.28080f)

    /**
     * Least-squares similarity transform (scale, rotation, translation) mapping [src] onto [dst].
     * Returns [a, b, tx, ty] for x' = a·x − b·y + tx, y' = b·x + a·y + ty.
     */
    fun similarity(src: List<Pair<Float, Float>>, dst: List<Pair<Float, Float>>): FloatArray {
        val n = src.size
        val sx = src.sumOf { it.first.toDouble() } / n; val sy = src.sumOf { it.second.toDouble() } / n
        val dx = dst.sumOf { it.first.toDouble() } / n; val dy = dst.sumOf { it.second.toDouble() } / n
        var num1 = 0.0; var num2 = 0.0; var den = 0.0
        for (i in 0 until n) {
            val px = src[i].first - sx; val py = src[i].second - sy
            val qx = dst[i].first - dx; val qy = dst[i].second - dy
            num1 += px * qx + py * qy; num2 += px * qy - py * qx; den += px * px + py * py
        }
        val a = num1 / den; val b = num2 / den
        val tx = dx - (a * sx - b * sy); val ty = dy - (b * sx + a * sy)
        return floatArrayOf(a.toFloat(), b.toFloat(), tx.toFloat(), ty.toFloat())
    }
}
