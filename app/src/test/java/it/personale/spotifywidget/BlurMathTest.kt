package it.personale.spotifywidget

import it.personale.spotifywidget.widget.BlurMath
import it.personale.spotifywidget.widget.WidgetColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BlurMathTest {
    private fun channel(color: Int, shift: Int) = (color shr shift) and 0xFF

    @Test
    fun aPlainImageStaysPlain() {
        val pixels = IntArray(30 * 20) { 0xFF7A6050.toInt() }
        BlurMath.gaussian(pixels, 30, 20, 4f).forEach { assertEquals(0xFF7A6050.toInt(), it) }
    }

    @Test
    fun theBlurSpreadsAnEdge() {
        // Left half black, right half white
        val width = 40
        val pixels = IntArray(width * 5) { if (it % width < width / 2) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
        val blurred = BlurMath.gaussian(pixels, width, 5, 3f)
        val row = (0 until width).map { channel(blurred[2 * width + it], 0) }
        assertEquals(0, row.first())
        assertEquals(255, row.last())
        // Half way at the edge, rising all along
        assertEquals(128f, (row[19] + row[20]) / 2f, 3f)
        assertTrue(row.zipWithNext().all { (a, b) -> b >= a })
    }

    @Test
    fun colorsAsTheSamsungWidgetAt50() {
        // Blurred wallpaper behind the Samsung widget in the screenshot, on average
        // 74, 64, 52: the widget shows 47, 36, 24 there
        val veil = WidgetColors.veil(null, dark = true, opacity = 50)
        val result = BlurMath.samsung(0xFF4A4034.toInt(), veil)
        assertEquals(47f, channel(result, 16).toFloat(), 3f)
        assertEquals(36f, channel(result, 8).toFloat(), 3f)
        assertEquals(24f, channel(result, 0).toFloat(), 3f)
    }

    @Test
    fun fullOpacityIsPlainBlack() {
        val veil = WidgetColors.veil(null, dark = true, opacity = 100)
        assertEquals(0xFF010101.toInt(), BlurMath.samsung(0xFFC08040.toInt(), veil))
    }

    @Test
    fun averageOfTheWallpaper() {
        val pixels = intArrayOf(0xFF000000.toInt(), 0xFFFF8040.toInt())
        assertEquals(0xFF7F4020.toInt(), BlurMath.average(pixels))
    }
}
