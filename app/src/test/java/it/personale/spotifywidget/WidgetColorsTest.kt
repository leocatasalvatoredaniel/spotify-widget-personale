package it.personale.spotifywidget

import it.personale.spotifywidget.widget.WidgetColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetColorsTest {
    @Test
    fun progressColorStandsOutOnTheDarkenedCover() {
        val covers = listOf(
            0xFFFFFFFF, 0xFFFFEB3B, 0xFF1ED760, 0xFFE91E63, 0xFF2196F3,
            0xFF795548, 0xFF000000, 0xFF3F51B5, 0xFF00BCD4, 0xFFFF9800,
        ).map { it.toInt() }
        covers.forEach { cover ->
            val accent = WidgetColors.accent(cover)
            val contrast = WidgetColors.contrast(accent, WidgetColors.DARKENED_COVER)
            assertTrue("cover ${Integer.toHexString(cover)}: $contrast", contrast >= 3.0)
        }
    }

    @Test
    fun lightColorsAreKept() {
        assertEquals(0xFFFF9800.toInt(), WidgetColors.accent(0xFFFF9800.toInt()))
    }

    @Test
    fun contrastOfBlackAndWhite() {
        assertEquals(21.0, WidgetColors.contrast(WidgetColors.WHITE, WidgetColors.BLACK), 0.01)
    }
}
