package it.personale.spotifywidget

import it.personale.spotifywidget.api.CurrentlyPlaying
import it.personale.spotifywidget.api.Paging
import it.personale.spotifywidget.api.PlayHistory
import it.personale.spotifywidget.api.SpotifyDevices
import it.personale.spotifywidget.api.SpotifyErrorBody
import it.personale.spotifywidget.api.SpotifyImage
import it.personale.spotifywidget.api.SpotifyJson
import it.personale.spotifywidget.api.SpotifyPlaylist
import it.personale.spotifywidget.api.bestFor
import it.personale.spotifywidget.data.toRecentItems
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelsTest {
    private val track = """
        {"uri":"spotify:track:1","name":"Song","artists":[{"name":"A"},{"name":"B"}],
         "album":{"name":"Album","images":[{"url":"big","width":640,"height":640},
         {"url":"medium","width":300,"height":300},{"url":"small","width":64,"height":64}]},
         "popularity":10,"preview_url":null}
    """.trimIndent()

    @Test
    fun currentlyPlaying() {
        val playing = SpotifyJson.decodeFromString<CurrentlyPlaying>(
            """{"is_playing":true,"progress_ms":1,"item":$track,"currently_playing_type":"track"}""",
        )
        assertTrue(playing.isPlaying)
        assertEquals("A, B", playing.item?.artistNames)
        assertEquals("medium", playing.item?.album?.images.bestFor(160))
    }

    @Test
    fun recentlyPlayedIsDedupedAndKeepsTheContext() {
        val page = SpotifyJson.decodeFromString<Paging<PlayHistory>>(
            """{"items":[
              {"track":$track,"played_at":"2026-10-30T10:00:00Z","context":{"type":"playlist","uri":"spotify:playlist:9"}},
              {"track":$track,"played_at":"2026-10-30T09:00:00Z","context":null},
              {"track":{"uri":"spotify:local:x","name":"Local"},"played_at":"2026-10-30T08:00:00Z"}
            ],"next":null}""",
        )
        val recent = page.items.filterNotNull().toRecentItems()
        assertEquals(1, recent.size)
        assertEquals("spotify:playlist:9", recent[0].contextUri)
        assertEquals("medium", recent[0].imageUrl)
    }

    @Test
    fun playlistsWithoutImagesAndNullItems() {
        val page = SpotifyJson.decodeFromString<Paging<SpotifyPlaylist>>(
            """{"items":[{"id":"1","uri":"spotify:playlist:1","name":"Mix","images":null},null],"next":"x"}""",
        )
        assertEquals(1, page.items.filterNotNull().size)
        assertNull(page.items[0]?.images.bestFor(160))
    }

    @Test
    fun devicesAndErrors() {
        val devices = SpotifyJson.decodeFromString<SpotifyDevices>(
            """{"devices":[{"id":"d1","is_active":false,"name":"Pixel","type":"Smartphone","volume_percent":50}]}""",
        )
        assertEquals("d1", devices.devices.single().id)
        val error = SpotifyJson.decodeFromString<SpotifyErrorBody>(
            """{"error":{"status":404,"message":"Player command failed: No active device found","reason":"NO_ACTIVE_DEVICE"}}""",
        )
        assertEquals("NO_ACTIVE_DEVICE", error.error?.reason)
    }

    @Test
    fun bestImageFallsBackToTheLargest() {
        val images = listOf(SpotifyImage("a", 64, 64), SpotifyImage("b", 100, 100))
        assertEquals("b", images.bestFor(160))
        assertNull(emptyList<SpotifyImage>().bestFor(160))
    }
}
