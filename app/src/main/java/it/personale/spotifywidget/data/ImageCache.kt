package it.personale.spotifywidget.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.graphics.scale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest

/**
 * Covers saved as small files: the widget is drawn in another process and
 * cannot download them while it is drawn
 */
class ImageCache(context: Context, private val http: OkHttpClient) {
    private val dir = File(context.filesDir, "covers").apply { mkdirs() }

    /** Path of the downloaded image, null if it cannot be downloaded */
    suspend fun fetch(url: String?): String? = withContext(Dispatchers.IO) {
        if (url == null) return@withContext null
        val file = File(dir, "${sha1(url)}.jpg")
        if (file.exists()) return@withContext file.path
        runCatching {
            http.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                val bytes = response.body?.bytes() ?: return@runCatching null
                val bitmap = decodeScaled(bytes) ?: return@runCatching null
                save(bitmap, file)
                file.path
            }
        }.getOrNull()
    }

    /** Saves the cover of the track playing now */
    suspend fun saveNowPlaying(bitmap: Bitmap, key: String): String = withContext(Dispatchers.IO) {
        val file = File(dir, "now-${sha1(key)}.jpg")
        if (!file.exists()) save(scaleDown(bitmap, NOW_PLAYING_SIZE_PX), file)
        file.path
    }

    /** Deletes the covers no longer shown */
    suspend fun keepOnly(paths: Set<String>) = withContext(Dispatchers.IO) {
        dir.listFiles()?.filter { it.path !in paths }?.forEach { it.delete() }
    }

    private fun save(bitmap: Bitmap, file: File) {
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
    }

    private fun decodeScaled(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= COVER_SIZE_PX) sample *= 2
        val bitmap = BitmapFactory.decodeByteArray(
            bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample },
        ) ?: return null
        return scaleDown(bitmap, COVER_SIZE_PX)
    }

    companion object {
        private const val NOW_PLAYING_SIZE_PX = 320

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
