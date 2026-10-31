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
    fun colorsAsTheSamsungWidgetsAt50() {
        // Wallpaper behind the Samsung widget in the screenshots, on average 68, 58, 46:
        // the dark widget shows 48, 37, 25 there, the light one 124, 113, 101
        val wallpaper = 0xFF443A2E.toInt()
        val dark = BlurMath.samsung(wallpaper, WidgetColors.blurVeil(dark = true, opacity = 50))
        assertEquals(48f, channel(dark, 16).toFloat(), 2f)
        assertEquals(37f, channel(dark, 8).toFloat(), 2f)
        assertEquals(25f, channel(dark, 0).toFloat(), 2f)
        val light = BlurMath.samsung(wallpaper, WidgetColors.blurVeil(dark = false, opacity = 50))
        assertEquals(124f, channel(light, 16).toFloat(), 2f)
        assertEquals(113f, channel(light, 8).toFloat(), 2f)
        assertEquals(101f, channel(light, 0).toFloat(), 2f)
    }

    @Test
    fun fullOpacityIsPlainBlack() {
        val veil = WidgetColors.blurVeil(dark = true, opacity = 100)
        assertEquals(0xFF010101.toInt(), BlurMath.samsung(0xFFC08040.toInt(), veil))
    }

    @Test
    fun averageOfTheWallpaper() {
        val pixels = intArrayOf(0xFF000000.toInt(), 0xFFFF8040.toInt())
        assertEquals(0xFF7F4020.toInt(), BlurMath.average(pixels))
    }

    @Test
    fun aWallpaperDrawnByAnAppIsBlurredMore() {
        assertEquals(BlurMath.BLUR_DP, BlurMath.blurDp(drawnByApp = false), 0f)
        // The blur of the Home screen and the one of the widget add up
        assertEquals(20.8f, BlurMath.blurDp(drawnByApp = true), 0.1f)
    }
}
