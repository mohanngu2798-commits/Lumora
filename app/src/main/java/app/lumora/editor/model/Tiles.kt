package app.lumora.editor.model

/**
 * Splits an image into overlapping square tiles for a fixed-size model. Each tile reads
 * [size] pixels starting at [readX], [readY] (edges are clamped), and only its centre
 * [keepW] x [keepH] pixels, starting at [x], [y], are kept, which hides seams.
 */
data class Tile(val readX: Int, val readY: Int, val x: Int, val y: Int, val keepW: Int, val keepH: Int)

object Tiles {
    fun plan(width: Int, height: Int, size: Int, pad: Int): List<Tile> {
        require(size > pad * 2)
        val step = size - pad * 2
        val out = ArrayList<Tile>()
        var y = 0
        while (y < height) {
            var x = 0
            while (x < width) {
                out += Tile(x - pad, y - pad, x, y, minOf(step, width - x), minOf(step, height - y))
                x += step
            }
            y += step
        }
        return out
    }
}
