package it.personale.spotifywidget.widget

import it.personale.spotifywidget.data.CardPosition
import it.personale.spotifywidget.data.WidgetTap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Where the card of a widget is on the screen, and how large. Android does
 * not tell a widget its position, but a tap gives the bounds of the view
 * that was touched: the card itself, or a button whose place in the card is
 * known from the layout (widget_player and [PlayerMetrics]).
 *
 * The launcher can show the widgets smaller than their layout (One UI Home
 * does, on a dense grid: 84% on the Galaxy S24 Ultra this was measured on).
 * The bounds of a tap then have the corner of the view where it is on the
 * screen, but its size before the scaling: a card worked out from one tap
 * alone is too large and, from a button, out of place. Two taps on different
 * views tell the scale: the views are as far apart on the screen as in the
 * layout, times the scale.
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

    /** A point in pixels */
    data class Offset(val x: Float, val y: Float)

    /**
     * Top left corner of the [tapped] view in the card, in pixels of the
     * layout (before the launcher scales the widget). [cardWidth] and
     * [cardHeight] are the size of the card in the layout. Null for a
     * button the widget does not show at this size.
     */
    fun offset(tapped: Tapped, cardWidth: Int, cardHeight: Int, metrics: PlayerMetrics, density: Float): Offset? {
        if (tapped == Tapped.CARD) return Offset(0f, 0f)
        if (cardWidth <= 0 || cardHeight <= 0) return null
        val cells = if (metrics.showActions) 5 else 3
        if (abs(tapped.cell) > cells / 2) return null
        val unit = metrics.scale * density
        // The controls leave 24 + 12 dp at the start and 20 + 16 at the end, and
        // share the rest in equal cells with a button of 48 dp in the middle of each
        val cell = (cardWidth - CONTROLS_SIDES * unit) / cells
        val index = tapped.cell + cells / 2
        val left = CONTROLS_START * unit + index * cell + (cell - BUTTON * unit) / 2
        // The controls are the last row: 48 dp high, 8 dp above the bottom
        val top = cardHeight - (PAD_BOTTOM + BUTTON) * unit
        return Offset(left, top)
    }

    /** Where the card is ([left], [top]) and how much the launcher scales the widget */
    data class Solution(
        val left: Float,
        val top: Float,
        val scale: Float,
        /** The scale comes from these taps, not from an earlier measure */
        val measured: Boolean,
    )

    /**
     * The card that explains the [taps] best: each view is at the corner of
     * the card plus its place in the layout times the scale. With taps on
     * views far enough apart the scale is measured, otherwise it is [prior]
     * (the last one measured, 1 if never).
     */
    fun solve(taps: List<WidgetTap>, prior: Float): Solution? {
        if (taps.isEmpty()) return null
        val n = taps.size
        val meanX = taps.sumOf { it.x.toDouble() } / n
        val meanY = taps.sumOf { it.y.toDouble() } / n
        val meanOffsetX = taps.sumOf { it.offsetX.toDouble() } / n
        val meanOffsetY = taps.sumOf { it.offsetY.toDouble() } / n
        var apart = 0.0
        var along = 0.0
        taps.forEach {
            val dx = it.offsetX - meanOffsetX
            val dy = it.offsetY - meanOffsetY
            apart += dx * dx + dy * dy
            along += dx * (it.x - meanX) + dy * (it.y - meanY)
        }
        val measured = apart >= MIN_APART * MIN_APART
        val scale = if (measured) (along / apart).toFloat() else prior
        return Solution(
            left = (meanX - scale * meanOffsetX).toFloat(),
            top = (meanY - scale * meanOffsetY).toFloat(),
            scale = scale,
            measured = measured,
        )
    }

    /**
     * The taps to keep after a new [tap]: the earlier ones on other views,
     * if one card explains them all; only the new one when the widget was
     * moved or resized meanwhile. [tolerance] in pixels.
     */
    fun add(taps: List<WidgetTap>, tap: WidgetTap, prior: Float, tolerance: Float): List<WidgetTap> {
        val earlier = taps.filter { it.view != tap.view && it.sameWidget(tap) }
        if (earlier.isEmpty()) return listOf(tap)
        val all = earlier + tap
        val solution = solve(all, prior) ?: return listOf(tap)
        val fits = solution.scale in MIN_SCALE..MAX_SCALE && all.all {
            abs(solution.left + solution.scale * it.offsetX - it.x) <= tolerance &&
                abs(solution.top + solution.scale * it.offsetY - it.y) <= tolerance
        }
        return if (fits) all else listOf(tap)
    }

    /** The card on the screen for a [solution], [cardWidth] x [cardHeight] in the layout */
    fun position(solution: Solution, cardWidth: Int, cardHeight: Int, screenWidth: Int): CardPosition {
        val left = solution.left.roundToInt()
        val top = solution.top.roundToInt()
        return CardPosition(
            left = left,
            top = top,
            right = left + max(1, (cardWidth * solution.scale).roundToInt()),
            bottom = top + max(1, (cardHeight * solution.scale).roundToInt()),
            screenWidth = screenWidth,
            exact = solution.measured,
            scale = solution.scale,
        )
    }

    /** Tolerance of [add] on a screen [screenWidth] pixels wide: a moved widget moves by much more */
    fun tolerance(screenWidth: Int): Float = max(12f, screenWidth * 0.012f)

    private const val CONTROLS_START = 24 + 12
    private const val CONTROLS_SIDES = 24 + 12 + 20 + 16
    private const val PAD_BOTTOM = 8
    private const val BUTTON = 48

    /** Views closer than this in the layout (pixels) do not tell the scale */
    private const val MIN_APART = 80.0

    // A launcher does not scale its widgets beyond these
    private const val MIN_SCALE = 0.5f
    private const val MAX_SCALE = 1.25f
}
