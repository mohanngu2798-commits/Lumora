package app.lumora.editor.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TilesTest {
    @Test fun tilesCoverEveryPixelExactlyOnce() {
        for ((w, h) in listOf(1 to 1, 42 to 42, 43 to 10, 500 to 333, 1200 to 900)) {
            val seen = IntArray(w * h)
            for (t in Tiles.plan(w, h, 50, 4)) {
                assertTrue(t.x - t.readX == 4 && t.y - t.readY == 4)
                for (y in t.y until t.y + t.keepH) for (x in t.x until t.x + t.keepW) seen[y * w + x]++
            }
            assertTrue("gaps or overlaps at ${w}x$h", seen.all { it == 1 })
        }
    }
}

class AutoEnhanceTest {
    private fun solid(r: Int, g: Int, b: Int, n: Int = 1000) = IntArray(n) { (0xFF shl 24) or (r shl 16) or (g shl 8) or b }

    @Test fun darkPhotoGetsBrighter() {
        assertTrue(AutoEnhance.analyze(solid(30, 30, 30)).exposure > 0.3f)
    }

    @Test fun brightPhotoGetsDarker() {
        assertTrue(AutoEnhance.analyze(solid(230, 230, 230)).exposure < 0f)
    }

    @Test fun blueCastGetsWarmed() {
        assertTrue(AutoEnhance.analyze(solid(90, 110, 160)).warmth > 0.05f)
    }

    @Test fun neutralMidGreyIsLeftAlmostAlone() {
        val p = AutoEnhance.analyze(solid(118, 118, 118))
        assertEquals(0f, p.warmth, 0.01f)
        assertTrue(kotlin.math.abs(p.exposure) < 0.1f)
    }

    @Test fun keepsCrop() {
        val base = EditParams(crop = CropSpec(aspect = 1f))
        assertEquals(base.crop, AutoEnhance.analyze(solid(50, 60, 70), base).crop)
    }
}

class CarouselTest {
    private val ids = listOf(1L, 2L, 3L, 4L)

    @Test fun panoramaCoversWholeStrip() {
        val c = CarouselLayouts.panorama(Carousel(slides = 3), listOf(3f), ids)
        val l = c.layers.single()
        assertTrue(l.width >= c.width - 1e-4f && l.height >= 1f - 1e-4f)
        assertEquals(c.width / 2, l.cx, 1e-4f)
    }

    @Test fun onePerSlideCentresEachPhotoInItsSlide() {
        val c = CarouselLayouts.onePerSlide(Carousel(slides = 2), listOf(1f, 1.5f, 0.7f), ids)
        assertEquals(3, c.slides)
        c.layers.forEachIndexed { i, l ->
            assertEquals(c.slideWidth * (i + 0.5f), l.cx, 1e-4f)
            assertTrue(l.width <= c.slideWidth && l.height <= 1f)
        }
    }

    @Test fun seamsSitOnSlideBoundaries() {
        val c = CarouselLayouts.seams(Carousel(slides = 2), listOf(1f, 1f, 1f), ids)
        assertEquals(4, c.slides)
        c.layers.forEachIndexed { i, l -> assertEquals(c.slideWidth * (i + 1), l.cx, 1e-4f) }
    }

    @Test fun pixelSizeMatchesInstagram() {
        assertEquals(3240 to 1350, Carousel(format = SlideFormat.Portrait, slides = 3).pixelSize())
    }
}
