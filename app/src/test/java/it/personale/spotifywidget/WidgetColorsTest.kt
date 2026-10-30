package it.personale.spotifywidget

import it.personale.spotifywidget.widget.WidgetColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetColorsTest {
    @Test
    fun whiteTextIsReadableOnEveryCoverColor() {
        val covers = listOf(
            0xFFFFFFFF, 0xFFFFEB3B, 0xFF1ED760, 0xFFE91E63, 0xFF2196F3,
            0xFF795548, 0xFF000000, 0xFF9E9E9E, 0xFF00BCD4, 0xFFFF9800,
        ).map { it.toInt() }
        covers.forEach { cover ->
            val colors = WidgetColors.fromCover(cover)
            val contrast = WidgetColors.contrast(WidgetColors.WHITE, colors.background)
            assertTrue("cover ${Integer.toHexString(cover)}: $contrast", contrast >= 7.0)
        }
    }

    @Test
    fun contrastOfBlackAndWhite() {
        assertEquals(21.0, WidgetColors.contrast(WidgetColors.WHITE, WidgetColors.BLACK), 0.01)
    }

    @Test
    fun likedGreenIsReadableOnTheBackground() {
        val background = WidgetColors.fromCover(0xFF1ED760.toInt()).background
        assertTrue(WidgetColors.contrast(0xFF1ED760.toInt(), background) >= 3.0)
    }
}
