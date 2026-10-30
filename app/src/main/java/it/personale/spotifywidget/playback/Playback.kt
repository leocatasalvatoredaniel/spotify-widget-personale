package it.personale.spotifywidget.playback

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import androidx.core.app.NotificationManagerCompat

/**
 * Spotify controlled through its media session, as the controls in the
 * notification shade: it works offline and without the Web API
 */
object Playback {
    const val SPOTIFY_PACKAGE = "com.spotify.music"

    fun hasAccess(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    fun listenerComponent(context: Context) = ComponentName(context, MediaListenerService::class.java)

    /** The media session of Spotify, null if Spotify is closed or access is missing */
    fun spotifyController(context: Context): MediaController? = runCatching {
        context.getSystemService(MediaSessionManager::class.java)
            .getActiveSessions(listenerComponent(context))
            .firstOrNull { it.packageName == SPOTIFY_PACKAGE }
    }.getOrNull()

    fun isPlaying(controller: MediaController): Boolean =
        controller.playbackState?.state.let {
            it == PlaybackState.STATE_PLAYING || it == PlaybackState.STATE_BUFFERING
        }

    /** False if there is no Spotify session to control */
    fun playPause(context: Context): Boolean {
        val controller = spotifyController(context) ?: return false
        if (isPlaying(controller)) controller.transportControls.pause()
        else controller.transportControls.play()
        return true
    }

    fun next(context: Context): Boolean =
        spotifyController(context)?.transportControls?.skipToNext() != null

    fun previous(context: Context): Boolean =
        spotifyController(context)?.transportControls?.skipToPrevious() != null

    /** Opens Spotify, or null if it is not installed */
    fun spotifyLaunchIntent(context: Context): Intent? =
        context.packageManager.getLaunchIntentForPackage(SPOTIFY_PACKAGE)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
