package it.personale.spotifywidget.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.graphics.scale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * The cover of the track playing, saved as a file: the widget is redrawn
 * also when Spotify is closed
 */
class ImageCache(context: Context) {
    private val dir = File(context.filesDir, "covers").apply { mkdirs() }

    /** Saves the cover of the track playing now */
    suspend fun saveNowPlaying(bitmap: Bitmap, key: String): String = withContext(Dispatchers.IO) {
        val file = File(dir, "now-${sha1(key)}.jpg")
        if (!file.exists()) {
            file.outputStream().use {
                scaleDown(bitmap, NOW_PLAYING_SIZE_PX).compress(Bitmap.CompressFormat.JPEG, 90, it)
            }
        }
        file.path
    }

    /** Deletes the covers no longer shown */
    suspend fun keepOnly(paths: Set<String>) = withContext(Dispatchers.IO) {
        dir.listFiles()?.filter { it.path !in paths }?.forEach { it.delete() }
    }

    companion object {
        // The cover fills the whole widget: about its width on a phone
        private const val NOW_PLAYING_SIZE_PX = 720

        fun scaleDown(bitmap: Bitmap, maxSize: Int): Bitmap {
            val largest = maxOf(bitmap.width, bitmap.height)
            if (largest <= maxSize) return bitmap
            val ratio = maxSize.toFloat() / largest
            return bitmap.scale(
                (bitmap.width * ratio).toInt().coerceAtLeast(1),
                (bitmap.height * ratio).toInt().coerceAtLeast(1),
            )
        }

        fun load(path: String?): Bitmap? =
            path?.let { runCatching { BitmapFactory.decodeFile(it) }.getOrNull() }

        private fun sha1(text: String): String =
            MessageDigest.getInstance("SHA-1").digest(text.toByteArray())
                .joinToString("") { "%02x".format(it) }
    }
}
