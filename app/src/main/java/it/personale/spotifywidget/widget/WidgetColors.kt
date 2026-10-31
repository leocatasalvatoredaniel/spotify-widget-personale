package it.personale.spotifywidget.widget

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Color of the progress bar, as the media player of One UI picks it (measured
 * on a Galaxy S24 Ultra): the hue of the cover, fully saturated at 45%
 * lightness; a fixed night blue for covers without colors
 */
object WidgetColors {
    const val WHITE = 0xFFFFFFFF.toInt()
    const val BLACK = 0xFF000000.toInt()

    /** Used by One UI for black and white covers */
    const val NEUTRAL = 0xFF2D3854.toInt()

    /** Below this saturation a cover counts as black and white */
    private const val MIN_SATURATION = 0.15f
    private const val SATURATION = 1f
    private const val LIGHTNESS = 0.45f

    /** [coverColor]: the most colorful color of the cover, null if it has none */
    fun progressColor(coverColor: Int?): Int {
        if (coverColor == null) return NEUTRAL
        val (hue, saturation, _) = hsl(coverColor)
        if (saturation < MIN_SATURATION) return NEUTRAL
        return fromHsl(hue, SATURATION, LIGHTNESS)
    }

    /** Hue (0-360), saturation and lightness (0-1) */
    fun hsl(color: Int): Triple<Float, Float, Float> {
        val r = ((color shr 16) and 0xFF) / 255f
        val g = ((color shr 8) and 0xFF) / 255f
        val b = (color and 0xFF) / 255f
        val max = max(r, max(g, b))
        val min = min(r, min(g, b))
        val lightness = (max + min) / 2
        val delta = max - min
        if (delta == 0f) return Triple(0f, 0f, lightness)
        val saturation = delta / (1 - abs(2 * lightness - 1))
        val hue = when (max) {
            r -> 60 * (((g - b) / delta).mod(6f))
            g -> 60 * ((b - r) / delta + 2)
            else -> 60 * ((r - g) / delta + 4)
        }
        return Triple(hue, saturation, lightness)
    }

    fun fromHsl(hue: Float, saturation: Float, lightness: Float): Int {
        val chroma = (1 - abs(2 * lightness - 1)) * saturation
        val x = chroma * (1 - abs((hue / 60).mod(2f) - 1))
        val m = lightness - chroma / 2
        val (r, g, b) = when ((hue / 60).toInt().coerceIn(0, 5)) {
            0 -> Triple(chroma, x, 0f)
            1 -> Triple(x, chroma, 0f)
            2 -> Triple(0f, chroma, x)
            3 -> Triple(0f, x, chroma)
            4 -> Triple(x, 0f, chroma)
            else -> Triple(chroma, 0f, x)
        }
        fun channel(value: Float) = ((value + m) * 255).roundToInt().coerceIn(0, 255)
        return (0xFF shl 24) or (channel(r) shl 16) or (channel(g) shl 8) or channel(b)
    }

    fun luminance(color: Int): Double {
        fun linear(shift: Int): Double {
            val c = ((color shr shift) and 0xFF) / 255.0
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * linear(16) + 0.7152 * linear(8) + 0.0722 * linear(0)
    }

    fun contrast(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }
}
