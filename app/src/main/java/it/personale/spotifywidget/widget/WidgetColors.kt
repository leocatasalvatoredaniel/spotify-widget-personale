package it.personale.spotifywidget.widget

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Color of the progress bar, taken from the cover as in the system player:
 * lightened when needed, so that it stands out on the darkened cover
 */
object WidgetColors {
    const val WHITE = 0xFFFFFFFF.toInt()
    const val BLACK = 0xFF000000.toInt()

    /** A typical cover under the dark layer of the widget */
    const val DARKENED_COVER = 0xFF333333.toInt()
    private const val MIN_CONTRAST = 3.0

    fun accent(color: Int): Int {
        var result = opaque(color)
        var steps = 0
        while (contrast(result, DARKENED_COVER) < MIN_CONTRAST && steps < 30) {
            result = blend(result, WHITE, 0.12)
            steps++
        }
        return result
    }

    fun opaque(color: Int): Int = color or (0xFF shl 24)

    fun blend(from: Int, to: Int, amount: Double): Int {
        fun channel(shift: Int): Int {
            val a = (from shr shift) and 0xFF
            val b = (to shr shift) and 0xFF
            return (a + (b - a) * amount).toInt().coerceIn(0, 255)
        }
        return (0xFF shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
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
