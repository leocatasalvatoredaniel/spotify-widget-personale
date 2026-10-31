package it.personale.spotifywidget.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.widget.Toast
import it.personale.spotifywidget.R
import it.personale.spotifywidget.app
import it.personale.spotifywidget.playback.Playback

/**
 * Invisible screen opened by play while Spotify is closed: it opens Spotify,
 * and the media listener starts the music as soon as Spotify is ready. An
 * activity, because only a tap may open Spotify from the background.
 */
class LaunchSpotifyActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val launch = Playback.spotifyLaunchIntent(this)
        if (launch == null) {
            Toast.makeText(this, R.string.spotify_missing, Toast.LENGTH_SHORT).show()
        } else {
            app.playRequestedAt = SystemClock.elapsedRealtime()
            startActivity(launch)
        }
        finish()
    }

    companion object {
        fun intent(context: Context): Intent =
            Intent(context, LaunchSpotifyActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
