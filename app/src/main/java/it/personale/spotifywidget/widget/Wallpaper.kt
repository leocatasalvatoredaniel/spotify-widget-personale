package it.personale.spotifywidget.widget

import android.app.WallpaperManager
import android.content.Context
import android.os.Build

/** What an app can know of the wallpaper of the Home screen without permissions */
object Wallpaper {
    /** Main color of the wallpaper, from Android 8.1; null when the phone does not tell it */
    fun color(context: Context): Int? = runCatching {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) return null
        WallpaperManager.getInstance(context)
            .getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
            ?.primaryColor
            ?.toArgb()
    }.getOrNull()

    /** The wallpaper is animated: it may not tell its colors */
    fun isLive(context: Context): Boolean =
        runCatching { WallpaperManager.getInstance(context).wallpaperInfo != null }.getOrDefault(false)
}
