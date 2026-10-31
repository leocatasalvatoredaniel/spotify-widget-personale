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
        val brown = 0xFF8A5A30.toInt()
        // Plain black at full opacity, as the Samsung widgets
        assertEquals(0xFF010101.toInt(), WidgetColors.veil(brown, dark = true, opacity = 100))
        // 29% at opacity 0 (settings preview) and 58.5% at 50 (Home screen)
        assertEquals(0x4A, WidgetColors.veil(null, dark = true, opacity = 0) ushr 24)
        assertEquals(0x95, WidgetColors.veil(null, dark = true, opacity = 50) ushr 24)
        // Without the colors of the wallpaper a neutral gray of 18
        assertEquals(0x95121212.toInt(), WidgetColors.veil(null, dark = true, opacity = 50))
        // With them, the hue of the wallpaper, dark
        val (hue, saturation, lightness) = WidgetColors.hsl(WidgetColors.veil(brown, dark = true, opacity = 50))
        assertEquals(28f, hue, 8f)
        assertTrue(saturation > 0.5f)
        assertTrue(lightness < 0.1f)
        // Light: a light gray, the same cover
        assertEquals(0x959A9A9A.toInt(), WidgetColors.veil(null, dark = false, opacity = 50))
        assertEquals(0xFFFCFCFE.toInt(), WidgetColors.veil(brown, dark = false, opacity = 100))
    }

    @Test
    fun lightVeilAsTheSamsungWidgetOnTheHomeScreen() {
        // Wallpaper behind the widgets (average 68, 58, 46): the light Samsung widget
        // at 50% gives on average 124, 113, 101 there
        val veil = WidgetColors.veil(0xFF443A2E.toInt(), dark = false, opacity = 50)
        val alpha = (veil ushr 24) / 255f
        val behind = listOf(68.1f, 58.5f, 46.2f)
        val samsung = listOf(124.2f, 112.7f, 100.6f)
        listOf(16, 8, 0).forEachIndexed { i, shift ->
            val result = alpha * ((veil shr shift) and 0xFF) + (1 - alpha) * behind[i]
            assertEquals(samsung[i], result, 2f)
        }
    }

    @Test
    fun blurVeilAsMeasured() {
        assertEquals(0x790A0F1B, WidgetColors.blurVeil(dark = true, opacity = 50))
        assertEquals(0x79AAAFBB, WidgetColors.blurVeil(dark = false, opacity = 50))
        assertEquals(0xFF010101.toInt(), WidgetColors.blurVeil(dark = true, opacity = 100))
        assertEquals(0x36, WidgetColors.blurVeil(dark = true, opacity = 0) ushr 24)
    }

    @Test
    fun veilAsTheSamsungWidgetOnTheHomeScreen() {
        // Wallpaper behind the widgets in the screenshot (average 74, 64, 52):
        // Samsung at 50% gives on average 47, 36, 24 there
        val veil = WidgetColors.veil(0xFF4A4034.toInt(), dark = true, opacity = 50)
        val alpha = (veil ushr 24) / 255f
        val behind = listOf(73.6f, 64.1f, 51.8f)
        val samsung = listOf(47.1f, 35.6f, 23.8f)
        listOf(16, 8, 0).forEachIndexed { i, shift ->
            val result = alpha * ((veil shr shift) and 0xFF) + (1 - alpha) * behind[i]
            assertEquals(samsung[i], result, 2f)
        }
    }

    @Test
    fun veilOfAStrongWallpaperStaysDark() {
        // A strongly colored wallpaper does not turn the veil into a bright color
        val veil = WidgetColors.veil(0xFF0040FF.toInt(), dark = true, opacity = 50)
        listOf(16, 8, 0).forEach { assertTrue(((veil shr it) and 0xFF) <= 18 + 20) }
    }

    @Test
    fun theMainHueLeadsNotASmallBrightDetail() {
        // As the cover of «Fuoco di paglia»: pink sky and skin, blue water, small yellow hair
        val cover = listOf(
            WidgetColors.CoverColor(0xFFC88C8C.toInt(), 30),
            WidgetColors.CoverColor(0xFF893C23.toInt(), 25),
            WidgetColors.CoverColor(0xFF2E4F86.toInt(), 25),
            WidgetColors.CoverColor(0xFFE2E24A.toInt(), 6),
            WidgetColors.CoverColor(0xFF1A1A1A.toInt(), 14),
        )
        val (hue, _, _) = WidgetColors.hsl(WidgetColors.progressColor(WidgetColors.sourceColor(cover)))
        assertTrue("hue $hue", hue < 40f)
    }

    @Test
    fun coversWithoutColorsHaveNoSource() {
        val cover = listOf(
            WidgetColors.CoverColor(0xFF202020.toInt(), 50),
            WidgetColors.CoverColor(0xFFA0A0A0.toInt(), 40),
            WidgetColors.CoverColor(0xFFF0F0F0.toInt(), 10),
        )
        assertEquals(null, WidgetColors.sourceColor(cover))
        assertEquals(WidgetColors.NEUTRAL, WidgetColors.progressColor(WidgetColors.sourceColor(cover)))
    }
}
