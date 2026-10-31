package it.personale.spotifywidget.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import it.personale.spotifywidget.app
import it.personale.spotifywidget.playback.Playback
import kotlinx.coroutines.launch

/**
 * The widget on the home screen: draws it when it is added or resized, and
 * receives its buttons. The media session then redraws it by itself.
 */
class SpotifyWidgetReceiver : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        renderAsync(context, ids)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        id: Int,
        options: Bundle,
    ) {
        renderAsync(context, intArrayOf(id))
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            // Spotify closed meanwhile: show the play button that opens it
            WidgetRenderer.ACTION_PLAY_PAUSE -> if (!Playback.playPause(context)) renderAsync(context, null)
            WidgetRenderer.ACTION_PREVIOUS -> Playback.previous(context)
            WidgetRenderer.ACTION_NEXT -> Playback.next(context)
            WidgetRenderer.ACTION_CUSTOM ->
                Playback.customAction(context, intent.getStringExtra(WidgetRenderer.EXTRA_CUSTOM_ACTION))
            else -> super.onReceive(context, intent)
        }
    }

    private fun renderAsync(context: Context, ids: IntArray?) {
        val pending = goAsync()
        context.app.scope.launch {
            try {
                WidgetRenderer.render(context, ids)
            } finally {
                pending.finish()
            }
        }
    }
}
