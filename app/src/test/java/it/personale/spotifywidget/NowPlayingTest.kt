package it.personale.spotifywidget

import it.personale.spotifywidget.data.NowPlaying
import it.personale.spotifywidget.data.SessionAction
import it.personale.spotifywidget.data.formatTime
import it.personale.spotifywidget.data.progressOf
import it.personale.spotifywidget.data.progressStepMs
import it.personale.spotifywidget.data.progressWithThumb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NowPlayingTest {
    private val track = NowPlaying(
        title = "Prayer in C",
        artist = "Lilly Wood and The Prick",
        durationMs = 189_000,
        positionMs = 49_000,
        positionAt = 1_000_000,
        isPlaying = true,
        actions = listOf(SessionAction("shuffle", "Shuffle", 1)),
    )

    @Test
    fun positionMovesWhilePlaying() {
        assertEquals(53_000, track.positionAt(1_004_000))
    }

    @Test
    fun positionStaysWhenPaused() {
        assertEquals(49_000, track.copy(isPlaying = false).positionAt(1_004_000))
    }

    @Test
    fun positionStopsAtTheEndOfTheTrack() {
        assertEquals(189_000, track.positionAt(2_000_000))
    }

    @Test
    fun positionIsNeverBeforeTheStart() {
        assertEquals(49_000, track.positionAt(900_000))
    }

    @Test
    fun closingSpotifyKeepsTheTrackPaused() {
        val stopped = track.stopped(1_010_000)
        assertFalse(stopped.isPlaying)
        assertEquals(59_000, stopped.positionMs)
        assertEquals(59_000, stopped.positionAt(1_100_000))
        assertTrue(stopped.actions.isEmpty())
    }

    @Test
    fun timesAreWrittenAsTheSystemPlayer() {
        assertEquals("00:00", formatTime(0))
        assertEquals("00:49", formatTime(49_999))
        assertEquals("03:09", formatTime(189_000))
        assertEquals("1:02:03", formatTime(3_723_000))
        assertEquals("00:00", formatTime(-5))
    }

    @Test
    fun progressOfTheBar() {
        assertEquals(2592, progressOf(49_000, 189_000))
        assertEquals(10_000, progressOf(200_000, 189_000))
        assertEquals(0, progressOf(5_000, 0))
    }

    @Test
    fun thumbStaysOnTheTrack() {
        // 320 dp bar, 16 dp thumb: the thumb starts one thumb in and ends at the end
        assertEquals(500, progressWithThumb(0, 189_000, 320f))
        assertEquals(10_000, progressWithThumb(189_000, 189_000, 320f))
        assertEquals(5250, progressWithThumb(94_500, 189_000, 320f))
        assertEquals(0, progressWithThumb(5_000, 0, 320f))
    }

    @Test
    fun theBarMovesAPixelAtATime() {
        // 3:24 on the bar of the widget on the phone (815 px, thumb 45 px): about 4 times a second
        assertEquals(264, progressStepMs(204_000, 815f, 45f))
        // A short track does not go beyond 10 times a second, a long one not below once
        assertEquals(100, progressStepMs(30_000, 815f, 45f))
        assertEquals(1000, progressStepMs(3_600_000, 815f, 45f))
        assertEquals(1000, progressStepMs(0, 815f, 45f))
    }
}
