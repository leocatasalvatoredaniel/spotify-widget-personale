package it.personale.spotifywidget

import it.personale.spotifywidget.data.CardPosition
import it.personale.spotifywidget.widget.CardLocator
import it.personale.spotifywidget.widget.CardLocator.Bounds
import it.personale.spotifywidget.widget.CardLocator.Tapped
import it.personale.spotifywidget.widget.PlayerMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.roundToInt

class CardLocatorTest {
    // The widget in the screenshot of the Galaxy S24 Ultra (1080 x 2340, 2.8125 px/dp):
    // card from (67, 245) to (1019, 705); play centered at (541, 611), previous at 393
    private val density = 2.8125f
    private val cardWidth = 952
    private val cardHeight = 460
    private val metrics = PlayerMetrics.forSize((cardWidth / density).roundToInt(), (cardHeight / density).roundToInt())

    private fun button(x: Int, y: Int): Bounds {
        val half = (24 * metrics.scale * density).roundToInt()
        return Bounds(x - half, y - half, x + half, y + half)
    }

    private fun locate(tapped: Tapped, bounds: Bounds) =
        CardLocator.locate(tapped, bounds, cardWidth, cardHeight, metrics, density, 1080)

    private fun assertNear(expected: CardPosition, actual: CardPosition?, tolerance: Int) {
        requireNotNull(actual)
        listOf(
            expected.left to actual.left,
            expected.top to actual.top,
            expected.right to actual.right,
            expected.bottom to actual.bottom,
        ).forEach { (e, a) -> assertTrue("expected $expected, got $actual", abs(e - a) <= tolerance) }
    }

    private val screenshot = CardPosition(67, 245, 1019, 705, 1080, exact = true)

    @Test
    fun theCardItselfIsExact() {
        val card = locate(Tapped.CARD, Bounds(67, 245, 1019, 705))
        assertEquals(screenshot, card)
    }

    @Test
    fun playTellsTheCardAsInTheScreenshot() {
        assertNear(screenshot, locate(Tapped.PLAY, button(541, 611)), 6)
    }

    @Test
    fun previousAndNextAreACellAside() {
        assertNear(screenshot, locate(Tapped.PREVIOUS, button(393, 611)), 8)
        assertNear(screenshot, locate(Tapped.NEXT, button(689, 611)), 8)
    }

    @Test
    fun aNarrowWidgetHasNoSideButtons() {
        val narrow = PlayerMetrics.forSize(200, 160)
        assertNull(CardLocator.locate(Tapped.ACTION_END, Bounds(0, 0, 10, 10), 560, 450, narrow, density, 1080))
    }

    @Test
    fun aButtonDoesNotMoveTheExactCard() {
        val estimate = locate(Tapped.PLAY, button(545, 615))!!
        assertSame(screenshot, CardLocator.update(screenshot, estimate, density))
        // Moved to another place of the home screen: the button wins
        val moved = locate(Tapped.PLAY, button(541, 1400))!!
        assertEquals(moved, CardLocator.update(screenshot, moved, density))
        // A new tap on the card always wins
        val card = screenshot.copy(top = 250, bottom = 710)
        assertEquals(card, CardLocator.update(screenshot, card, density))
    }

    @Test
    fun theCardFollowsTheResolution() {
        // From FHD+ to QHD+ (1440 pixels wide)
        assertEquals(CardPosition(89, 327, 1359, 940, 1440, exact = true), screenshot.onScreen(1440))
    }
}
