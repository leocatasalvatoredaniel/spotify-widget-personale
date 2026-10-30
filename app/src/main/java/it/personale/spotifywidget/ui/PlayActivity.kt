package it.personale.spotifywidget.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import it.personale.spotifywidget.R
import it.personale.spotifywidget.api.NotLoggedInException
import it.personale.spotifywidget.api.SpotifyApiException
import it.personale.spotifywidget.api.pickPlaybackDevice
import it.personale.spotifywidget.api.playRequest
import it.personale.spotifywidget.app
import it.personale.spotifywidget.playback.Playback
import it.personale.spotifywidget.playback.PlayWhenReadyWorker
import kotlinx.coroutines.launch

/**
 * Invisible screen opened by a playlist or a recent track of the widget: it
 * starts the playback on this phone, opening Spotify first if it is closed.
 * An activity, because only a tap may open Spotify from the background.
 */
class PlayActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val contextUri = intent.getStringExtra(EXTRA_CONTEXT)
        val trackUri = intent.getStringExtra(EXTRA_TRACK)
        if (contextUri == null && trackUri == null) {
            finish()
            return
        }
        lifecycleScope.launch {
            start(contextUri, trackUri)
            finish()
        }
    }

    private suspend fun start(contextUri: String?, trackUri: String?) {
        val api = app.api
        val request = playRequest(contextUri, trackUri)
        try {
            try {
                api.play(request)
            } catch (error: SpotifyApiException) {
                if (!error.isNoActiveDevice) throw error
                // No active device: use this phone if Spotify is open on it
                val device = api.devices().pickPlaybackDevice()
                if (device?.id != null) {
                    api.play(request, device.id)
                } else {
                    openSpotifyAndPlay(contextUri, trackUri)
                }
            }
        } catch (error: NotLoggedInException) {
            toast(getString(R.string.play_login_required))
            startActivity(Intent(this, MainActivity::class.java))
        } catch (error: SpotifyApiException) {
            toast(
                if (error.isPremiumRequired) getString(R.string.play_premium_required)
                else getString(R.string.play_failed, error.message),
            )
        } catch (error: Exception) {
            toast(getString(R.string.play_failed, error.message ?: error.javaClass.simpleName))
        }
    }

    /** Spotify closed: open it, then start the playback when it is ready */
    private fun openSpotifyAndPlay(contextUri: String?, trackUri: String?) {
        val launch = Playback.spotifyLaunchIntent(this)
        if (launch == null) {
            toast(getString(R.string.play_failed, "Spotify"))
            return
        }
        startActivity(launch)
        toast(getString(R.string.play_starting))
        WorkManager.getInstance(this).enqueueUniqueWork(
            PlayWhenReadyWorker.NAME,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<PlayWhenReadyWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setInputData(
                    Data.Builder()
                        .putString(EXTRA_CONTEXT, contextUri)
                        .putString(EXTRA_TRACK, trackUri)
                        .build(),
                )
                .build(),
        )
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

    companion object {
        const val EXTRA_CONTEXT = "context_uri"
        const val EXTRA_TRACK = "track_uri"

        fun intent(context: Context, contextUri: String?, trackUri: String?): Intent =
            Intent(context, PlayActivity::class.java)
                // A different data for each item, or Android reuses one pending intent
                .setData("spotifywidget://play/${Uri.encode(trackUri ?: contextUri)}".toUri())
                .putExtra(EXTRA_CONTEXT, contextUri)
                .putExtra(EXTRA_TRACK, trackUri)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
