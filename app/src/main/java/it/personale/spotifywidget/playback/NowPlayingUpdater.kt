package it.personale.spotifywidget.playback

import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import androidx.palette.graphics.Palette
import it.personale.spotifywidget.WidgetApp
import it.personale.spotifywidget.data.NowPlaying
import it.personale.spotifywidget.data.SessionAction
import it.personale.spotifywidget.widget.WidgetColors
import it.personale.spotifywidget.widget.WidgetRenderer
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

    fun onSessionChanged(controller: MediaController?) {
        pending?.cancel()
        pending = app.scope.launch {
            delay(DEBOUNCE_MS)
            apply(controller)
        }
    }

    private suspend fun apply(controller: MediaController?) {
        val metadata = controller?.metadata
        val state = controller?.playbackState
        val now = System.currentTimeMillis()
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
        if (metadata == null || title.isNullOrBlank()) {
            // Spotify closed: the last track stays, paused
            app.store.update { it.copy(nowPlaying = it.nowPlaying?.stopped(now)) }
            WidgetRenderer.renderAll(app)
            return
        }
        val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE).orEmpty()
        val album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM).orEmpty()

        val previous = app.store.read().nowPlaying
        val key = NowPlaying(title, artist, album).key
        val keepArt = previous?.key == key && previous.artPath?.let { File(it).exists() } == true
        val art = if (keepArt) null else coverOf(metadata)
        val artPath = if (keepArt) previous?.artPath else art?.let { app.images.saveNowPlaying(it, key) }
        val accent = if (keepArt) previous?.accent else art?.let { accentOf(it) }

        val nowPlaying = NowPlaying(
            title = title,
            artist = artist,
            album = album,
            artPath = artPath,
            accent = accent,
            isPlaying = Playback.isPlaying(state),
            durationMs = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION).coerceAtLeast(0),
            positionMs = state?.let { Playback.positionNow(it) }?.coerceAtLeast(0) ?: 0,
            positionAt = now,
            actions = state?.customActions.orEmpty()
                .filter { it.icon != 0 }
                .map { SessionAction(it.action, it.name?.toString().orEmpty(), it.icon) },
            remote = controller.playbackInfo?.playbackType ==
                MediaController.PlaybackInfo.PLAYBACK_TYPE_REMOTE,
        )
        app.store.update { it.copy(nowPlaying = nowPlaying) }
        if (!keepArt) app.images.keepOnly(setOfNotNull(artPath))
        WidgetRenderer.renderAll(app)
    }

    private fun coverOf(metadata: MediaMetadata): Bitmap? =
        metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)

    /** A lively color of the cover for the progress bar, as the system player */
    private fun accentOf(bitmap: Bitmap): Int? = runCatching {
        val palette = Palette.from(bitmap).maximumColorCount(16).generate()
        val swatch = palette.vibrantSwatch ?: palette.lightVibrantSwatch ?: palette.dominantSwatch
        swatch?.rgb?.let(WidgetColors::accent)
    }.getOrNull()

    private companion object {
        const val DEBOUNCE_MS = 300L
    }
}
