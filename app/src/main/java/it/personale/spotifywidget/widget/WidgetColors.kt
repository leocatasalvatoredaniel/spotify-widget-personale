package it.personale.spotifywidget.widget

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
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

    /** One color of the cover and how many of its pixels have it */
    data class CoverColor(val color: Int, val population: Int)

    /**
     * The color that leads the cover, as Material's «Score» (Android's color
     * extraction) and the media player of One UI pick it: the hue that covers
     * the most of the cover together with its neighbors, preferring colorful
     * ones; null when the cover has no colors. A small but bright detail
     * (e.g. yellow hair on a pink cover) does not win over the main hue.
     */
    fun sourceColor(colors: List<CoverColor>): Int? {
        val total = colors.sumOf { it.population }.toFloat()
        if (total <= 0f) return null
        val lch = colors.map { it to lch(it.color) }
        val huePopulation = FloatArray(360)
        lch.forEach { (color, value) -> huePopulation[value.third.roundToInt().mod(360)] += color.population.toFloat() }
        fun share(hue: Int): Float = (-14..15).sumOf { huePopulation[(hue + it).mod(360)].toDouble() }.toFloat() / total
        return lch
            .map { (color, value) -> Triple(color.color, value.second, share(value.third.roundToInt().mod(360))) }
            .filter { (_, chroma, share) -> chroma >= MIN_CHROMA && share > MIN_SHARE }
            .maxByOrNull { (_, chroma, share) ->
                share * 100 * 0.7f + (chroma - TARGET_CHROMA) * (if (chroma < TARGET_CHROMA) 0.1f else 0.3f)
            }
            ?.first
    }

    // Thresholds of Material's «Score»
    private const val MIN_CHROMA = 5f
    private const val MIN_SHARE = 0.01f
    private const val TARGET_CHROMA = 48f

    /** CIE lightness, chroma and hue (degrees) of an sRGB color */
    fun lch(color: Int): Triple<Float, Float, Float> {
        fun linear(shift: Int): Double {
            val c = ((color shr shift) and 0xFF) / 255.0
            return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        val r = linear(16)
        val g = linear(8)
        val b = linear(0)
        val x = (0.4124 * r + 0.3576 * g + 0.1805 * b) / 0.95047
        val y = 0.2126 * r + 0.7152 * g + 0.0722 * b
        val z = (0.0193 * r + 0.1192 * g + 0.9505 * b) / 1.08883
        fun f(t: Double) = if (t > 0.008856) Math.cbrt(t) else 7.787 * t + 16.0 / 116
        val lightness = 116 * f(y) - 16
        val a = 500 * (f(x) - f(y))
        val bb = 200 * (f(y) - f(z))
        val hue = (Math.toDegrees(atan2(bb, a)) + 360) % 360
        return Triple(lightness.toFloat(), hypot(a, bb).toFloat(), hue.toFloat())
    }

    /** [coverColor]: the color that leads the cover, null if it has no colors */
    fun progressColor(coverColor: Int?): Int {
        if (coverColor == null) return NEUTRAL
        val (hue, saturation, _) = hsl(coverColor)
        if (saturation < MIN_SATURATION) return NEUTRAL
        return fromHsl(hue, SATURATION, LIGHTNESS)
    }

    /**
     * Veil of the Samsung style. Samsung blurs what is behind its widgets, so
     * their veil takes the colors of the wallpaper: here the main color of
     * the wallpaper, dark or light, stands in for the blur. The veil covers
     * 29% at opacity 0, 64% at 50 and 100% at 100 (measured on the Samsung
     * widgets), and gets plain black or white towards 100% as theirs.
     */
    fun veil(wallpaperColor: Int?, dark: Boolean, opacity: Int): Int {
        val level = opacity.coerceIn(0, 100) / 100f
        val alpha = VEIL_MIN + (1 - VEIL_MIN) * level
        val plain = if (dark) VEIL_DARK else VEIL_LIGHT
        val tinted = wallpaperColor?.let {
            val (hue, saturation, _) = hsl(it)
            if (dark) fromHsl(hue, min(saturation, 0.6f), 0.1f) else fromHsl(hue, min(saturation, 0.3f), 0.94f)
        } ?: plain
        val color = mix(tinted, plain, level)
        return ((alpha * 255).roundToInt() shl 24) or (color and 0xFFFFFF)
    }

    /** [from] moved towards [to] by [amount] (0-1) */
    fun mix(from: Int, to: Int, amount: Float): Int {
        fun channel(shift: Int): Int {
            val a = (from shr shift) and 0xFF
            val b = (to shr shift) and 0xFF
            return (a + (b - a) * amount).roundToInt().coerceIn(0, 255)
        }
        return (0xFF shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    private const val VEIL_MIN = 0.29f
    private const val VEIL_DARK = 0xFF010101.toInt()
    private const val VEIL_LIGHT = 0xFFFCFCFE.toInt()

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
