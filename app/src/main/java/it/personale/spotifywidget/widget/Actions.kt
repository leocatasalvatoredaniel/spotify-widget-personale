package it.personale.spotifywidget.widget

import android.content.Context
import android.widget.Toast
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import it.personale.spotifywidget.R
import it.personale.spotifywidget.app
import it.personale.spotifywidget.playback.Playback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Buttons of the widget. The media session updates the widget by itself:
// the state is also changed here so that the button reacts at once.

class PlayPauseAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        if (!Playback.playPause(context)) return
        context.app.store.update { state ->
            state.copy(nowPlaying = state.nowPlaying?.let { it.copy(isPlaying = !it.isPlaying) })
        }
        updateWidgets(context)
    }
}

class NextAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        Playback.next(context)
    }
}

class PreviousAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        Playback.previous(context)
    }
}

class LikeAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val app = context.app
        val result = runCatching {
            val nowPlaying = app.store.read().nowPlaying ?: return
            val uri = nowPlaying.trackUri
                ?: app.api.currentlyPlaying()?.item?.takeIf { it.name == nowPlaying.title }?.uri
                ?: return
            val liked = !app.api.isSaved(uri)
            if (liked) app.api.save(uri) else app.api.remove(uri)
            app.store.update { state ->
                state.copy(
                    nowPlaying = state.nowPlaying?.copy(trackUri = uri),
                    liked = liked,
                    likedUri = uri,
                )
            }
            updateWidgets(context)
        }
        if (result.isFailure) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, R.string.like_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
