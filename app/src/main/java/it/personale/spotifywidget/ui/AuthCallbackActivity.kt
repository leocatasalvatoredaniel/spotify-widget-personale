package it.personale.spotifywidget.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import it.personale.spotifywidget.R
import it.personale.spotifywidget.app
import it.personale.spotifywidget.widget.RefreshWorker
import kotlinx.coroutines.launch

/**
 * Receives spotifywidget://callback at the end of the login in the browser
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
        val uri = intent.data ?: return finish()
        lifecycleScope.launch {
            val result = runCatching { app.auth.completeLogin(uri) }
            val message = result.fold(
                onSuccess = { getString(R.string.login_done) },
                onFailure = { getString(R.string.login_failed, it.message) },
            )
            Toast.makeText(this@AuthCallbackActivity, message, Toast.LENGTH_LONG).show()
            if (result.isSuccess) RefreshWorker.refreshSoon(this@AuthCallbackActivity, 0)
            startActivity(
                Intent(this@AuthCallbackActivity, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            )
            finish()
        }
    }
}
