package it.personale.spotifywidget

import it.personale.spotifywidget.auth.loginPage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginPageTest {
    @Test
    fun successLinksBackToTheApp() {
        val page = loginPage("Spotify collegato", null, "Torna all'app", success = true)
        assertTrue(page.contains("""href="spotifywidget://callback?status=ok""""))
        assertTrue(page.contains("<h1>Spotify collegato</h1>"))
    }

    @Test
    fun errorMessagesAreEscaped() {
        val page = loginPage("Errore", "HTTP 400: <script>alert(1)</script>", "Torna", success = false)
        assertFalse(page.contains("<script>"))
        assertTrue(page.contains("&lt;script&gt;"))
        assertTrue(page.contains("status=error"))
    }
}
