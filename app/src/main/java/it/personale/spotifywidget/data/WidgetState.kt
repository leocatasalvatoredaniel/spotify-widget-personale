package it.personale.spotifywidget.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

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

@Serializable
data class WidgetState(
    val nowPlaying: NowPlaying? = null,
    /** Notification icon of Spotify, a drawable of the Spotify app */
    val appIcon: Int? = null,
)

/** Time as in the system player: 00:49, 03:09, 1:02:03 */
fun formatTime(millis: Long): String {
    val seconds = millis.coerceAtLeast(0) / 1000
    val hours = seconds / 3600
    val minutes = seconds / 60 % 60
    val rest = seconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, rest)
    else "%02d:%02d".format(minutes, rest)
}

const val PROGRESS_MAX = 1000

/** Progress of the bar, from 0 to [PROGRESS_MAX] */
fun progressOf(position: Long, duration: Long): Int =
    if (duration <= 0) 0
    else (position.coerceIn(0, duration) * PROGRESS_MAX / duration).toInt()
