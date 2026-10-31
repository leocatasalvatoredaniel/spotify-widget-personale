package it.personale.spotifywidget.playback

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationManagerCompat
import it.personale.spotifywidget.R

/**
 * Spotify controlled through its media session, as the media player of the
 * system: it works offline and without a Spotify account in the app
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

    fun isPlaying(state: PlaybackState?): Boolean =
        state?.state == PlaybackState.STATE_PLAYING || state?.state == PlaybackState.STATE_BUFFERING

    /** Position now: Spotify reports it only when it changes it */
    fun positionNow(state: PlaybackState): Long {
        if (state.state != PlaybackState.STATE_PLAYING || state.lastPositionUpdateTime <= 0) {
            return state.position
        }
        val elapsed = SystemClock.elapsedRealtime() - state.lastPositionUpdateTime
        return state.position + (elapsed * state.playbackSpeed).toLong()
    }

    /** False if there is no Spotify session to control */
    fun playPause(context: Context): Boolean {
        val controller = spotifyController(context) ?: return false
        if (isPlaying(controller.playbackState)) controller.transportControls.pause()
        else controller.transportControls.play()
        return true
    }

    fun next(context: Context): Boolean =
        spotifyController(context)?.transportControls?.skipToNext() != null

    fun previous(context: Context): Boolean =
        spotifyController(context)?.transportControls?.skipToPrevious() != null

    /** Presses a button of Spotify, as the media player of the system does */
    fun customAction(context: Context, action: String?): Boolean {
        val controller = spotifyController(context) ?: return false
        val custom = controller.playbackState?.customActions
            ?.firstOrNull { it.action == action }
            ?: return false
        controller.transportControls.sendCustomAction(custom, custom.extras)
        return true
    }

    /** Opens Spotify, or null if it is not installed */
    fun spotifyLaunchIntent(context: Context): Intent? =
        context.packageManager.getLaunchIntentForPackage(SPOTIFY_PACKAGE)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Where the music plays, e.g. "Questo smartphone" or the earphones */
    fun outputName(context: Context, controller: MediaController?): String {
        if (controller?.playbackInfo?.playbackType == MediaController.PlaybackInfo.PLAYBACK_TYPE_REMOTE) {
            return context.getString(R.string.device_connect)
        }
        val device = runCatching { mediaOutput(context) }.getOrNull()
        return outputLabel(
            type = device?.type,
            productName = device?.productName?.toString(),
            thisPhone = context.getString(R.string.device_this_phone),
            wired = context.getString(R.string.device_wired),
        )
    }

    private fun mediaOutput(context: Context): AudioDeviceInfo? {
        val audio = context.getSystemService(AudioManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val media = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build()
            return audio.getAudioDevicesForAttributes(media).firstOrNull()
        }
        // Before Android 13: connected earphones or speakers take the music
        return audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .firstOrNull { it.type in EXTERNAL_OUTPUTS }
    }

    private val EXTERNAL_OUTPUTS = setOf(
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_USB_DEVICE,
    )

    /**
     * The output switcher of the system for Spotify, as the button of the
     * media player: Samsung's media panel, otherwise the one of Android,
     * otherwise Spotify itself
     */
    fun outputSwitcher(context: Context): OutputSwitcher {
        val packageManager = context.packageManager
        samsungMediaPanel(packageManager)?.let { return OutputSwitcher.Activity(it, OutputSwitcher.Kind.SAMSUNG) }
        val panel = Intent(ACTION_MEDIA_OUTPUT_PANEL)
            .putExtra(EXTRA_MEDIA_OUTPUT_PANEL_PACKAGE, SPOTIFY_PACKAGE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val activity = packageManager.resolveActivity(panel, PackageManager.MATCH_DEFAULT_ONLY)
        if (activity != null && activity.activityInfo.exported && activity.activityInfo.permission == null) {
            return OutputSwitcher.Activity(panel, OutputSwitcher.Kind.ANDROID)
        }
        val dialog = Intent(ACTION_MEDIA_OUTPUT_DIALOG)
            .setPackage(SYSTEMUI_PACKAGE)
            .putExtra(EXTRA_MEDIA_OUTPUT_DIALOG_PACKAGE, SPOTIFY_PACKAGE)
        val receiver = packageManager.queryBroadcastReceivers(dialog, 0).firstOrNull()
        if (receiver != null && receiver.activityInfo.exported && receiver.activityInfo.permission == null) {
            return OutputSwitcher.Broadcast(dialog)
        }
        return OutputSwitcher.Activity(spotifyLaunchIntent(context), OutputSwitcher.Kind.SPOTIFY)
    }

    /**
     * The media panel of One UI («Media and devices»): the media playing and
     * the audio outputs, with the volume and Smart View. Samsung does not
     * document it, so it is used only if another app may open it.
     */
    private fun samsungMediaPanel(packageManager: PackageManager): Intent? {
        val panel = Intent()
            .setComponent(ComponentName(SAMSUNG_MEDIA_PACKAGE, SAMSUNG_MEDIA_ACTIVITY))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (canOpen(packageManager, panel)) return panel
        val main = packageManager.getLaunchIntentForPackage(SAMSUNG_MEDIA_PACKAGE)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return main?.takeIf { canOpen(packageManager, it) }
    }

    private fun canOpen(packageManager: PackageManager, intent: Intent): Boolean {
        val info = runCatching { packageManager.resolveActivity(intent, 0)?.activityInfo }.getOrNull()
            ?: return false
        return info.exported && info.enabled && info.permission == null
    }

    /** Activities of the Samsung media panel that other apps may open, to diagnose the button */
    fun samsungMediaActivities(context: Context): List<String>? = runCatching {
        context.packageManager
            .getPackageInfo(SAMSUNG_MEDIA_PACKAGE, PackageManager.GET_ACTIVITIES)
            .activities.orEmpty()
            .filter { it.exported && it.permission == null }
            .map { it.name.removePrefix("$SAMSUNG_MEDIA_PACKAGE.") }
    }.getOrNull()

    sealed interface OutputSwitcher {
        val kind: Kind

        /** [intent] is null when Spotify is not installed */
        class Activity(val intent: Intent?, override val kind: Kind) : OutputSwitcher
        class Broadcast(val intent: Intent) : OutputSwitcher {
            override val kind = Kind.ANDROID
        }

        enum class Kind { SAMSUNG, ANDROID, SPOTIFY }
    }

    // Media panel of One UI
    private const val SAMSUNG_MEDIA_PACKAGE = "com.samsung.android.mdx.quickboard"
    private const val SAMSUNG_MEDIA_ACTIVITY = "com.samsung.android.mdx.quickboard.view.MediaActivity"

    // Used by the media player of the system (Settings and System UI)
    private const val ACTION_MEDIA_OUTPUT_PANEL = "com.android.settings.panel.action.MEDIA_OUTPUT"
    private const val EXTRA_MEDIA_OUTPUT_PANEL_PACKAGE = "com.android.settings.panel.extra.PACKAGE_NAME"
    private const val SYSTEMUI_PACKAGE = "com.android.systemui"
    private const val ACTION_MEDIA_OUTPUT_DIALOG = "com.android.systemui.action.LAUNCH_MEDIA_OUTPUT_DIALOG"
    private const val EXTRA_MEDIA_OUTPUT_DIALOG_PACKAGE = "package_name"
}

/** Name of an audio output as the system player shows it */
fun outputLabel(type: Int?, productName: String?, thisPhone: String, wired: String): String =
    when (type) {
        null,
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE,
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE,
        AudioDeviceInfo.TYPE_TELEPHONY,
        -> thisPhone
        // Wired earphones report the name of the phone
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_LINE_ANALOG,
        -> wired
        else -> productName?.takeIf { it.isNotBlank() } ?: thisPhone
    }
