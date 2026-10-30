package it.personale.spotifywidget.api

/**
 * What to ask Spotify to play for an item of the widget: a playlist, or a
 * track inside the playlist or album it was played from
 */
fun playRequest(contextUri: String?, trackUri: String?): PlayRequest {
    // Spotify accepts a starting track only inside albums and playlists
    val canStartAt = contextUri != null &&
        (contextUri.startsWith("spotify:playlist:") || contextUri.startsWith("spotify:album:"))
    return when {
        contextUri != null && trackUri != null && canStartAt ->
            PlayRequest(contextUri = contextUri, offset = PlayOffset(trackUri))
        trackUri != null -> PlayRequest(uris = listOf(trackUri))
        contextUri != null -> PlayRequest(contextUri = contextUri)
        else -> throw IllegalArgumentException("Nothing to play")
    }
}

/**
 * The phone among the devices of the account, or any device
 */
fun List<SpotifyDevice>.pickPlaybackDevice(): SpotifyDevice? =
    filter { it.id != null }.let { devices ->
        devices.firstOrNull { it.type.equals("Smartphone", ignoreCase = true) }
            ?: devices.firstOrNull { it.isActive }
            ?: devices.firstOrNull()
    }
