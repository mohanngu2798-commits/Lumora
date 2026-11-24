package app.lumora.editor.model

import kotlin.math.max
import kotlin.math.min

/** Instagram post shapes. [aspect] is width / height of one slide. */
enum class SlideFormat(val label: String, val aspect: Float, val pxW: Int, val pxH: Int) {
    Portrait("4:5", 4f / 5f, 1080, 1350),
    Square("1:1", 1f, 1080, 1080),
    Story("9:16", 9f / 16f, 1080, 1920),
}

/**
 * One photo placed on the carousel canvas. Canvas units: the canvas is 1 tall and
 * slides × aspect wide. ([cx], [cy]) is the photo's centre, [height] its height, [rotation] in degrees.
 */
data class Layer(
    val id: Long,
    val photoAspect: Float,   // photo width / height
    val cx: Float,
    val cy: Float,
    val height: Float,
    val rotation: Float = 0f,
    val corner: Float = 0f,   // corner radius as a fraction of the shorter side, 0..0.5
) {
    val width get() = height * photoAspect
}

sealed interface Background {
    data class Solid(val argb: Int) : Background
    data class Gradient(val from: Int, val to: Int) : Background
    /** The first photo, blurred and stretched to fill: the "no crop" Instagram look. */
    data object BlurredPhoto : Background
}

data class Carousel(
    val format: SlideFormat = SlideFormat.Portrait,
    val slides: Int = 3,
    val layers: List<Layer> = emptyList(),
    val background: Background = Background.Solid(0xFFFFFFFF.toInt()),
    val gap: Float = 0.04f,   // margin used by the auto layouts, in canvas units
) {
    val width get() = slides * format.aspect
    val slideWidth get() = format.aspect

    /** Output pixel size of the whole strip. */
    fun pixelSize(): Pair<Int, Int> = format.pxW * slides to format.pxH
}

/** Ready-made arrangements, inspired by popular carousel trends, computed from the photos' shapes. */
object CarouselLayouts {

    /** One photo stretched across every slide (panorama swipe). Extra photos are ignored. */
    fun panorama(c: Carousel, aspects: List<Float>, ids: List<Long>): Carousel {
        if (aspects.isEmpty()) return c
        val a = aspects[0]
        // Cover the whole strip.
        val h = max(1f, c.width / a)
        return c.copy(layers = listOf(Layer(ids[0], a, c.width / 2, 0.5f, h)))
    }

    /** One photo per slide, fitted with an even margin. Adds slides if there are more photos. */
    fun onePerSlide(c: Carousel, aspects: List<Float>, ids: List<Long>): Carousel {
        val slides = aspects.size.coerceIn(1, 10)
        val base = c.copy(slides = max(c.slides, slides))
        val layers = aspects.take(10).mapIndexed { i, a ->
            val boxW = base.slideWidth - c.gap * 2; val boxH = 1f - c.gap * 2
            val h = min(boxH, boxW / a)
            Layer(ids[i], a, base.slideWidth * (i + 0.5f), 0.5f, h)
        }
        return base.copy(layers = layers)
    }

    /**
     * Photos sit across the seams between slides, so each swipe reveals the other half: the
     * signature seamless-carousel look. Alternates high and low for rhythm.
     */
    fun seams(c: Carousel, aspects: List<Float>, ids: List<Long>): Carousel {
        if (aspects.isEmpty()) return c
        val slides = max(c.slides, aspects.size + 1).coerceAtMost(10)
        val base = c.copy(slides = slides)
        val layers = aspects.take(slides - 1).mapIndexed { i, a ->
            val h = min(0.62f, base.slideWidth * 0.9f / a)
            val cy = if (i % 2 == 0) 0.36f else 0.64f
            Layer(ids[i], a, base.slideWidth * (i + 1), cy, h, rotation = if (i % 2 == 0) -3f else 3f, corner = 0.04f)
        }
        return base.copy(layers = layers)
    }

    /** A photo-dump collage: photos scattered in a loose grid with a slight tilt. */
    fun dump(c: Carousel, aspects: List<Float>, ids: List<Long>): Carousel {
        if (aspects.isEmpty()) return c
        val perSlide = 2
        val slides = ((aspects.size + perSlide - 1) / perSlide).coerceIn(1, 10)
        val base = c.copy(slides = max(c.slides, slides))
        val layers = aspects.take(slides * perSlide).mapIndexed { i, a ->
            val slide = i / perSlide; val top = i % perSlide == 0
            val h = min(0.44f, base.slideWidth * 0.7f / a)
            val cx = base.slideWidth * (slide + if (top) 0.42f else 0.58f)
            val cy = if (top) 0.28f else 0.72f
            Layer(ids[i], a, cx, cy, h, rotation = listOf(-4f, 3f, -2f, 5f)[i % 4], corner = 0.02f)
        }
        return base.copy(layers = layers)
    }
}
