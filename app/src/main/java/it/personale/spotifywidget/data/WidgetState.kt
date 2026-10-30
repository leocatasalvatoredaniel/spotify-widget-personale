package it.personale.spotifywidget.data

import it.personale.spotifywidget.api.PlayHistory
import it.personale.spotifywidget.api.bestFor
import kotlinx.serialization.Serializable

/** What Spotify is playing, read from the media session of the phone */
@Serializable
data class NowPlaying(
    val title: String,
    val artist: String,
    val album: String = "",
    /** spotify:track:… when known */
    val trackUri: String? = null,
    val artPath: String? = null,
    /** Main color of the cover */
    val color: Int? = null,
    val isPlaying: Boolean = false,
) {
    val key: String get() = "$title|$artist|$album"
}

@Serializable
data class PlaylistItem(
    val uri: String,
    val name: String,
    val imageUrl: String? = null,
    val imagePath: String? = null,
)

@Serializable
data class RecentItem(
    val trackUri: String,
    val title: String,
    val artist: String,
    val contextUri: String? = null,
    val imageUrl: String? = null,
    val imagePath: String? = null,
)

@Serializable
data class WidgetState(
    val nowPlaying: NowPlaying? = null,
    /** Whether the track [likedUri] is in the liked songs, null if unknown */
    val liked: Boolean? = null,
    val likedUri: String? = null,
    val favorites: List<PlaylistItem> = emptyList(),
    val recent: List<RecentItem> = emptyList(),
) {
    /** Liked state of the track playing now, null if unknown */
    val nowPlayingLiked: Boolean?
        get() = liked.takeIf { nowPlaying?.trackUri != null && nowPlaying.trackUri == likedUri }
}

const val MAX_FAVORITES = 4
const val MAX_RECENT = 8
// Covers are downloaded at this size (px): enough for the widget, light in memory
const val COVER_SIZE_PX = 160

/**
 * Recently played tracks for the widget: each track once, newest first
 */
fun List<PlayHistory>.toRecentItems(limit: Int = MAX_RECENT): List<RecentItem> =
    distinctBy { it.track.uri }
        .filter { it.track.uri.startsWith("spotify:track:") }
        .take(limit)
        .map {
            RecentItem(
                trackUri = it.track.uri,
                title = it.track.name,
                artist = it.track.artistNames,
                contextUri = it.context?.uri,
                imageUrl = it.track.album.images.bestFor(COVER_SIZE_PX),
            )
        }
