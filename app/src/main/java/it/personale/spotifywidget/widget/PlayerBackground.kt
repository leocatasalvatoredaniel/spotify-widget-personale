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
 * of One UI. Measured on a Galaxy S24 Ultra by comparing the same cover in
 * the system player and in the widget: at 51% of its brightness at the top
 * center, about half of that at the sides, and 19% at the bottom center.
 */
object PlayerBackground {
    /** Sharp up to the width of the widget on a large phone */
    private const val MAX_WIDTH_PX = 1600

    // Black over the cover across the width (0 = left, 1 = right): none in the
    // middle, 52% at the sides
    private val SIDE_STOPS = floatArrayOf(0f, 0.04f, 0.125f, 0.21f, 0.29f, 0.375f, 0.46f, 0.54f, 0.625f, 0.71f, 0.79f, 0.875f, 0.96f, 1f)
    private val SIDE_ALPHAS = floatArrayOf(0.52f, 0.49f, 0.43f, 0.32f, 0.18f, 0.06f, 0f, 0f, 0.06f, 0.18f, 0.32f, 0.43f, 0.49f, 0.52f)

    // Black over the cover from the top (0) to the bottom (1)
    private val HEIGHT_STOPS = floatArrayOf(0f, 0.19f, 0.31f, 0.44f, 0.56f, 0.69f, 0.81f, 0.94f, 1f)
    private val HEIGHT_ALPHAS = floatArrayOf(0.49f, 0.5f, 0.52f, 0.56f, 0.61f, 0.66f, 0.74f, 0.81f, 0.84f)

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

        val heights = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, height.toFloat(),
                HEIGHT_ALPHAS.map(::black).toIntArray(),
                HEIGHT_STOPS,
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), heights)
        return result
    }

    private fun black(alpha: Float): Int = (alpha * 255).roundToInt() shl 24
}
