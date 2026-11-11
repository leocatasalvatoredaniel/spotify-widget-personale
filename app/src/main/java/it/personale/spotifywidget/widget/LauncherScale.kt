package it.personale.spotifywidget.widget

import android.os.Environment
import java.io.File

/**
 * How much the launcher scales the widgets (see [CardLocator]), as last
 * measured by one of the widgets of the phone made this way: a file in the
 * storage of the phone, which the agenda widget of PoliTo reads and writes
 * too. A widget that knows it needs one tap to know where it is.
 */
object LauncherScale {
    @Volatile
    private var cached: Float? = null

    @Volatile
    private var read = false

    private fun file() = File(Environment.getExternalStorageDirectory(), ".scala-widget-home")

    /** The scale another widget measured; null when none did, or without access to the files */
    fun shared(refresh: Boolean = false): Float? {
        if (read && !refresh) return cached
        cached = if (!WallpaperBlur.canRead()) null else runCatching {
            file().readText().trim().toFloat().takeIf { it in 0.5f..1.25f }
        }.getOrNull()
        read = true
        return cached
    }

    fun share(scale: Float) {
        cached = scale
        read = true
        if (!WallpaperBlur.canRead()) return
        runCatching { file().writeText(scale.toString()) }
    }
}
