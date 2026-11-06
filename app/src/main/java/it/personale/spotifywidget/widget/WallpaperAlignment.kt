package it.personale.spotifywidget.widget

import android.os.Environment
import java.io.File

/**
 * The [Alignment] found for a wallpaper, kept in a file of the shared storage
 * of the phone: the other personal widgets (the PoliTo agenda) read it too,
 * since they read the same wallpaper with the access to all files.
 * Line 1: the wallpaper ("system:<id>" or "picked:<date>"); line 2: zoom,
 * shift x, shift y.
 */
object WallpaperAlignment {
    private val file: File
        get() = File(Environment.getExternalStorageDirectory(), ".allineamento-sfondo-widget")

    /** The alignment of the wallpaper [key], if it was found */
    fun read(key: String): Alignment? = runCatching {
        val lines = file.readLines()
        if (lines.firstOrNull()?.trim() != key) return null
        Alignment.parse(lines.getOrNull(1) ?: return null)
    }.getOrNull()

    fun write(key: String, alignment: Alignment) {
        runCatching { file.writeText("$key\n${alignment.format()}\n") }
    }

    fun forget() {
        runCatching { file.delete() }
    }
}
