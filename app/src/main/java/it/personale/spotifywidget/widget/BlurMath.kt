package it.personale.spotifywidget.widget

import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * The background of the Samsung widgets, measured on the Home screen of a
 * Galaxy S24 Ultra next to a Samsung widget at 50%, dark and light, on the
 * same wallpaper: the wallpaper blurred (Gaussian, about 17 dp), its colors
 * 2.77 times stronger around the lightness of each point, then the veil of
 * the widget ([WidgetColors.blurVeil]). The model differs from the Samsung
 * widgets by 3.7/255 per point (rms), the same for dark and light.
 */
object BlurMath {
    /** Spread of the blur (dp) */
    const val BLUR_DP = 17f

    /** How much stronger the colors get */
    const val SATURATION = 2.77f

    /**
     * Blur of a wallpaper drawn by an app of wallpapers (One UI reports the
     * photo of the Home screen so): One UI shows the photo a little blurred
     * on the Home screen, so its image gets that blur too. Measured (about
     * 4 dp) on a screenshot of the Home screen against the photo itself;
     * the Samsung widget there blurs it about 17-18 dp in all.
     */
    const val HOME_BLUR_DP = 4f

    /** Spread of the blur (dp) for the image of the wallpaper */
    fun blurDp(drawnByApp: Boolean): Float =
        if (drawnByApp) sqrt(BLUR_DP * BLUR_DP + HOME_BLUR_DP * HOME_BLUR_DP) else BLUR_DP

    /** Gaussian blur of ARGB [pixels]; the edges repeat outwards. The result is opaque. */
    fun gaussian(pixels: IntArray, width: Int, height: Int, sigma: Float): IntArray {
        require(pixels.size == width * height)
        if (sigma < 0.3f) return pixels.map { it or OPAQUE }.toIntArray()
        val radius = ceil(sigma * 3).toInt()
        val kernel = FloatArray(radius * 2 + 1) { exp(-((it - radius) * (it - radius)) / (2 * sigma * sigma)) }
        val total = kernel.sum()
        for (i in kernel.indices) kernel[i] /= total

        val channels = listOf(16, 8, 0).map { shift -> FloatArray(pixels.size) { ((pixels[it] shr shift) and 0xFF).toFloat() } }
        val buffer = FloatArray(pixels.size)
        channels.forEach { channel ->
            // Rows, then columns
            for (y in 0 until height) {
                val row = y * width
                for (x in 0 until width) {
                    var sum = 0f
                    for (k in kernel.indices) {
                        val sx = (x + k - radius).coerceIn(0, width - 1)
                        sum += channel[row + sx] * kernel[k]
                    }
                    buffer[row + x] = sum
                }
            }
            for (x in 0 until width) {
                for (y in 0 until height) {
                    var sum = 0f
                    for (k in kernel.indices) {
                        val sy = (y + k - radius).coerceIn(0, height - 1)
                        sum += buffer[sy * width + x] * kernel[k]
                    }
                    channel[y * width + x] = sum
                }
            }
        }
        return IntArray(pixels.size) { i ->
            OPAQUE or (channelOf(channels[0][i]) shl 16) or (channelOf(channels[1][i]) shl 8) or channelOf(channels[2][i])
        }
    }

    /** One blurred point as behind a Samsung widget: stronger colors, then the [veil] over them */
    fun samsung(pixel: Int, veil: Int): Int {
        val alpha = (veil ushr 24) / 255f
        val r = ((pixel shr 16) and 0xFF).toFloat()
        val g = ((pixel shr 8) and 0xFF).toFloat()
        val b = (pixel and 0xFF).toFloat()
        val lightness = 0.2126f * r + 0.7152f * g + 0.0722f * b
        fun channel(value: Float, shift: Int): Int {
            val strong = (lightness + SATURATION * (value - lightness)).coerceIn(0f, 255f)
            return channelOf(alpha * ((veil shr shift) and 0xFF) + (1 - alpha) * strong)
        }
        return OPAQUE or (channel(r, 16) shl 16) or (channel(g, 8) shl 8) or channel(b, 0)
    }

    /** Average color of [pixels] */
    fun average(pixels: IntArray): Int {
        if (pixels.isEmpty()) return OPAQUE
        var r = 0L
        var g = 0L
        var b = 0L
        pixels.forEach {
            r += (it shr 16) and 0xFF
            g += (it shr 8) and 0xFF
            b += it and 0xFF
        }
        val n = pixels.size
        return OPAQUE or ((r / n).toInt() shl 16) or ((g / n).toInt() shl 8) or (b / n).toInt()
    }

    private fun channelOf(value: Float) = value.roundToInt().coerceIn(0, 255)

    private const val OPAQUE = 0xFF shl 24
}
