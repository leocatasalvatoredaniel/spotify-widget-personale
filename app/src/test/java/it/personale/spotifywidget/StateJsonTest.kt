package it.personale.spotifywidget

import it.personale.spotifywidget.data.CardPosition
import it.personale.spotifywidget.data.StateJson
import it.personale.spotifywidget.data.WidgetState
import it.personale.spotifywidget.data.WidgetTap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun widgetsOfEarlierVersionsKeepTheCover() {
        val state = StateJson.decodeFromString(WidgetState.serializer(), """{"nowPlaying":null}""")
        assertTrue(state.background.cover)
        assertEquals(50, state.background.opacity)
        assertTrue(state.background.dark)
    }

    @Test
    fun tapsOnTheWidgetsAreKept() {
        val tap = WidgetTap("PLAY", 471, 540, 483f, 353f, 1136, 548, 1080)
        val state = WidgetState(taps = mapOf(42 to listOf(tap)), launcherScale = 0.838f)
        val json = StateJson.encodeToString(WidgetState.serializer(), state)
        assertEquals(state, StateJson.decodeFromString(WidgetState.serializer(), json))
    }

    @Test
    fun positionsOfEarlierVersionsAreRead() {
        // Before the scale of the launcher was measured: the card as its tap told it
        val json = """{"positions":{"42":{"left":67,"top":245,"right":1203,"bottom":793,"screenWidth":1080,"exact":true}}}"""
        val state = StateJson.decodeFromString(WidgetState.serializer(), json)
        assertEquals(CardPosition(67, 245, 1203, 793, 1080, exact = true, scale = 1f), state.positions[42])
        assertTrue(state.taps.isEmpty())
        assertEquals(null, state.launcherScale)
        // Saved before the blur: no blur, no positions
        val old = StateJson.decodeFromString(WidgetState.serializer(), """{"background":{"cover":false}}""")
        assertFalse(old.background.blur)
        assertTrue(old.positions.isEmpty())
    }
}
