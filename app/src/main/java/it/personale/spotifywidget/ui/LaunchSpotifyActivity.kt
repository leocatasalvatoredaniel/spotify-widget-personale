package it.personale.spotifywidget.ui

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.widget.Toast
import it.personale.spotifywidget.R
import it.personale.spotifywidget.app
import it.personale.spotifywidget.playback.Playback
import it.personale.spotifywidget.widget.CardLocator
import it.personale.spotifywidget.widget.WidgetRenderer
import kotlinx.coroutines.launch

/**
 * Invisible screen opened by the widget: it opens Spotify, and with play
 * pressed while Spotify is closed the media listener starts the music as soon
 * as Spotify is ready. An activity, because only a tap may open Spotify from
 * the background. The tap also tells where the widget is on the screen.
 */
class LaunchSpotifyActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        recordTap()
        val launch = Playback.spotifyLaunchIntent(this)
        if (launch == null) {
            Toast.makeText(this, R.string.spotify_missing, Toast.LENGTH_SHORT).show()
        } else {
            if (intent.getBooleanExtra(EXTRA_PLAY, false)) app.playRequestedAt = SystemClock.elapsedRealtime()
            startActivity(launch)
        }
        finish()
    }

    private fun recordTap() {
        val bounds = intent.sourceBounds ?: return
        val tapped = intent.getStringExtra(WidgetRenderer.EXTRA_TAPPED)
            ?.let { name -> CardLocator.Tapped.entries.firstOrNull { it.name == name } } ?: return
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val context = applicationContext
        app.scope.launch { WidgetRenderer.recordTap(context, id, tapped, bounds) }
    }

    companion object {
        private const val EXTRA_PLAY = "play"

        /** A tap on the card of the widget [widgetId] */
        fun open(context: Context, widgetId: Int): Intent = intent(context, widgetId, CardLocator.Tapped.CARD)

        /** Play pressed in the widget [widgetId] while Spotify is closed */
        fun play(context: Context, widgetId: Int): Intent =
            intent(context, widgetId, CardLocator.Tapped.PLAY).putExtra(EXTRA_PLAY, true)

        private fun intent(context: Context, widgetId: Int, tapped: CardLocator.Tapped): Intent =
            Intent(context, LaunchSpotifyActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .setData(WidgetRenderer.widgetUri(widgetId))
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                .putExtra(WidgetRenderer.EXTRA_TAPPED, tapped.name)
    }
}
