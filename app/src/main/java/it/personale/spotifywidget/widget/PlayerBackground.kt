package it.personale.spotifywidget.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.graphics.createBitmap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The cover as the background of the player, shaded as in the media player
 * of One UI: at half brightness at the top center, darker towards the sides
 * and the bottom (measured on the player of a Galaxy S24 Ultra), so that the
 * text at the top and the controls at the bottom stay readable
 */
object PlayerBackground {
    /** Wide enough for a sharp cover, light enough for the launcher */
    private const val MAX_WIDTH_PX = 720

    // Black over the cover from the center (0) to the sides (1): 42% at the sides
    private val SIDE_ALPHAS = floatArrayOf(0.42f, 0.105f, 0f, 0.105f, 0.42f)
    private val SIDE_STOPS = floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f)

    // Black over the cover from the top to the bottom
    private const val TOP_ALPHA = 0.5f
    private const val BOTTOM_ALPHA = 0.7f

    fun render(cover: Bitmap, widthPx: Int, heightPx: Int): Bitmap {
        val ratio = min(1f, MAX_WIDTH_PX.toFloat() / widthPx)
        val width = max(1, (widthPx * ratio).roundToInt())
        val height = max(1, (heightPx * ratio).roundToInt())
        val result = createBitmap(width, height)
        val canvas = Canvas(result)

        // The cover fills the widget, cut at the sides or at the top and bottom
        val scale = max(width / cover.width.toFloat(), height / cover.height.toFloat())
        val coverWidth = cover.width * scale
        val coverHeight = cover.height * scale
        val left = (width - coverWidth) / 2
        val top = (height - coverHeight) / 2
        canvas.drawBitmap(
            cover,
            null,
            RectF(left, top, left + coverWidth, top + coverHeight),
            Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG),
        )

        val sides = Paint().apply {
            shader = LinearGradient(
                0f, 0f, width.toFloat(), 0f,
                SIDE_ALPHAS.map(::black).toIntArray(),
                SIDE_STOPS,
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), sides)

        val bottom = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, height.toFloat(),
                black(TOP_ALPHA),
                black(BOTTOM_ALPHA),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bottom)
        return result
    }

    private fun black(alpha: Float): Int = (alpha * 255).roundToInt() shl 24
}
