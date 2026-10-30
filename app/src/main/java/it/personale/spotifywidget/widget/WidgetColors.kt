package it.personale.spotifywidget.widget

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Colors of the widget, taken from the cover: a dark background with white
 * text that stays readable (at least 7:1, so secondary text at 80% opacity
 * is still above 4.5:1)
 */
data class WidgetColors(
    val background: Int,
    val text: Int,
    val secondary: Int,
    val button: Int,
    val onButton: Int,
) {
    companion object {
        const val WHITE = 0xFFFFFFFF.toInt()
        const val BLACK = 0xFF000000.toInt()
        private const val DEFAULT_BACKGROUND = 0xFF1F2A2E.toInt()
        private const val MIN_CONTRAST = 7.0

        fun fromCover(coverColor: Int?): WidgetColors {
            val background = darkEnough(coverColor ?: DEFAULT_BACKGROUND)
            return WidgetColors(
                background = background,
                text = WHITE,
                secondary = withAlpha(WHITE, 0.8),
                button = WHITE,
                onButton = background,
            )
        }

        fun darkEnough(color: Int): Int {
            var result = opaque(color)
            var steps = 0
            while (contrast(WHITE, result) < MIN_CONTRAST && steps < 30) {
                result = blend(result, BLACK, 0.12)
                steps++
            }
            return result
        }

        fun withAlpha(color: Int, alpha: Double): Int =
            ((alpha * 255).toInt() shl 24) or (color and 0x00FFFFFF)

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
}
