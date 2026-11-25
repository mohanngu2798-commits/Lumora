package app.lumora.editor.model

/** A cell of a grid collage, in 0..1 canvas units. */
data class Cell(val x: Float, val y: Float, val w: Float, val h: Float) {
    fun contains(px: Float, py: Float) = px >= x && px <= x + w && py >= y && py <= y + h
}

enum class CollageMode { Grid, Free }

/** Which photo fills a grid cell, and how it is framed inside it. Pan is -1..1 of the spare room. */
data class CellPhoto(val photo: Int, val panX: Float = 0f, val panY: Float = 0f, val zoom: Float = 1f)

/** A freely placed photo. Centre in 0..1 canvas units; [width] is a fraction of canvas width; rotation in degrees. */
data class FreeItem(val photo: Int, val cx: Float, val cy: Float, val width: Float, val rotation: Float = 0f)

data class CollageSpec(
    val mode: CollageMode = CollageMode.Grid,
    val aspect: Float = 4f / 5f,      // width / height
    val template: Int = 0,
    val spacing: Float = 0.02f,       // gap as a fraction of the canvas' short side
    val radius: Float = 0f,           // corner radius as a fraction of the canvas' short side
    val background: Int = 0xFFFFFFFF.toInt(),
    val cells: List<CellPhoto> = emptyList(),
    val items: List<FreeItem> = emptyList(),
    val frames: Boolean = true,       // white print borders in freestyle
    val shadows: Boolean = true,
) {
    fun layout(count: Int): List<Cell> = GridTemplates.forCount(count).let { it[template.coerceIn(0, it.lastIndex)] }
}

object GridTemplates {
    const val MAX_PHOTOS = 9

    /** Rows of equal height with [counts] equal cells in each. */
    private fun rows(vararg counts: Int): List<Cell> = counts.flatMapIndexed { r, n ->
        val h = 1f / counts.size
        (0 until n).map { Cell(it.toFloat() / n, r * h, 1f / n, h) }
    }

    private fun cols(vararg counts: Int): List<Cell> = rows(*counts).map { Cell(it.y, it.x, it.h, it.w) }

    /** One large cell on the left, the rest stacked on the right. */
    private fun featureLeft(n: Int, split: Float = 0.6f): List<Cell> =
        listOf(Cell(0f, 0f, split, 1f)) + (0 until n - 1).map { Cell(split, it.toFloat() / (n - 1), 1f - split, 1f / (n - 1)) }

    /** One large cell on top, the rest in a row below. */
    private fun featureTop(n: Int, split: Float = 0.62f): List<Cell> =
        listOf(Cell(0f, 0f, 1f, split)) + (0 until n - 1).map { Cell(it.toFloat() / (n - 1), split, 1f / (n - 1), 1f - split) }

    /** Large cell in the middle row, framed by rows above and below. */
    private fun featureMiddle(top: Int, bottom: Int): List<Cell> {
        val b = 0.25f
        return (0 until top).map { Cell(it.toFloat() / top, 0f, 1f / top, b) } +
            Cell(0f, b, 1f, 1f - 2 * b) +
            (0 until bottom).map { Cell(it.toFloat() / bottom, 1f - b, 1f / bottom, b) }
    }

    private val cache = HashMap<Int, List<List<Cell>>>()

    fun forCount(n: Int): List<List<Cell>> = cache.getOrPut(n.coerceIn(1, MAX_PHOTOS)) { build(n.coerceIn(1, MAX_PHOTOS)) }

    private fun build(n: Int): List<List<Cell>> = when (n) {
        1 -> listOf(rows(1))
        2 -> listOf(rows(2), rows(1, 1), featureLeft(2, 0.62f), featureTop(2, 0.65f))
        3 -> listOf(featureLeft(3), rows(3), rows(1, 1, 1), featureTop(3), rows(1, 2), rows(2, 1), cols(1, 2))
        4 -> listOf(rows(2, 2), featureLeft(4), featureTop(4), rows(4), rows(1, 1, 1, 1), rows(1, 3), rows(3, 1), featureMiddle(2, 1))
        5 -> listOf(rows(2, 3), rows(3, 2), featureLeft(5), featureTop(5), featureMiddle(2, 2), rows(1, 2, 2), cols(2, 3))
        6 -> listOf(rows(3, 3), rows(2, 2, 2), featureTop(6), featureMiddle(3, 2), rows(1, 2, 3), cols(3, 3), featureLeft(6))
        7 -> listOf(rows(3, 1, 3), rows(2, 3, 2), featureMiddle(3, 3), rows(4, 3), cols(3, 4), rows(1, 3, 3))
        8 -> listOf(rows(3, 2, 3), rows(4, 4), rows(2, 2, 2, 2), featureMiddle(4, 3), cols(4, 4), rows(3, 3, 2))
        else -> listOf(rows(3, 3, 3), rows(4, 1, 4), rows(2, 3, 4), cols(3, 3, 3), featureMiddle(4, 4))
    }

    /** Scatters [n] photos across the canvas like prints tossed on a table. Deterministic per [seed]. */
    fun scatter(n: Int, seed: Int = 7): List<FreeItem> {
        val rnd = java.util.Random(seed.toLong())
        val cols = when { n <= 2 -> n; n <= 4 -> 2; n <= 9 -> 3; else -> 4 }
        val rowsN = (n + cols - 1) / cols
        val w = (0.95f / cols).coerceAtMost(0.62f)
        return (0 until n).map { i ->
            val c = i % cols; val r = i / cols
            FreeItem(
                photo = i,
                cx = (c + 0.5f) / cols + (rnd.nextFloat() - 0.5f) * 0.08f,
                cy = (r + 0.5f) / rowsN + (rnd.nextFloat() - 0.5f) * 0.06f,
                width = w * (0.95f + rnd.nextFloat() * 0.15f),
                rotation = (rnd.nextFloat() - 0.5f) * 16f,
            )
        }
    }
}
