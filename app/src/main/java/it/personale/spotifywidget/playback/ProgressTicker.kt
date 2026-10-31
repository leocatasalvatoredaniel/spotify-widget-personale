package it.personale.spotifywidget.playback

import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState
import it.personale.spotifywidget.WidgetApp
import it.personale.spotifywidget.widget.WidgetRenderer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Moves the progress bar and the time once a second while Spotify plays and
 * the screen is on. Only these two values are sent to the launcher, not the
 * whole widget.
 */
class ProgressTicker(private val app: WidgetApp) {
    private var job: Job? = null

    fun restart(controller: MediaController?, screenOn: Boolean) {
        job?.cancel()
        if (controller == null || !screenOn) return
        job = app.scope.launch {
            while (isActive) {
                val state = controller.playbackState ?: break
                if (state.state != PlaybackState.STATE_PLAYING) break
                val duration = controller.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0
                if (duration <= 0) break
                val position = Playback.positionNow(state).coerceIn(0, duration)
                WidgetRenderer.renderProgress(app, position, duration)
                // Just after the next whole second, as the time on screen changes
                delay(SECOND - position % SECOND + 20)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private companion object {
        const val SECOND = 1000L
    }
}
