package it.personale.spotifywidget.playback

import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import it.personale.spotifywidget.app

/**
 * Kept running by Android while the access is granted: it follows the media
 * session of Spotify and updates the widget when the track or the playback
 * state changes. Notifications themselves are ignored.
 */
class MediaListenerService : NotificationListenerService() {
    private var sessionManager: MediaSessionManager? = null
    private var controller: MediaController? = null

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onSessionDestroyed() {
            controller?.unregisterCallback(this)
            controller = null
            publish()
        }
    }

    private val sessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { attach(it) }

    override fun onListenerConnected() {
        val manager = getSystemService(MediaSessionManager::class.java)
        sessionManager = manager
        val component = Playback.listenerComponent(this)
        runCatching {
            manager.addOnActiveSessionsChangedListener(sessionsListener, component)
            // Always publish: what was saved may be stale (Spotify closed meanwhile)
            attach(manager.getActiveSessions(component), force = true)
        }
    }

    override fun onListenerDisconnected() {
        sessionManager?.removeOnActiveSessionsChangedListener(sessionsListener)
        controller?.unregisterCallback(callback)
        controller = null
        sessionManager = null
    }

    private fun attach(controllers: List<MediaController>?, force: Boolean = false) {
        val spotify = controllers?.firstOrNull { it.packageName == Playback.SPOTIFY_PACKAGE }
        if (!force && spotify?.sessionToken == controller?.sessionToken) return
        controller?.unregisterCallback(callback)
        controller = spotify
        spotify?.registerCallback(callback, Handler(Looper.getMainLooper()))
        publish()
    }

    private fun publish() {
        val current = controller
        app.nowPlaying.onSessionChanged(current?.metadata, current?.playbackState)
    }
}
