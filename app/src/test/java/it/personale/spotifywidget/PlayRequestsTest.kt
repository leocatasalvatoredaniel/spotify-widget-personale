package it.personale.spotifywidget

import it.personale.spotifywidget.api.PlayOffset
import it.personale.spotifywidget.api.PlayRequest
import it.personale.spotifywidget.api.SpotifyDevice
import it.personale.spotifywidget.api.SpotifyJson
import it.personale.spotifywidget.api.pickPlaybackDevice
import it.personale.spotifywidget.api.playRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayRequestsTest {
    @Test
    fun trackInsideAPlaylistStartsAtTheTrack() {
        assertEquals(
            PlayRequest(contextUri = "spotify:playlist:1", offset = PlayOffset("spotify:track:2")),
            playRequest("spotify:playlist:1", "spotify:track:2"),
        )
    }

    @Test
    fun trackFromAnArtistIsPlayedAlone() {
        assertEquals(
            PlayRequest(uris = listOf("spotify:track:2")),
            playRequest("spotify:artist:1", "spotify:track:2"),
        )
    }

    @Test
    fun playlistAlone() {
        assertEquals(PlayRequest(contextUri = "spotify:playlist:1"), playRequest("spotify:playlist:1", null))
    }

    @Test
    fun requestJsonHasOnlyTheFieldsSet() {
        assertEquals(
            """{"context_uri":"spotify:playlist:1"}""",
            SpotifyJson.encodeToString(PlayRequest.serializer(), PlayRequest(contextUri = "spotify:playlist:1")),
        )
    }

    @Test
    fun thePhoneIsPreferred() {
        val devices = listOf(
            SpotifyDevice(id = "pc", isActive = true, type = "Computer"),
            SpotifyDevice(id = "phone", type = "Smartphone"),
        )
        assertEquals("phone", devices.pickPlaybackDevice()?.id)
        assertNull(listOf(SpotifyDevice(id = null, type = "Smartphone")).pickPlaybackDevice())
    }
}
