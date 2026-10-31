package it.personale.spotifywidget.widget

/**
 * How the player fits a widget of any size: which parts it shows and how
 * much everything is scaled, so that buttons and text grow and shrink with
 * the widget as the media player of the system does
 */
data class PlayerMetrics(
    val scale: Float,
    /** Width of the progress bar, null when the size of the widget is unknown */
    val progressWidthDp: Float?,
    /** Row with the output device */
    val showOutput: Boolean,
    /** Name of the device next to the output button */
    val showDeviceName: Boolean,
    val showTimes: Boolean,
    /** Spotify's buttons (shuffle, +) at the sides of the controls */
    val showActions: Boolean,
) {
    companion object {
        // Sizes (dp) of the player at scale 1, as the media player of One UI
        private const val PAD_TOP = 14
        private const val PAD_BOTTOM = 8
        private const val OUTPUT_ROW = 24 + 4
        private const val TITLE_ROW = 22
        private const val MIN_GAP = 6
        // The track is 4.5 dp and the thumb on it 16 dp; its glow may overflow
        private const val PROGRESS = 16
        private const val TIMES_ROW = 16
        private const val CONTROLS = 48
        private const val SIDE_PADDING = 24 + 20 + 12 + 16
        private const val BUTTON = 48

        // Margins around the progress bar at scale 1: padding of the player and end margin
        private const val PROGRESS_MARGINS = 24 + 20 + 4

        const val MIN_SCALE = 0.5f
        const val MAX_SCALE = 1.6f

        val DEFAULT = PlayerMetrics(
            scale = 1f,
            progressWidthDp = null,
            showOutput = true,
            showDeviceName = true,
            showTimes = true,
            showActions = true,
        )

        fun forSize(widthDp: Int, heightDp: Int): PlayerMetrics {
            if (widthDp <= 0 || heightDp <= 0) return DEFAULT
            // Narrow widgets keep previous, play and next, as the small player of the quick panel
            val showActions = widthDp >= 280
            val buttons = if (showActions) 5 else 3
            val widthScale = widthDp.toFloat() / (SIDE_PADDING + buttons * BUTTON)

            // The richest layout that fits the height without shrinking below 90%,
            // as the player of the notification shade keeps every row
            val layouts = if (showActions) {
                listOf(true to true, false to true, false to false)
            } else {
                listOf(true to false, false to false)
            }
            val (showOutput, showTimes) = layouts.firstOrNull { (output, times) ->
                heightDp.toFloat() / height(output, times) >= minOf(0.9f, widthScale)
            } ?: layouts.last()

            val scale = minOf(widthScale, heightDp.toFloat() / height(showOutput, showTimes))
                .coerceIn(MIN_SCALE, MAX_SCALE)
            return PlayerMetrics(
                scale = scale,
                progressWidthDp = widthDp - PROGRESS_MARGINS * scale,
                showOutput = showOutput,
                showDeviceName = showActions,
                showTimes = showTimes,
                showActions = showActions,
            )
        }

        private fun height(output: Boolean, times: Boolean): Int =
            PAD_TOP + (if (output) OUTPUT_ROW else 0) + TITLE_ROW + MIN_GAP + PROGRESS +
                (if (times) TIMES_ROW else 0) + CONTROLS + PAD_BOTTOM
    }
}
