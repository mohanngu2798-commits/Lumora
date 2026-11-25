package app.lumora.editor.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import app.lumora.editor.R
import kotlin.math.max
import kotlin.math.roundToInt

/** AI-generated example pictures (no real people) used on the home and intro screens. */
object Samples {
    val wall = listOf(R.drawable.sample_wall_1, R.drawable.sample_wall_2, R.drawable.sample_wall_3, R.drawable.sample_wall_4,
        R.drawable.sample_wall_5, R.drawable.sample_wall_6, R.drawable.sample_wall_7, R.drawable.sample_wall_8)
    val collage = listOf(R.drawable.sample_collage_1, R.drawable.sample_collage_2, R.drawable.sample_collage_3, R.drawable.sample_collage_4)

    /** Example picture for an intro "what do you post" tile. */
    fun forTopic(label: String): Int = when (label) {
        "Travel" -> R.drawable.sample_travel
        "Food" -> R.drawable.sample_food
        "Photo dump" -> R.drawable.sample_photo_dump
        "Wedding" -> R.drawable.sample_wedding
        "Fashion" -> R.drawable.sample_fashion
        "Daily life" -> R.drawable.sample_daily_life
        "Landscape" -> R.drawable.sample_landscape
        "Business" -> R.drawable.sample_business
        else -> R.drawable.sample_festivals
    }
}

@Composable
fun rememberImages(ids: List<Int>): List<ImageBitmap> = ids.map { ImageBitmap.imageResource(it) }

/** Draws [img] centre-cropped into a rounded rectangle. */
fun DrawScope.drawPhoto(img: ImageBitmap, topLeft: Offset, size: Size, radius: Float = 0f, filter: ColorFilter? = null) {
    val s = max(size.width / img.width, size.height / img.height)
    val sw = size.width / s; val sh = size.height / s
    val src = IntOffset(((img.width - sw) / 2).roundToInt().coerceAtLeast(0), ((img.height - sh) / 2).roundToInt().coerceAtLeast(0))
    val srcSize = IntSize(sw.roundToInt().coerceAtMost(img.width), sh.roundToInt().coerceAtMost(img.height))
    val path = Path().apply { addRoundRect(RoundRect(topLeft.x, topLeft.y, topLeft.x + size.width, topLeft.y + size.height, CornerRadius(radius))) }
    clipPath(path) {
        drawImage(img, srcOffset = src, srcSize = srcSize,
            dstOffset = IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()),
            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()), colorFilter = filter)
    }
}
