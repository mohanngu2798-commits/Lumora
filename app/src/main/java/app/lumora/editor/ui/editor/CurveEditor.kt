package app.lumora.editor.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import app.lumora.editor.model.CurvePoints

/** Interactive tone curve. Points live in 0..1; y = 1 is the top of the box. */
@Composable
fun CurveEditor(
    points: CurvePoints,
    color: Color,
    onChange: (CurvePoints) -> Unit,
    onCommit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val current by rememberUpdatedState(points)
    val change by rememberUpdatedState(onChange)
    val commit by rememberUpdatedState(onCommit)
    var dragIndex by remember { mutableIntStateOf(-1) }

    fun toCurve(o: Offset, w: Float, h: Float) = (o.x / w).coerceIn(0f, 1f) to (1f - o.y / h).coerceIn(0f, 1f)
    fun nearest(o: Offset, w: Float, h: Float, radiusPx: Float): Int {
        var best = -1; var bestD = radiusPx * radiusPx
        current.points.forEachIndexed { i, (x, y) ->
            val dx = x * w - o.x; val dy = (1f - y) * h - o.y
            val d = dx * dx + dy * dy
            if (d < bestD) { bestD = d; best = i }
        }
        return best
    }

    Canvas(
        modifier
            .pointerInput(Unit) {
                val radius = 28.dp.toPx()
                detectTapGestures(onDoubleTap = { o ->
                    val i = nearest(o, size.width.toFloat(), size.height.toFloat(), radius)
                    if (i > 0 && i < current.points.lastIndex) {
                        change(CurvePoints(current.points.toMutableList().apply { removeAt(i) }))
                        commit()
                    }
                })
            }
            .pointerInput(Unit) {
                val radius = 28.dp.toPx()
                // Work on a local copy so fast drags don't wait for recomposition.
                var working = current.points
                detectDragGestures(
                    onDragStart = { o ->
                        val w = size.width.toFloat(); val h = size.height.toFloat()
                        working = current.points
                        dragIndex = nearest(o, w, h, radius)
                        if (dragIndex < 0) {
                            val p = toCurve(o, w, h)
                            working = (working + p).sortedBy { it.first }
                            dragIndex = working.indexOf(p)
                            change(CurvePoints(working))
                        }
                    },
                    onDrag = { pointer, _ ->
                        pointer.consume()
                        val i = dragIndex
                        if (i in working.indices) {
                            val w = size.width.toFloat(); val h = size.height.toFloat()
                            val (rawX, y) = toCurve(pointer.position, w, h)
                            val x = when (i) {
                                0 -> 0f
                                working.lastIndex -> 1f
                                else -> rawX.coerceIn(working[i - 1].first + 0.01f, working[i + 1].first - 0.01f)
                            }
                            working = working.toMutableList().apply { set(i, x to y) }
                            change(CurvePoints(working))
                        }
                    },
                    onDragEnd = { dragIndex = -1; commit() },
                    onDragCancel = { dragIndex = -1; commit() },
                )
            }
    ) {
        val w = size.width; val h = size.height
        val grid = Color.White.copy(alpha = 0.12f)
        for (k in 1..3) {
            drawLine(grid, Offset(w * k / 4, 0f), Offset(w * k / 4, h))
            drawLine(grid, Offset(0f, h * k / 4), Offset(w, h * k / 4))
        }
        drawRect(grid, style = Stroke(1.dp.toPx()))
        drawLine(grid, Offset(0f, h), Offset(w, 0f))

        val samples = points.sample()
        val path = Path().apply {
            samples.forEachIndexed { i, v ->
                val x = w * i / 255f; val y = h * (1f - v)
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
        }
        drawPath(path, color, style = Stroke(2.dp.toPx()))
        points.points.forEach { (x, y) ->
            drawCircle(color, radius = 6.dp.toPx(), center = Offset(x * w, (1f - y) * h))
        }
    }
}
