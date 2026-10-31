package it.personale.spotifywidget

import it.personale.spotifywidget.widget.WidgetColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetColorsTest {
    private fun assertColor(expected: Int, actual: Int) {
        // One step of rounding per channel is fine
        for (shift in listOf(16, 8, 0)) {
            val e = (expected shr shift) and 0xFF
            val a = (actual shr shift) and 0xFF
            assertEquals("channel $shift of ${Integer.toHexString(actual)}", e.toFloat(), a.toFloat(), 2f)
        }
    }

    @Test
    fun colorfulCoversGiveTheirHueFullySaturated() {
        // Colors of the system player on a Galaxy S24 Ultra
        assertColor(0xFFE42E00.toInt(), WidgetColors.progressColor(WidgetColors.fromHsl(12f, 0.6f, 0.5f)))
        assertColor(0xFFE48600.toInt(), WidgetColors.progressColor(WidgetColors.fromHsl(35f, 0.4f, 0.7f)))
    }

    @Test
    fun blackAndWhiteCoversGiveNightBlue() {
        assertEquals(WidgetColors.NEUTRAL, WidgetColors.progressColor(null))
        assertEquals(WidgetColors.NEUTRAL, WidgetColors.progressColor(0xFF808080.toInt()))
        assertEquals(WidgetColors.NEUTRAL, WidgetColors.progressColor(0xFF7A7570.toInt()))
    }

    @Test
    fun hslRoundTrip() {
        listOf(0xFFE42E00, 0xFF2D3854, 0xFF1ED760, 0xFF3F51B5).map { it.toInt() }.forEach { color ->
            val (h, s, l) = WidgetColors.hsl(color)
            assertColor(color, WidgetColors.fromHsl(h, s, l))
        }
    }

    @Test
    fun contrastOfBlackAndWhite() {
        assertEquals(21.0, WidgetColors.contrast(WidgetColors.WHITE, WidgetColors.BLACK), 0.01)
    }

    @Test
    fun veilAsTheSamsungWidgets() {
        // Plain black at full opacity, as the Samsung widgets
        assertEquals(0xFF010101.toInt(), WidgetColors.veil(0xFF8A5A30.toInt(), dark = true, opacity = 100))
        // 29% at opacity 0 and 64% at 50 (measured)
        assertEquals(0x4A, WidgetColors.veil(null, dark = true, opacity = 0) ushr 24)
        assertEquals(0xA4, WidgetColors.veil(null, dark = true, opacity = 50) ushr 24)
        // Without opacity the veil takes the color of the wallpaper, dark or light
        val (hue, saturation, lightness) = WidgetColors.hsl(WidgetColors.veil(0xFF8A5A30.toInt(), dark = true, opacity = 0))
        assertEquals(28f, hue, 3f)
        assertTrue(saturation > 0.3f)
        assertEquals(0.1f, lightness, 0.02f)
        assertTrue(WidgetColors.hsl(WidgetColors.veil(0xFF8A5A30.toInt(), dark = false, opacity = 0)).third > 0.9f)
    }
}
