package it.personale.spotifywidget

import android.app.Application
import android.content.Context
import it.personale.spotifywidget.data.ImageCache
import it.personale.spotifywidget.data.WidgetStore
import it.personale.spotifywidget.playback.NowPlayingUpdater
import it.personale.spotifywidget.playback.ProgressTicker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.security.KeyStore

/**
 * Shared objects of the app: the widget, the media listener and the screens
 * use the same instances
 */
class WidgetApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val store: WidgetStore by lazy { WidgetStore(this) }
    val images: ImageCache by lazy { ImageCache(this) }
    val nowPlaying: NowPlayingUpdater by lazy { NowPlayingUpdater(this) }
    val ticker: ProgressTicker by lazy { ProgressTicker(this) }

    /** When play was pressed with Spotify closed (elapsed realtime), 0 if not */
    @Volatile
    var playRequestedAt: Long = 0

    override fun onCreate() {
        super.onCreate()
        scope.launch(Dispatchers.IO) { removeOldData() }
    }

    /**
     * Earlier versions logged in to the Spotify Web API: delete the saved
     * login, its key and the background jobs
     */
    private fun removeOldData() {
        runCatching { deleteSharedPreferences("auth") }
        runCatching {
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry("spotify-tokens")
        }
        runCatching { deleteDatabase("androidx.work.workdb") }
    }
}

val Context.app: WidgetApp get() = applicationContext as WidgetApp
