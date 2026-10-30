package it.personale.spotifywidget.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// Only the fields used by the widget: the other ones are ignored

val SpotifyJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
}

@Serializable
data class SpotifyImage(val url: String, val width: Int? = null, val height: Int? = null)

@Serializable
data class SpotifyArtist(val name: String = "")

@Serializable
data class SpotifyAlbum(val name: String = "", val images: List<SpotifyImage> = emptyList())

@Serializable
data class SpotifyTrack(
    val uri: String,
    val name: String = "",
    val artists: List<SpotifyArtist> = emptyList(),
    val album: SpotifyAlbum = SpotifyAlbum(),
) {
    val artistNames: String get() = artists.joinToString(", ") { it.name }
}

@Serializable
data class CurrentlyPlaying(
    @SerialName("is_playing") val isPlaying: Boolean = false,
    val item: SpotifyTrack? = null,
)

@Serializable
data class SpotifyDevice(
    val id: String? = null,
    @SerialName("is_active") val isActive: Boolean = false,
    val name: String = "",
    val type: String = "",
)

@Serializable
data class SpotifyDevices(val devices: List<SpotifyDevice> = emptyList())

@Serializable
data class PlayContext(val uri: String? = null, val type: String? = null)

@Serializable
data class PlayHistory(
    val track: SpotifyTrack,
    @SerialName("played_at") val playedAt: String = "",
    val context: PlayContext? = null,
)

@Serializable
data class SpotifyPlaylist(
    val id: String,
    val uri: String,
    val name: String = "",
    val images: List<SpotifyImage>? = null,
)

@Serializable
data class Paging<T>(val items: List<T?> = emptyList(), val next: String? = null)

@Serializable
data class SpotifyUser(
    val id: String,
    @SerialName("display_name") val displayName: String? = null,
    val product: String? = null,
)

@Serializable
data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("expires_in") val expiresIn: Long = 3600,
    @SerialName("refresh_token") val refreshToken: String? = null,
)

@Serializable
data class PlayOffset(val uri: String)

@Serializable
data class PlayRequest(
    @SerialName("context_uri") val contextUri: String? = null,
    val uris: List<String>? = null,
    val offset: PlayOffset? = null,
)

@Serializable
data class SpotifyErrorBody(val error: SpotifyError? = null)

@Serializable
data class SpotifyError(val status: Int = 0, val message: String = "", val reason: String? = null)

/**
 * The smallest image that is still sharp at the given size in pixels
 */
fun List<SpotifyImage>?.bestFor(sizePx: Int): String? {
    val images = this.orEmpty()
    return images
        .filter { (it.width ?: 0) >= sizePx }
        .minByOrNull { it.width ?: Int.MAX_VALUE }
        ?.url
        ?: images.maxByOrNull { it.width ?: 0 }?.url
}
