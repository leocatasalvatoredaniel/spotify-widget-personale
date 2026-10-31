package it.personale.spotifywidget.playback

import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import it.personale.spotifywidget.app
import it.personale.spotifywidget.widget.WidgetRenderer
import kotlinx.coroutines.launch

/**
 * Kept running by Android while the access is granted: it follows the media
 * session of Spotify and updates the widget when the track, the playback
 * state or the audio output change. The notifications themselves are not
 * read.
 */
class MediaListenerService : NotificationListenerService() {
    private val handler = Handler(Looper.getMainLooper())
    private var sessionManager: MediaSessionManager? = null
    private var controller: MediaController? = null
    private var screenOn = true
    private var receiverRegistered = false

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onAudioInfoChanged(info: MediaController.PlaybackInfo) = publish()
        override fun onSessionDestroyed() {
            controller?.unregisterCallback(this)
            controller = null
            publish()
        }
    }

    private val sessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { attach(it) }

    // Earphones connected or disconnected: the name of the output changes
    private val devicesCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>?) = publish()
        override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>?) = publish()
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            screenOn = intent.action == Intent.ACTION_SCREEN_ON
            app.ticker.restart(controller, screenOn)
        }
    }

    override fun onListenerConnected() {
        val manager = getSystemService(MediaSessionManager::class.java)
        sessionManager = manager
        screenOn = getSystemService(PowerManager::class.java).isInteractive
        if (!receiverRegistered) {
            registerReceiver(
                screenReceiver,
                IntentFilter().apply {
                    addAction(Intent.ACTION_SCREEN_ON)
                    addAction(Intent.ACTION_SCREEN_OFF)
                },
            )
            receiverRegistered = true
        }
        getSystemService(AudioManager::class.java).registerAudioDeviceCallback(devicesCallback, handler)
        // The veil of the Samsung style takes the colors of the wallpaper (from Android 8.1)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            WallpaperManager.getInstance(this).addOnColorsChangedListener(wallpaperListener, handler)
        }
        val component = Playback.listenerComponent(this)
        runCatching {
            manager.addOnActiveSessionsChangedListener(sessionsListener, component)
            // Always publish: what was saved may be stale (Spotify closed meanwhile)
            attach(manager.getActiveSessions(component), force = true)
        }
    }

    private val wallpaperListener by lazy {
        WallpaperManager.OnColorsChangedListener { _, which ->
            if (which and WallpaperManager.FLAG_SYSTEM != 0) app.scope.launch { WidgetRenderer.renderAll(app) }
        }
    }

    override fun onListenerDisconnected() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            WallpaperManager.getInstance(this).removeOnColorsChangedListener(wallpaperListener)
        }
        sessionManager?.removeOnActiveSessionsChangedListener(sessionsListener)
        if (receiverRegistered) {
            runCatching { unregisterReceiver(screenReceiver) }
            receiverRegistered = false
        }
        getSystemService(AudioManager::class.java).unregisterAudioDeviceCallback(devicesCallback)
        controller?.unregisterCallback(callback)
        controller = null
        sessionManager = null
        app.ticker.stop()
    }

    private fun attach(controllers: List<MediaController>?, force: Boolean = false) {
        val spotify = controllers?.firstOrNull { it.packageName == Playback.SPOTIFY_PACKAGE }
        if (!force && spotify?.sessionToken == controller?.sessionToken) return
        controller?.unregisterCallback(callback)
        controller = spotify
        spotify?.registerCallback(callback, handler)
        publish()
    }

    private fun publish() {
        val current = controller
        playIfRequested(current)
        app.nowPlaying.onSessionChanged(current)
        app.ticker.restart(current, screenOn)
    }

    /** Play pressed in the widget while Spotify was closed: start when it is ready */
    private fun playIfRequested(controller: MediaController?) {
        val requested = app.playRequestedAt
        if (controller == null || requested == 0L) return
        when {
            SystemClock.elapsedRealtime() - requested > PLAY_REQUEST_MS -> app.playRequestedAt = 0
            controller.playbackState?.state == PlaybackState.STATE_PLAYING -> app.playRequestedAt = 0
            else -> controller.transportControls.play()
        }
    }

    private companion object {
        const val PLAY_REQUEST_MS = 20_000L
    }
}
