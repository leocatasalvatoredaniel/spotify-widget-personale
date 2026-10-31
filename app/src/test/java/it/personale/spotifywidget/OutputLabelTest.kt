package it.personale.spotifywidget

import android.media.AudioDeviceInfo
import it.personale.spotifywidget.playback.outputLabel
import org.junit.Assert.assertEquals
import org.junit.Test

class OutputLabelTest {
    private fun label(type: Int?, name: String? = null) =
        outputLabel(type, name, thisPhone = "Questo smartphone", wired = "Cuffie con cavo")

    @Test
    fun speakerOfThePhone() {
        assertEquals("Questo smartphone", label(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, "SM-S921B"))
        assertEquals("Questo smartphone", label(null))
    }

    @Test
    fun bluetoothShowsItsName() {
        assertEquals("Galaxy Buds3 Pro", label(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, "Galaxy Buds3 Pro"))
        assertEquals("Questo smartphone", label(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, " "))
    }

    @Test
    fun wiredEarphonesDoNotShowThePhoneName() {
        assertEquals("Cuffie con cavo", label(AudioDeviceInfo.TYPE_WIRED_HEADPHONES, "SM-S921B"))
    }
}
