package it.personale.spotifywidget

import it.personale.spotifywidget.playback.confirmationFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FeedbackTest {
    @Test
    fun addingAndRemovingSayWhere() {
        assertEquals("Aggiunto a Brani che ti piacciono", confirmationFor("Aggiungi a Brani che ti piacciono"))
        assertEquals("Rimosso da Brani che ti piacciono", confirmationFor("Rimuovi da Brani che ti piacciono"))
        assertEquals("Added to Liked Songs", confirmationFor("Add to Liked Songs"))
    }

    @Test
    fun shuffleAndRepeatStayQuiet() {
        assertNull(confirmationFor("Attiva riproduzione casuale"))
        assertNull(confirmationFor("Smart Shuffle"))
        assertNull(confirmationFor("Ripeti"))
    }

    @Test
    fun otherNamesAreShownAsTheyAre() {
        assertEquals("Mi piace", confirmationFor("Mi piace"))
        assertNull(confirmationFor("  "))
    }
}
