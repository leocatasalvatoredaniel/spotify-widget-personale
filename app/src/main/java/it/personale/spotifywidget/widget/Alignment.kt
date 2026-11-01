package it.personale.spotifywidget.widget

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * How One UI places the wallpaper on the screen, compared with the image the
 * app reads. The image is laid over the screen filling it and centered; One
 * UI then zooms it about the center of the screen and moves it (e.g. as the
 * photo was framed when it was set as wallpaper). Measured on a Galaxy S24
 * Ultra: zoom 1.20 and a shift of about 130 px down, which put the blur
 * behind the widgets out of line with the wallpaper around them.
 *
 * Android does not tell the framing to apps, so it is found on a screenshot
 * of a page of the Home screen without widgets: the zoom and shift that make
 * the image of the wallpaper look most like the screenshot. Large widgets
 * would mislead the search (their veils are dark shapes the wallpaper does
 * not have); small things such as icons are left out after a first search.
 */
data class Alignment(
    val zoom: Float,
    /** Shift as a fraction of the width of the screen */
    val shiftX: Float,
    val shiftY: Float,
) {
    /**
     * Point of the image under the point ([x], [y]) of a screen [width] x
     * [height], for an image [imageWidth] x [imageHeight]
     */
    fun imagePoint(x: Float, y: Float, width: Int, height: Int, imageWidth: Int, imageHeight: Int): Pair<Float, Float> {
        val fit = max(width.toFloat() / imageWidth, height.toFloat() / imageHeight)
        val left = (width - imageWidth * fit) / 2
        val top = (height - imageHeight * fit) / 2
        val cx = width / 2f
        val cy = height / 2f
        val baseX = cx + (x - cx - shiftX * width) / zoom
        val baseY = cy + (y - cy - shiftY * width) / zoom
        return (baseX - left) / fit to (baseY - top) / fit
    }

    /** Result of [find]: the alignment and how alike image and screenshot are, before and after (correlation) */
    data class Found(val alignment: Alignment, val match: Float, val matchBefore: Float)

    companion object {
        /** Filling the screen and centered, as Android shows a plain wallpaper */
        val NONE = Alignment(1f, 0f, 0f)

        /**
         * Finds the alignment of the [image] (luminance, [imageWidth] x
         * [imageHeight]) on the [screenshot] (luminance, [width] x [height]),
         * using only the points in [use] (e.g. not the widgets of the app)
         */
        fun find(
            screenshot: FloatArray,
            width: Int,
            height: Int,
            image: FloatArray,
            imageWidth: Int,
            imageHeight: Int,
            use: BooleanArray,
        ): Found {
            val first = search(screenshot, width, height, image, imageWidth, imageHeight, use)
            // Points far from the wallpaper (icons, text) are left out, then searched again
            val kept = withoutOutliers(first, screenshot, width, height, image, imageWidth, imageHeight, use)
            val second = search(screenshot, width, height, image, imageWidth, imageHeight, kept)
            val best = if (match(second, screenshot, width, height, image, imageWidth, imageHeight, kept, 1) >=
                match(first, screenshot, width, height, image, imageWidth, imageHeight, kept, 1)
            ) second else first
            return Found(
                best,
                match(best, screenshot, width, height, image, imageWidth, imageHeight, kept, 1),
                match(NONE, screenshot, width, height, image, imageWidth, imageHeight, kept, 1),
            )
        }

        /** Points that, with the [alignment], do not stray from the wallpaper more than most */
        private fun withoutOutliers(
            alignment: Alignment,
            screenshot: FloatArray,
            width: Int,
            height: Int,
            image: FloatArray,
            imageWidth: Int,
            imageHeight: Int,
            use: BooleanArray,
        ): BooleanArray {
            // Brightness of the screenshot as a linear function of the image, fitted on all points
            val points = mutableListOf<Int>()
            val values = mutableListOf<Pair<Double, Double>>()
            val fit = max(width.toFloat() / imageWidth, height.toFloat() / imageHeight)
            val scale = 1 / (alignment.zoom * fit)
            val (u0, v0) = alignment.imagePoint(0f, 0f, width, height, imageWidth, imageHeight)
            for (i in use.indices) {
                if (!use[i]) continue
                val u = u0 + (i % width) * scale
                val v = v0 + (i / width) * scale
                if (u < 0 || v < 0 || u > imageWidth - 1 || v > imageHeight - 1) continue
                points += i
                values += screenshot[i].toDouble() to sample(image, imageWidth, imageHeight, u, v).toDouble()
            }
            if (points.size < MIN_POINTS) return use
            val n = values.size
            val ma = values.sumOf { it.first } / n
            val mb = values.sumOf { it.second } / n
            val cov = values.sumOf { (it.first - ma) * (it.second - mb) }
            val vb = values.sumOf { (it.second - mb) * (it.second - mb) }
            val slope = if (vb > 0) cov / vb else 0.0
            val residuals = values.map { abs(it.first - (ma + slope * (it.second - mb))) }
            val limit = residuals.sorted()[(n * KEPT).toInt().coerceAtMost(n - 1)]
            val kept = BooleanArray(use.size)
            points.forEachIndexed { index, point -> kept[point] = residuals[index] <= limit }
            return kept
        }

        private fun search(
            screenshot: FloatArray,
            width: Int,
            height: Int,
            image: FloatArray,
            imageWidth: Int,
            imageHeight: Int,
            use: BooleanArray,
        ): Alignment {
            fun score(alignment: Alignment, step: Int): Float =
                match(alignment, screenshot, width, height, image, imageWidth, imageHeight, use, step)
            // Coarse, then fine around the best
            var best = NONE
            var bestScore = -2f
            var zoom = MIN_ZOOM
            while (zoom <= MAX_ZOOM + 1e-4f) {
                var sx = -MAX_SHIFT
                while (sx <= MAX_SHIFT + 1e-4f) {
                    var sy = -MAX_SHIFT
                    while (sy <= MAX_SHIFT + 1e-4f) {
                        val candidate = Alignment(zoom, sx, sy)
                        val s = score(candidate, COARSE_STEP)
                        if (s > bestScore) {
                            bestScore = s
                            best = candidate
                        }
                        sy += COARSE_SHIFT
                    }
                    sx += COARSE_SHIFT
                }
                zoom += COARSE_ZOOM
            }
            val coarse = best
            val pixel = 1f / width
            for (dz in -4..4) {
                for (dx in -6..6) {
                    for (dy in -6..6) {
                        val candidate = Alignment(
                            coarse.zoom + dz * FINE_ZOOM,
                            coarse.shiftX + dx * pixel,
                            coarse.shiftY + dy * pixel,
                        )
                        val s = score(candidate, FINE_STEP)
                        if (s > bestScore) {
                            bestScore = s
                            best = candidate
                        }
                    }
                }
            }
            return best
        }

        /** Correlation between the [screenshot] and the [image] placed with the [alignment], every [step] points */
        fun match(
            alignment: Alignment,
            screenshot: FloatArray,
            width: Int,
            height: Int,
            image: FloatArray,
            imageWidth: Int,
            imageHeight: Int,
            use: BooleanArray,
            step: Int,
        ): Float {
            // The point of the image is linear in the point of the screen
            val fit = max(width.toFloat() / imageWidth, height.toFloat() / imageHeight)
            val scale = 1 / (alignment.zoom * fit)
            val (u0, v0) = alignment.imagePoint(0f, 0f, width, height, imageWidth, imageHeight)
            var n = 0
            var sa = 0.0
            var sb = 0.0
            var saa = 0.0
            var sbb = 0.0
            var sab = 0.0
            var y = 0
            while (y < height) {
                var x = 0
                while (x < width) {
                    val i = y * width + x
                    if (use[i]) {
                        val u = u0 + x * scale
                        val v = v0 + y * scale
                        if (u >= 0 && v >= 0 && u <= imageWidth - 1 && v <= imageHeight - 1) {
                            val a = screenshot[i].toDouble()
                            val b = sample(image, imageWidth, imageHeight, u, v).toDouble()
                            n++
                            sa += a
                            sb += b
                            saa += a * a
                            sbb += b * b
                            sab += a * b
                        }
                    }
                    x += step
                }
                y += step
            }
            // Too little of the screen covered: not a candidate
            if (n < MIN_POINTS / (step * step)) return -1f
            val cov = sab - sa * sb / n
            val va = saa - sa * sa / n
            val vb = sbb - sb * sb / n
            if (va <= 0 || vb <= 0) return -1f
            return (cov / sqrt(va * vb)).toFloat()
        }

        private fun sample(image: FloatArray, width: Int, height: Int, u: Float, v: Float): Float {
            val x0 = u.toInt().coerceIn(0, width - 1)
            val y0 = v.toInt().coerceIn(0, height - 1)
            val x1 = (x0 + 1).coerceAtMost(width - 1)
            val y1 = (y0 + 1).coerceAtMost(height - 1)
            val fx = u - x0
            val fy = v - y0
            val top = image[y0 * width + x0] * (1 - fx) + image[y0 * width + x1] * fx
            val bottom = image[y1 * width + x0] * (1 - fx) + image[y1 * width + x1] * fx
            return top * (1 - fy) + bottom * fy
        }

        fun parse(text: String): Alignment? {
            val parts = text.trim().split(",").map { it.toFloatOrNull() ?: return null }
            if (parts.size < 3 || parts[0] <= 0) return null
            return Alignment(parts[0], parts[1], parts[2])
        }

        private const val MIN_ZOOM = 0.9f
        private const val MAX_ZOOM = 1.5f
        private const val COARSE_ZOOM = 0.04f
        private const val FINE_ZOOM = 0.01f
        /** Shift tried up to a quarter of the width of the screen */
        private const val MAX_SHIFT = 0.25f
        private const val COARSE_SHIFT = 0.025f
        private const val COARSE_STEP = 3
        private const val FINE_STEP = 2
        private const val MIN_POINTS = 4000
        /** Share of the points kept after the first search */
        private const val KEPT = 0.8
    }

    /** As kept in the file shared with the other widgets of the phone */
    fun format(): String = listOf(zoom, shiftX, shiftY).joinToString(",") { (it * 10000).roundToInt().div(10000f).toString() }
}
