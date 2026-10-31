package it.personale.spotifywidget

import android.app.Application
import android.content.Context
import it.personale.spotifywidget.api.SpotifyApi
import android.net.Uri
import it.personale.spotifywidget.auth.LoopbackReceiver
import it.personale.spotifywidget.auth.SpotifyAuth
import it.personale.spotifywidget.auth.TokenStore
import it.personale.spotifywidget.auth.loginPage
import it.personale.spotifywidget.data.ImageCache
import it.personale.spotifywidget.data.WidgetStore
import it.personale.spotifywidget.playback.NowPlayingUpdater
import it.personale.spotifywidget.widget.RefreshWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Shared objects of the app: the widget, the media listener and the screens
 * use the same instances
 */
class WidgetApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }
    val auth: SpotifyAuth by lazy { SpotifyAuth(http, TokenStore(this)) }
    val api: SpotifyApi by lazy { SpotifyApi(http, auth) }
    val store: WidgetStore by lazy { WidgetStore(this) }
    val images: ImageCache by lazy { ImageCache(this, http) }
    val nowPlaying: NowPlayingUpdater by lazy { NowPlayingUpdater(this) }
    private val loginReceiver by lazy { LoopbackReceiver(scope) }

    /** Starts the Spotify login: the URI to open in the browser */
    fun startLogin(): Uri = auth.authorizationUri(loginReceiver.start(::completeLogin))

    /** End of the login in the browser: the page the browser shows */
    private suspend fun completeLogin(callback: Uri): String {
        val result = runCatching { auth.completeLogin(callback) }
        if (result.isSuccess) RefreshWorker.refreshSoon(this, 0)
        return loginPage(
            title = getString(if (result.isSuccess) R.string.login_done else R.string.login_failed_title),
            message = result.exceptionOrNull()?.let { getString(R.string.login_failed, it.message) },
            button = getString(R.string.login_back),
            success = result.isSuccess,
        )
    }

    override fun onCreate() {
        super.onCreate()
        RefreshWorker.schedulePeriodic(this)
    }
}

val Context.app: WidgetApp get() = applicationContext as WidgetApp
