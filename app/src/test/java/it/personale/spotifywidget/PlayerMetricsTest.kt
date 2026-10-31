package it.personale.spotifywidget

import it.personale.spotifywidget.widget.PlayerMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerMetricsTest {
    @Test
    fun fourByTwoShowsEverythingABitLarger() {
        val metrics = PlayerMetrics.forSize(360, 181)
        assertTrue(metrics.showOutput && metrics.showTimes && metrics.showActions)
        assertEquals(1.15f, metrics.scale, 0.02f)
    }

    @Test
    fun sizeOfTheNotificationPlayerKeepsEveryRow() {
        val metrics = PlayerMetrics.forSize(357, 150)
        assertTrue(metrics.showOutput && metrics.showTimes && metrics.showActions)
        assertEquals(0.95f, metrics.scale, 0.02f)
    }

    @Test
    fun lowWidgetsDropTheDeviceAndTheTimesBeforeShrinking() {
        val metrics = PlayerMetrics.forSize(360, 110)
        assertFalse(metrics.showOutput)
        assertFalse(metrics.showTimes)
        assertTrue(metrics.showActions)
        assertEquals(1.02f, metrics.scale, 0.02f)
    }

    @Test
    fun narrowWidgetsKeepPreviousPlayNext() {
        val metrics = PlayerMetrics.forSize(180, 180)
        assertFalse(metrics.showActions)
        assertFalse(metrics.showDeviceName)
        assertTrue(metrics.showOutput)
        assertFalse(metrics.showTimes)
        assertEquals(0.83f, metrics.scale, 0.02f)
    }

    @Test
    fun tallWidgetsDoNotGrowBeyondTheirWidth() {
        // 72 dp of margins and five buttons of 48 dp
        assertEquals(360f / 312f, PlayerMetrics.forSize(360, 400).scale, 0.001f)
    }

    @Test
    fun scaleStaysWithinLimits() {
        assertEquals(PlayerMetrics.MIN_SCALE, PlayerMetrics.forSize(80, 40).scale, 0.001f)
        assertEquals(PlayerMetrics.MAX_SCALE, PlayerMetrics.forSize(900, 900).scale, 0.001f)
    }

    @Test
    fun unknownSizeUsesTheDesign() {
        assertEquals(PlayerMetrics.DEFAULT, PlayerMetrics.forSize(0, 0))
    }
}
