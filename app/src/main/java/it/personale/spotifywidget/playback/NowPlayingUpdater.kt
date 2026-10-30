package it.personale.spotifywidget.playback

import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.PlaybackState
import androidx.palette.graphics.Palette
import it.personale.spotifywidget.WidgetApp
import it.personale.spotifywidget.data.NowPlaying
import it.personale.spotifywidget.widget.RefreshWorker
import it.personale.spotifywidget.widget.updateWidgets
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * Turns the media session of Spotify into the data of the widget. Bursts of
 * changes (Spotify sends several when a track starts) become one update.
 */
class NowPlayingUpdater(private val app: WidgetApp) {
    private var pending: Job? = null

    fun onSessionChanged(metadata: MediaMetadata?, state: PlaybackState?) {
        pending?.cancel()
        pending = app.scope.launch {
            delay(DEBOUNCE_MS)
            apply(metadata, state)
        }
    }

    private suspend fun apply(metadata: MediaMetadata?, state: PlaybackState?) {
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
        if (metadata == null || title.isNullOrBlank()) {
            app.store.update { it.copy(nowPlaying = null) }
            updateWidgets(app)
            return
        }
        val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE).orEmpty()
        val album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM).orEmpty()
        val mediaId = metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)
        val isPlaying = state?.state == PlaybackState.STATE_PLAYING ||
            state?.state == PlaybackState.STATE_BUFFERING

        val previous = app.store.read().nowPlaying
        val key = NowPlaying(title, artist, album).key
        val sameTrack = previous?.key == key
        val keepArt = sameTrack && previous?.artPath?.let { File(it).exists() } == true
        val art = if (keepArt) null else coverOf(metadata)
        val artPath = if (keepArt) previous?.artPath else art?.let { app.images.saveNowPlaying(it, key) }
        val color = if (keepArt) previous?.color else art?.let { mainColor(it) }

        val nowPlaying = NowPlaying(
            title = title,
            artist = artist,
            album = album,
            trackUri = mediaId?.takeIf { it.startsWith("spotify:track:") }
                ?: previous?.trackUri?.takeIf { sameTrack },
            artPath = artPath,
            color = color,
            isPlaying = isPlaying,
        )
        app.store.update { it.copy(nowPlaying = nowPlaying) }
        updateWidgets(app)
        // A new track: find out whether it is liked and refresh the recent ones
        if (!sameTrack) RefreshWorker.refreshSoon(app)
    }

    private fun coverOf(metadata: MediaMetadata): Bitmap? =
        metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)

    private fun mainColor(bitmap: Bitmap): Int? = runCatching {
        val palette = Palette.from(bitmap).maximumColorCount(16).generate()
        (palette.darkVibrantSwatch ?: palette.vibrantSwatch ?: palette.dominantSwatch)?.rgb
    }.getOrNull()

    private companion object {
        const val DEBOUNCE_MS = 300L
    }
}
