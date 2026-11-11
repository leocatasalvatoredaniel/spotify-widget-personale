package it.personale.spotifywidget.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.abs
import kotlin.math.roundToInt

/** JSON of the saved state: fields of older versions are ignored */
val StateJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
}

/**
 * A button that Spotify adds to its media controls, e.g. shuffle or add to
 * the library: the widget shows it with the icon chosen by Spotify
 */
@Serializable
data class SessionAction(
    val action: String,
    val name: String,
    /** Drawable of the Spotify app */
    val icon: Int,
)

/** What Spotify is playing, read from its media session */
@Serializable
data class NowPlaying(
    val title: String,
    val artist: String,
    val album: String = "",
    val artPath: String? = null,
    /** Color of the progress bar, from the cover */
    val accent: Int? = null,
    /** Rule that computed [accent]: older colors are computed again */
    val accentRule: Int = 0,
    val isPlaying: Boolean = false,
    val durationMs: Long = 0,
    /** Position read at [positionAt] (System.currentTimeMillis) */
    val positionMs: Long = 0,
    val positionAt: Long = 0,
    /** Buttons of Spotify, in its order */
    val actions: List<SessionAction> = emptyList(),
    /** Playing on another device with Spotify Connect */
    val remote: Boolean = false,
) {
    val key: String get() = "$title|$artist|$album"

    /** Position at the time [now], within the track */
    fun positionAt(now: Long): Long {
        val elapsed = if (isPlaying) (now - positionAt).coerceAtLeast(0) else 0
        val position = (positionMs + elapsed).coerceAtLeast(0)
        return if (durationMs > 0) position.coerceAtMost(durationMs) else position
    }

    /** Spotify closed: the track stays in the widget, paused where it was */
    fun stopped(now: Long): NowPlaying = copy(
        isPlaying = false,
        positionMs = positionAt(now),
        positionAt = now,
        actions = emptyList(),
        remote = false,
    )
}

/**
 * Background of the widget: the cover, as the media player of the system,
 * or a veil as the widgets of Samsung (opacity and colors as their settings)
 */
@Serializable
data class WidgetBackground(
    val cover: Boolean = true,
    /** 0, 50 or 100, as the slider of the Samsung widgets */
    val opacity: Int = 50,
    val dark: Boolean = true,
    /** Blur the wallpaper behind the widget, as Samsung: needs the access to all files */
    val blur: Boolean = false,
) {
    companion object {
        val OPACITIES = listOf(0, 50, 100)
    }
}

@Serializable
data class WidgetState(
    val nowPlaying: NowPlaying? = null,
    val background: WidgetBackground = WidgetBackground(),
    /**
     * Where each widget was on the screen, by widget id, as earlier versions
     * kept it: used until the widget is tapped again
     */
    val positions: Map<Int, CardPosition> = emptyMap(),
    /** The taps that tell where each widget is on the screen, by widget id */
    val taps: Map<Int, List<WidgetTap>> = emptyMap(),
    /** How much the launcher scales the widgets, as last measured from the taps */
    val launcherScale: Float? = null,
)

/**
 * A tap on a view of a widget: where the view is on the screen and where it
 * is in the card (see CardLocator)
 */
@Serializable
data class WidgetTap(
    /** The view tapped (name of a CardLocator.Tapped) */
    val view: String,
    /** Top left corner of the view on the screen, in pixels */
    val x: Int,
    val y: Int,
    /** Top left corner of the view in the card, in pixels of the layout */
    val offsetX: Float,
    val offsetY: Float,
    /** Size of the card in the layout when it was tapped */
    val cardWidth: Int,
    val cardHeight: Int,
    /** Width of the screen when it was tapped: the resolution can change */
    val screenWidth: Int,
) {
    /** Tapped on a widget of the same size on the same screen */
    fun sameWidget(other: WidgetTap): Boolean = sameWidget(other.cardWidth, other.cardHeight, other.screenWidth)

    fun sameWidget(cardWidth: Int, cardHeight: Int, screenWidth: Int): Boolean =
        abs(this.cardWidth - cardWidth) <= 2 && abs(this.cardHeight - cardHeight) <= 2 && this.screenWidth == screenWidth
}

/**
 * Card of a widget on the screen, in pixels of the screen in portrait.
 * Android does not tell a widget where it is: a tap does.
 */
@Serializable
data class CardPosition(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    /** Width of the screen when it was measured: the resolution can change */
    val screenWidth: Int,
    /** The scale of the launcher was measured; in earlier versions, bounds of the card itself */
    val exact: Boolean,
    /** How much the launcher scales the widget: the card is this much smaller than its layout */
    val scale: Float = 1f,
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top

    /** The same card on a screen [width] pixels wide */
    fun onScreen(width: Int): CardPosition {
        if (width == screenWidth || screenWidth <= 0) return this
        val scale = width.toFloat() / screenWidth
        fun scaled(value: Int) = (value * scale).roundToInt()
        return CardPosition(scaled(left), scaled(top), scaled(right), scaled(bottom), width, exact, this.scale)
    }
}

/** Time as in the system player: 00:49, 03:09, 1:02:03 */
fun formatTime(millis: Long): String {
    val seconds = millis.coerceAtLeast(0) / 1000
    val hours = seconds / 3600
    val minutes = seconds / 60 % 60
    val rest = seconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, rest)
    else "%02d:%02d".format(minutes, rest)
}

/** Current rule of the color of the progress bar, as One UI picks it */
const val ACCENT_RULE = 3

/** Steps of the bar: finer than its pixels, so that it can move one pixel at a time */
const val PROGRESS_MAX = 10_000

/** Progress of the bar, from 0 to [PROGRESS_MAX] */
fun progressOf(position: Long, duration: Long): Int =
    if (duration <= 0) 0
    else (position.coerceIn(0, duration) * PROGRESS_MAX / duration).toInt()

/** Width (dp) of the thumb at the end of the progress */
const val THUMB_DP = 16f

/**
 * Progress for a bar of [barWidthDp] whose thumb ends the played part: it
 * starts one thumb in, so that the thumb never leaves the track
 */
fun progressWithThumb(position: Long, duration: Long, barWidthDp: Float?): Int {
    if (duration <= 0) return 0
    val fraction = position.coerceIn(0, duration).toFloat() / duration
    val thumb = if (barWidthDp != null && barWidthDp > THUMB_DP) THUMB_DP / barWidthDp else DEFAULT_THUMB_FRACTION
    return ((thumb + fraction * (1 - thumb)) * PROGRESS_MAX).toInt()
}

// The thumb on the bar of a 4×2 widget
private const val DEFAULT_THUMB_FRACTION = 0.05f

/**
 * Time (ms) for the thumb to move one pixel on a bar [barWidthPx] wide with
 * a thumb [thumbPx] wide: the bar is drawn again as often, so that it moves
 * smoothly as the one of the system player instead of a step a second.
 * At most 10 times a second, at least once.
 */
fun progressStepMs(durationMs: Long, barWidthPx: Float, thumbPx: Float): Long {
    val travel = barWidthPx - thumbPx
    if (durationMs <= 0 || travel <= 0) return MAX_STEP_MS
    return (durationMs / travel).toLong().coerceIn(MIN_STEP_MS, MAX_STEP_MS)
}

private const val MIN_STEP_MS = 100L
private const val MAX_STEP_MS = 1000L
