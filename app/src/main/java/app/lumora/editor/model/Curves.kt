package app.lumora.editor.model

import kotlin.math.abs

enum class CurveChannel { Master, Red, Green, Blue }

/** A tone curve: control points in 0..1, sorted by x, always including both endpoints. */
data class CurvePoints(val points: List<Pair<Float, Float>> = listOf(0f to 0f, 1f to 1f)) {
    val isIdentity: Boolean get() = points.all { abs(it.first - it.second) < 1e-4f }

    /** Samples the curve into 256 values using monotone cubic (Fritsch–Carlson) interpolation. */
    fun sample(): FloatArray {
        val p = points.sortedBy { it.first }
        val n = p.size
        val xs = FloatArray(n) { p[it].first }
        val ys = FloatArray(n) { p[it].second }
        val out = FloatArray(256)
        if (n < 2) { for (i in 0 until 256) out[i] = i / 255f; return out }

        val d = FloatArray(n - 1) { (ys[it + 1] - ys[it]) / (xs[it + 1] - xs[it]).coerceAtLeast(1e-6f) }
        val m = FloatArray(n)
        m[0] = d[0]; m[n - 1] = d[n - 2]
        for (i in 1 until n - 1) m[i] = if (d[i - 1] * d[i] <= 0f) 0f else (d[i - 1] + d[i]) / 2f
        for (i in 0 until n - 1) {
            if (d[i] == 0f) { m[i] = 0f; m[i + 1] = 0f; continue }
            val a = m[i] / d[i]; val b = m[i + 1] / d[i]
            val s = a * a + b * b
            if (s > 9f) { val t = 3f / kotlin.math.sqrt(s); m[i] = t * a * d[i]; m[i + 1] = t * b * d[i] }
        }

        var seg = 0
        for (i in 0 until 256) {
            val x = i / 255f
            when {
                x <= xs[0] -> out[i] = ys[0]
                x >= xs[n - 1] -> out[i] = ys[n - 1]
                else -> {
                    while (seg < n - 2 && x > xs[seg + 1]) seg++
                    val h = xs[seg + 1] - xs[seg]
                    val t = (x - xs[seg]) / h
                    val t2 = t * t; val t3 = t2 * t
                    out[i] = (2 * t3 - 3 * t2 + 1) * ys[seg] + (t3 - 2 * t2 + t) * h * m[seg] +
                        (-2 * t3 + 3 * t2) * ys[seg + 1] + (t3 - t2) * h * m[seg + 1]
                }
            }
            out[i] = out[i].coerceIn(0f, 1f)
        }
        return out
    }
}

data class Curves(
    val master: CurvePoints = CurvePoints(),
    val red: CurvePoints = CurvePoints(),
    val green: CurvePoints = CurvePoints(),
    val blue: CurvePoints = CurvePoints(),
) {
    operator fun get(c: CurveChannel) = when (c) {
        CurveChannel.Master -> master; CurveChannel.Red -> red
        CurveChannel.Green -> green; CurveChannel.Blue -> blue
    }

    fun with(c: CurveChannel, p: CurvePoints) = when (c) {
        CurveChannel.Master -> copy(master = p); CurveChannel.Red -> copy(red = p)
        CurveChannel.Green -> copy(green = p); CurveChannel.Blue -> copy(blue = p)
    }

    /** 256x1 RGBA lookup table: each channel = channelCurve(masterCurve(x)). */
    fun toLut(): ByteArray {
        val m = master.sample(); val r = red.sample(); val g = green.sample(); val b = blue.sample()
        val lut = ByteArray(256 * 4)
        for (i in 0 until 256) {
            val mi = (m[i] * 255f + 0.5f).toInt().coerceIn(0, 255)
            lut[i * 4] = (r[mi] * 255f + 0.5f).toInt().toByte()
            lut[i * 4 + 1] = (g[mi] * 255f + 0.5f).toInt().toByte()
            lut[i * 4 + 2] = (b[mi] * 255f + 0.5f).toInt().toByte()
            lut[i * 4 + 3] = 0xFF.toByte()
        }
        return lut
    }
}
