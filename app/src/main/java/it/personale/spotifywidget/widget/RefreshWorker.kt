package it.personale.spotifywidget.widget

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import it.personale.spotifywidget.api.bestFor
import it.personale.spotifywidget.app
import it.personale.spotifywidget.data.COVER_SIZE_PX
import it.personale.spotifywidget.data.toRecentItems
import java.util.concurrent.TimeUnit

/**
 * Data that needs the Web API: whether the track playing is liked, the
 * recently played tracks and the covers of the favorite playlists
 */
class RefreshWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext.app
        if (!app.auth.isLoggedIn) return Result.success()
        runCatching { refreshLiked() }
        runCatching { refreshRecent() }
        runCatching { refreshFavoriteCovers() }
        cleanUpCovers()
        updateWidgets(applicationContext)
        return Result.success()
    }

    private suspend fun refreshLiked() {
        val app = applicationContext.app
        val nowPlaying = app.store.read().nowPlaying ?: return
        // The media session of Spotify does not always say which track it is
        val uri = nowPlaying.trackUri
            ?: app.api.currentlyPlaying()?.item
                ?.takeIf { it.name == nowPlaying.title }
                ?.uri
            ?: return
        val liked = app.api.isSaved(uri)
        app.store.update { state ->
            if (state.nowPlaying?.key != nowPlaying.key) return@update state
            state.copy(
                nowPlaying = state.nowPlaying.copy(trackUri = uri),
                liked = liked,
                likedUri = uri,
            )
        }
    }

    private suspend fun refreshRecent() {
        val app = applicationContext.app
        val items = app.api.recentlyPlayed().toRecentItems()
            .map { it.copy(imagePath = app.images.fetch(it.imageUrl)) }
        app.store.update { it.copy(recent = items) }
    }

    private suspend fun refreshFavoriteCovers() {
        val app = applicationContext.app
        val favorites = app.store.read().favorites
        if (favorites.none { it.imagePath == null }) return
        // Cover URLs of playlists change when they are edited: read them again
        val playlists = app.api.myPlaylists().associateBy { it.uri }
        val updated = favorites.map { favorite ->
            val url = playlists[favorite.uri]?.images.bestFor(COVER_SIZE_PX) ?: favorite.imageUrl
            favorite.copy(imageUrl = url, imagePath = app.images.fetch(url))
        }
        app.store.update { it.copy(favorites = updated) }
    }

    private suspend fun cleanUpCovers() {
        val app = applicationContext.app
        val state = app.store.read()
        app.images.keepOnly(
            listOfNotNull(state.nowPlaying?.artPath).toSet() +
                state.favorites.mapNotNull { it.imagePath } +
                state.recent.mapNotNull { it.imagePath },
        )
    }

    companion object {
        private const val PERIODIC = "refresh-periodic"
        private const val SOON = "refresh-soon"

        private val network = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        fun schedulePeriodic(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<RefreshWorker>(30, TimeUnit.MINUTES)
                    .setConstraints(network)
                    .build(),
            )
        }

        /** After a track change: Spotify adds a track to the recent ones after a while */
        fun refreshSoon(context: Context, delaySeconds: Long = 5) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                SOON,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<RefreshWorker>()
                    .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
                    .setConstraints(network)
                    .build(),
            )
        }
    }
}
