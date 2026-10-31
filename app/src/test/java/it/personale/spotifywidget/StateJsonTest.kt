package it.personale.spotifywidget

import it.personale.spotifywidget.data.StateJson
import it.personale.spotifywidget.data.WidgetState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class StateJsonTest {
    @Test
    fun stateOfTheFirstVersionIsStillRead() {
        // Saved by the version with the Web API: playlists, recent tracks, liked state
        val json = """
            {"nowPlaying":{"title":"Prayer in C","artist":"Robin Schulz","album":"",
             "trackUri":"spotify:track:1","artPath":"/data/covers/now-1.jpg","color":-16777216,
             "isPlaying":true},
             "liked":true,"likedUri":"spotify:track:1",
             "favorites":[{"uri":"spotify:playlist:1","name":"Loquito"}],
             "recent":[{"trackUri":"spotify:track:2","title":"CHAPA","artist":"X"}]}
        """.trimIndent()
        val state = StateJson.decodeFromString(WidgetState.serializer(), json)
        val nowPlaying = state.nowPlaying!!
        assertEquals("Prayer in C", nowPlaying.title)
        assertEquals("/data/covers/now-1.jpg", nowPlaying.artPath)
        assertEquals(0, nowPlaying.durationMs)
        assertFalse(nowPlaying.actions.any())
    }
}
