package it.personale.spotifywidget.widget

import it.personale.spotifywidget.data.CardPosition
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Where the card of a widget is on the screen. Android does not tell a widget
 * its position, but a tap gives the bounds of the view that was touched: the
 * card itself, or a button whose place in the card is known from the layout
 * (widget_player and [PlayerMetrics]).
 */
object CardLocator {
    /** What was tapped, with the cell of the controls of the buttons (0 = play) */
    enum class Tapped(val cell: Int) {
        CARD(0),
        PLAY(0),
        PREVIOUS(-1),
        NEXT(1),
        ACTION_START(-2),
        ACTION_END(2),
    }

    /** Bounds of a view on the screen, in pixels */
    data class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val centerX: Float get() = (left + right) / 2f
        val centerY: Float get() = (top + bottom) / 2f
    }

    /**
     * The card, from the [bounds] of the [tapped] view. [cardWidth] and
     * [cardHeight] are the size of the card in pixels, [density] the pixels
     * of a dp. Null when a button cannot tell the card: size unknown, or a
     * button hidden in a narrow widget.
     */
    fun locate(
        tapped: Tapped,
        bounds: Bounds,
        cardWidth: Int,
        cardHeight: Int,
        metrics: PlayerMetrics,
        density: Float,
        screenWidth: Int,
    ): CardPosition? {
        if (tapped == Tapped.CARD) {
            return CardPosition(bounds.left, bounds.top, bounds.right, bounds.bottom, screenWidth, exact = true)
        }
        if (cardWidth <= 0 || cardHeight <= 0) return null
        if (!metrics.showActions && abs(tapped.cell) > 1) return null
        val scale = metrics.scale * density
        // The controls leave 24 + 12 dp at the start and 20 + 16 at the end:
        // the play button is in the middle of the card, the others a cell aside
        val cells = if (metrics.showActions) 5 else 3
        val cell = (cardWidth - CONTROLS_SIDES * scale) / cells
        val centerX = bounds.centerX - tapped.cell * cell
        // The controls are the last row: 48 dp high, 8 dp above the bottom
        val bottom = (bounds.centerY + (PAD_BOTTOM + CONTROLS / 2) * scale).roundToInt()
        val left = (centerX - cardWidth / 2f).roundToInt()
        return CardPosition(left, bottom - cardHeight, left + cardWidth, bottom, screenWidth, exact = false)
    }

    /**
     * The position to keep after a tap: a card worked out from a button does
     * not replace the exact one unless the widget was moved
     */
    fun update(current: CardPosition?, found: CardPosition, density: Float): CardPosition {
        if (current == null || found.exact || !current.exact) return found
        val known = current.onScreen(found.screenWidth)
        val tolerance = MOVED_DP * density
        val moved = abs(known.left - found.left) > tolerance || abs(known.top - found.top) > tolerance
        return if (moved) found else known
    }

    private const val CONTROLS_SIDES = 24 + 12 + 20 + 16
    private const val PAD_BOTTOM = 8
    private const val CONTROLS = 48

    /** Below this shift (dp) a button tells the same place as the exact card */
    private const val MOVED_DP = 24
}
