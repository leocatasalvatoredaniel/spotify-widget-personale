package it.personale.spotifywidget.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import it.personale.spotifywidget.R
import it.personale.spotifywidget.api.SpotifyApiException
import it.personale.spotifywidget.api.pickPlaybackDevice
import it.personale.spotifywidget.api.playRequest
import it.personale.spotifywidget.app
import it.personale.spotifywidget.ui.PlayActivity
import kotlinx.coroutines.delay

/**
 * Starts a playback once Spotify, just opened, is ready on this phone
 */
class PlayWhenReadyWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val request = runCatching {
            playRequest(
                inputData.getString(PlayActivity.EXTRA_CONTEXT),
                inputData.getString(PlayActivity.EXTRA_TRACK),
            )
        }.getOrElse { return Result.failure() }
        val api = applicationContext.app.api
        repeat(ATTEMPTS) {
            delay(WAIT_MS)
            val device = runCatching { api.devices().pickPlaybackDevice() }.getOrNull()
            if (device?.id != null) {
                return try {
                    api.play(request, device.id)
                    Result.success()
                } catch (error: SpotifyApiException) {
                    Result.failure()
                }
            }
        }
        return Result.failure()
    }

    // Needed by expedited work on Android 11 and older
    override suspend fun getForegroundInfo(): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, applicationContext.getString(R.string.app_name), NotificationManager.IMPORTANCE_LOW),
        )
        val notification = Notification.Builder(applicationContext, CHANNEL)
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentTitle(applicationContext.getString(R.string.play_starting))
            .build()
        return ForegroundInfo(NOTIFICATION_ID, notification)
    }

    companion object {
        const val NAME = "play-when-ready"
        private const val CHANNEL = "playback"
        private const val NOTIFICATION_ID = 1
        private const val ATTEMPTS = 10
        private const val WAIT_MS = 1_500L
    }
}
