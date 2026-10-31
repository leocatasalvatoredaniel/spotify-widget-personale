package it.personale.spotifywidget.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import it.personale.spotifywidget.R

/**
 * Opened by the button of the page shown at the end of the Spotify login
 * (spotifywidget://callback?status=…): back to the settings
 */
class AuthCallbackActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handle(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent) {
        if (intent.data?.getQueryParameter("status") == "ok") {
            Toast.makeText(this, R.string.login_done, Toast.LENGTH_SHORT).show()
        }
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
        finish()
    }
}
