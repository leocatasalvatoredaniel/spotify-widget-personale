package it.personale.spotifywidget

import it.personale.spotifywidget.widget.Alignment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.exp
import kotlin.random.Random

class AlignmentTest {
    // A blurred photo: soft blobs of light and dark, as a blurred wallpaper
    private val imageWidth = 160
    private val imageHeight = 240
    private val image = FloatArray(imageWidth * imageHeight).also { pixels ->
        val random = Random(7)
        val blobs = List(14) { Triple(random.nextFloat() * imageWidth, random.nextFloat() * imageHeight, random.nextFloat() * 200 - 100) }
        for (y in 0 until imageHeight) for (x in 0 until imageWidth) {
            pixels[y * imageWidth + x] = 100 + blobs.sumOf { (bx, by, amount) ->
                val d = ((x - bx) * (x - bx) + (y - by) * (y - by)) / (2 * 22f * 22f)
                (amount * exp(-d)).toDouble()
            }.toFloat()
        }
    }

    // A screen of the proportions of the Galaxy S24 Ultra (1080 x 2340), small
    private val width = 135
    private val height = 292

    /**
     * Screenshot of a page of the Home screen without widgets: the wallpaper
     * placed with the [alignment], a few icons, the row of the dock below
     */
    private fun screenshot(alignment: Alignment): FloatArray {
        val random = Random(3)
        return FloatArray(width * height) { i ->
            val x = i % width
            val y = i / width
            val (u, v) = alignment.imagePoint(x.toFloat(), y.toFloat(), width, height, imageWidth, imageHeight)
            val wallpaper = image[v.toInt().coerceIn(0, imageHeight - 1) * imageWidth + u.toInt().coerceIn(0, imageWidth - 1)]
            val icon = (y in 150..166 && (x - 10) % 32 < 16) || y > 230
            (if (icon) 230f else wallpaper) + random.nextFloat() * 6
        }
    }

    // As the app uses the screenshot: without the status bar and the dock
    private val use = BooleanArray(width * height) { i -> i / width in 12..215 }

    @Test
    fun findsTheFramingOfOneUi() {
        // As measured on the phone: zoom 1.20, shifted down
        val actual = Alignment(1.2f, 0.015f, 0.12f)
        val found = Alignment.find(screenshot(actual), width, height, image, imageWidth, imageHeight, use)
        assertEquals(1.2f, found.alignment.zoom, 0.02f)
        assertEquals(0.015f, found.alignment.shiftX, 0.012f)
        assertEquals(0.12f, found.alignment.shiftY, 0.012f)
        assertTrue(found.match > found.matchBefore)
    }

    @Test
    fun aPlainWallpaperStaysAsItIs() {
        val found = Alignment.find(screenshot(Alignment.NONE), width, height, image, imageWidth, imageHeight, use)
        assertEquals(1f, found.alignment.zoom, 0.02f)
        assertEquals(0f, found.alignment.shiftX, 0.012f)
        assertEquals(0f, found.alignment.shiftY, 0.012f)
    }

    @Test
    fun keptAsText() {
        val alignment = Alignment(1.2f, 0.0148f, -0.1185f)
        assertEquals(alignment, Alignment.parse(alignment.format()))
        assertEquals(null, Alignment.parse("x"))
    }
}
