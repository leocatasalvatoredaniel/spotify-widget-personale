package it.personale.spotifywidget

import it.personale.spotifywidget.data.CardPosition
import it.personale.spotifywidget.data.WidgetTap
import it.personale.spotifywidget.widget.CardLocator
import it.personale.spotifywidget.widget.CardLocator.Tapped
import it.personale.spotifywidget.widget.PlayerMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.roundToInt

class CardLocatorTest {
    // The widget on the Galaxy S24 Ultra (1080 x 2340, 2.8125 px/dp): 404 x 216 dp for
    // the launcher, so a card of 1136 x 548 pixels in the layout, which One UI Home
    // shows at 83.8%: from (66, 244) to (1018, 704) in the screenshot
    private val density = 2.8125f
    private val cardWidth = 1136
    private val cardHeight = 548
    private val metrics = PlayerMetrics.forSize(404, 195)
    private val launcher = 0.838f
    private val onScreen = CardPosition(66, 244, 1018, 704, 1080, exact = true, scale = launcher)
    private val tolerance = CardLocator.tolerance(1080)

    /** The tap the launcher reports for a view: its corner where it is on the screen */
    private fun tap(tapped: Tapped, left: Int = onScreen.left, top: Int = onScreen.top, scale: Float = launcher): WidgetTap {
        val offset = requireNotNull(CardLocator.offset(tapped, cardWidth, cardHeight, metrics, density))
        return WidgetTap(
            view = tapped.name,
            x = (left + scale * offset.x).roundToInt(),
            y = (top + scale * offset.y).roundToInt(),
            offsetX = offset.x,
            offsetY = offset.y,
            cardWidth = cardWidth,
            cardHeight = cardHeight,
            screenWidth = 1080,
        )
    }

    private fun position(taps: List<WidgetTap>, prior: Float = 1f): CardPosition =
        CardLocator.position(requireNotNull(CardLocator.solve(taps, prior)), cardWidth, cardHeight, 1080)

    private fun assertNear(expected: CardPosition, actual: CardPosition, pixels: Int) {
        listOf(
            expected.left to actual.left,
            expected.top to actual.top,
            expected.right to actual.right,
            expected.bottom to actual.bottom,
        ).forEach { (e, a) -> assertTrue("expected $expected, got $actual", abs(e - a) <= pixels) }
    }

    @Test
    fun theButtonsAreWhereTheLayoutPutsThem() {
        // Corners of the buttons in the card, read on the layout itself at this size
        mapOf(Tapped.PREVIOUS to 306, Tapped.PLAY to 483, Tapped.NEXT to 661).forEach { (tapped, left) ->
            val offset = requireNotNull(CardLocator.offset(tapped, cardWidth, cardHeight, metrics, density))
            assertEquals(left.toFloat(), offset.x, 2f)
            assertEquals(353f, offset.y, 2f)
        }
        assertEquals(CardLocator.Offset(0f, 0f), CardLocator.offset(Tapped.CARD, cardWidth, cardHeight, metrics, density))
    }

    @Test
    fun theScreenshotOfThePhoneAgrees() {
        // Centers of previous, play and next in the screenshot: (395, 612), (542, 612), (689, 612)
        val half = 167 / 2f
        mapOf(Tapped.PREVIOUS to 395, Tapped.PLAY to 542, Tapped.NEXT to 689).forEach { (tapped, x) ->
            val offset = requireNotNull(CardLocator.offset(tapped, cardWidth, cardHeight, metrics, density))
            assertEquals(x.toFloat(), onScreen.left + launcher * (offset.x + half), 4f)
            assertEquals(612f, onScreen.top + launcher * (offset.y + half), 4f)
        }
    }

    @Test
    fun theCardAndAButtonMeasureTheScaleOfTheLauncher() {
        val taps = listOf(tap(Tapped.CARD), tap(Tapped.PLAY))
        val solution = requireNotNull(CardLocator.solve(taps, prior = 1f))
        assertTrue(solution.measured)
        assertEquals(launcher, solution.scale, 0.004f)
        assertNear(onScreen, position(taps), 3)
    }

    @Test
    fun twoButtonsMeasureItToo() {
        val taps = listOf(tap(Tapped.PREVIOUS), tap(Tapped.NEXT))
        assertEquals(launcher, requireNotNull(CardLocator.solve(taps, prior = 1f)).scale, 0.01f)
        assertNear(onScreen, position(taps), 6)
    }

    @Test
    fun oneTapNeedsTheScaleMeasuredBefore() {
        val taps = listOf(tap(Tapped.PLAY))
        assertFalse(requireNotNull(CardLocator.solve(taps, prior = launcher)).measured)
        assertNear(onScreen, position(taps, prior = launcher), 2)
        // Without it the card is as large as its layout and out of place: what earlier versions drew
        val unscaled = position(taps, prior = 1f)
        assertEquals(cardWidth, unscaled.width)
        assertTrue(onScreen.left - unscaled.left > 60)
    }

    @Test
    fun aCardTappedWithoutScalingStaysAsItIs() {
        val taps = listOf(tap(Tapped.CARD, scale = 1f), tap(Tapped.NEXT, scale = 1f))
        assertEquals(1f, requireNotNull(CardLocator.solve(taps, prior = 1f)).scale, 0.004f)
        assertNear(CardPosition(66, 244, 66 + cardWidth, 244 + cardHeight, 1080, exact = true), position(taps), 2)
    }

    @Test
    fun aLaterTapOnTheSameViewReplacesTheEarlierOne() {
        val taps = CardLocator.add(listOf(tap(Tapped.CARD), tap(Tapped.PLAY)), tap(Tapped.PLAY), launcher, tolerance)
        assertEquals(listOf(Tapped.CARD.name, Tapped.PLAY.name), taps.map { it.view })
    }

    @Test
    fun aMovedWidgetStartsFromTheNewTap() {
        val before = listOf(tap(Tapped.CARD), tap(Tapped.PLAY))
        // Moved two rows down: the earlier taps no longer tell where it is
        val moved = tap(Tapped.NEXT, top = onScreen.top + 520)
        val taps = CardLocator.add(before, moved, launcher, tolerance)
        assertEquals(listOf(moved), taps)
        assertNear(
            CardPosition(66, 764, 1018, 1224, 1080, exact = false, scale = launcher),
            position(taps, prior = launcher),
            2,
        )
    }

    @Test
    fun tapsOnAWidgetOfAnotherSizeAreLeftOut() {
        val resized = tap(Tapped.PLAY).copy(cardHeight = cardHeight + 300)
        assertEquals(listOf(resized), CardLocator.add(listOf(tap(Tapped.CARD)), resized, launcher, tolerance))
    }

    @Test
    fun buttonsHiddenInANarrowWidgetTellNothing() {
        val narrow = PlayerMetrics.forSize(200, 160)
        assertNull(CardLocator.offset(Tapped.ACTION_START, 562, 450, narrow, density))
        assertTrue(CardLocator.offset(Tapped.NEXT, 562, 450, narrow, density) != null)
    }

    @Test
    fun noTapsNoCard() {
        assertNull(CardLocator.solve(emptyList(), prior = 1f))
    }
}
