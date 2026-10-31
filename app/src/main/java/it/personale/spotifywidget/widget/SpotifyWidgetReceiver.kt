package it.personale.spotifywidget.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.widget.Toast
import it.personale.spotifywidget.PressedAction
import it.personale.spotifywidget.R
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
            WidgetRenderer.ACTION_CUSTOM -> {
                val pressed = Playback.customAction(context, intent.getStringExtra(WidgetRenderer.EXTRA_CUSTOM_ACTION))
                if (pressed != null) {
                    context.app.pressedAction = PressedAction(
                        action = pressed.action,
                        name = pressed.name?.toString().orEmpty(),
                        icon = pressed.icon,
                        at = SystemClock.elapsedRealtime(),
                    )
                }
            }
            ACTION_PINNED -> {
                Toast.makeText(context, R.string.widget_pinned, Toast.LENGTH_SHORT).show()
                renderAsync(context, null)
            }
            else -> super.onReceive(context, intent)
        }
    }

    companion object {
        /** The launcher added the widget asked by the app */
        const val ACTION_PINNED = "it.personale.spotifywidget.PINNED"
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
