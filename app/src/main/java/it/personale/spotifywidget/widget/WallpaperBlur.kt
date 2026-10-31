package it.personale.spotifywidget.widget

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.util.DisplayMetrics
import android.view.Display
import androidx.core.graphics.createBitmap
import it.personale.spotifywidget.app
import it.personale.spotifywidget.data.CardPosition
import it.personale.spotifywidget.data.WidgetBackground
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The wallpaper of the Home screen, blurred as the Samsung widgets show it
 * ([BlurMath]), cut at the place of each widget. Reading the wallpaper needs
 * the access to all files: from Android 13 the system gives the wallpaper
 * only to the apps that have it. When the phone does not give it (e.g. a
 * wallpaper drawn by an app), the photo chosen in the settings stands in.
 */
object WallpaperBlur {
    enum class Status {
        NOT_SUPPORTED,

        /** No access to all files and no photo chosen */
        NO_ACCESS,

        /** Blurring the wallpaper of the phone */
        READY,

        /** Blurring the photo chosen in the settings */
        PICKED,

        /** The phone gives no image of its wallpaper and no photo was chosen */
        NO_IMAGE,
    }

    enum class Source { SYSTEM, PICKED }

    /** The whole screen with the wallpaper blurred, smaller than the screen */
    class Screen(
        val key: String,
        val width: Int,
        val height: Int,
        /** Pixels of the screen in a pixel of [pixels] */
        val scale: Float,
        val pixels: IntArray,
        /** Width of the screen in pixels, in portrait */
        val screenWidth: Int,
        /** Average color of the wallpaper, for the veil without blur */
        val average: Int,
        val source: Source,
    )

    /** Rounded corners of the card, which cut the image, need Android 12 */
    fun supported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    fun canRead(): Boolean = supported() && Environment.isExternalStorageManager()

    fun status(context: Context): Status {
        if (!supported()) return Status.NOT_SUPPORTED
        if (!canRead() && !pickedFile(context).exists()) return Status.NO_ACCESS
        return when (screen(context)?.source) {
            Source.SYSTEM -> Status.READY
            Source.PICKED -> Status.PICKED
            null -> Status.NO_IMAGE
        }
    }

    /** Photo chosen in the settings, used when the phone gives no image of its wallpaper */
    fun pickedFile(context: Context) = File(context.filesDir, "wallpaper-picked")

    /** Keeps the photo at [uri] as the image to blur */
    fun savePicked(context: Context, uri: Uri): Boolean = runCatching {
        val file = pickedFile(context)
        val temporary = File(context.filesDir, "wallpaper-picked.tmp")
        context.contentResolver.openInputStream(uri)?.use { input ->
            temporary.outputStream().use { input.copyTo(it) }
        } ?: return false
        temporary.renameTo(file).also { context.app.blurredScreen = null }
    }.getOrDefault(false)

    /** Who draws the wallpaper when it is not a plain image: name and package */
    fun drawnBy(context: Context): Pair<String, String>? = runCatching {
        WallpaperManager.getInstance(context).wallpaperInfo?.let {
            it.loadLabel(context.packageManager).toString() to it.packageName
        }
    }.getOrNull()

    /** Size of the screen in portrait, in pixels; null when the phone is in landscape */
    fun portraitScreen(context: Context): Pair<Int, Int>? {
        val (width, height) = realScreen(context) ?: return null
        return if (width <= height) width to height else null
    }

    private fun realScreen(context: Context): Pair<Int, Int>? = runCatching {
        val display = context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        display.getRealMetrics(metrics)
        metrics.widthPixels to metrics.heightPixels
    }.getOrNull()

    /**
     * The blurred wallpaper, kept until the wallpaper or the screen changes:
     * the image of the phone, or else the photo chosen in the settings; null
     * when there is neither
     */
    fun screen(context: Context): Screen? {
        if (!supported()) return null
        val (width, height) = realScreen(context)?.let { (w, h) -> min(w, h) to max(w, h) } ?: return null
        val density = context.resources.displayMetrics.density
        val screen = "${width}x$height:$density"
        val scale = width.toFloat() / WORK_WIDTH
        val smallHeight = (height / scale).roundToInt()

        if (canRead()) {
            val manager = WallpaperManager.getInstance(context)
            val id = runCatching { manager.getWallpaperId(WallpaperManager.FLAG_SYSTEM) }.getOrDefault(-1)
            val key = "system:$id:$screen"
            cached(context, key)?.let { return it }
            readWallpaper(manager, Wallpaper.isLive(context), WORK_WIDTH, smallHeight)?.let {
                return blur(context, key, it, width, height, Source.SYSTEM)
            }
        }
        val picked = pickedFile(context).takeIf { it.exists() } ?: return null
        val key = "picked:${picked.lastModified()}:$screen"
        cached(context, key)?.let { return it }
        val photo = decodePhoto(picked, WORK_WIDTH, smallHeight) ?: return null
        return blur(context, key, photo, width, height, Source.PICKED)
    }

    private fun cached(context: Context, key: String) = context.app.blurredScreen?.takeIf { it.key == key }

    /** [wallpaper] on a screen [width] x [height], as One UI Home shows it (filling it, centered), blurred */
    private fun blur(context: Context, key: String, wallpaper: Bitmap, width: Int, height: Int, source: Source): Screen {
        val density = context.resources.displayMetrics.density
        val scale = width.toFloat() / WORK_WIDTH
        val smallWidth = WORK_WIDTH
        val smallHeight = (height / scale).roundToInt()
        val small = createBitmap(smallWidth, smallHeight)
        val fit = max(smallWidth.toFloat() / wallpaper.width, smallHeight.toFloat() / wallpaper.height)
        val matrix = Matrix().apply {
            setScale(fit, fit)
            postTranslate((smallWidth - wallpaper.width * fit) / 2, (smallHeight - wallpaper.height * fit) / 2)
        }
        Canvas(small).drawBitmap(wallpaper, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
        val pixels = IntArray(smallWidth * smallHeight)
        small.getPixels(pixels, 0, smallWidth, 0, 0, smallWidth, smallHeight)
        small.recycle()

        val blurred = BlurMath.gaussian(pixels, smallWidth, smallHeight, BlurMath.BLUR_DP * density / scale)
        return Screen(key, smallWidth, smallHeight, scale, blurred, width, BlurMath.average(pixels), source)
            .also { context.app.blurredScreen = it }
    }

    /** Background of the card at [position], as a Samsung widget with the [style] would show there */
    fun card(screen: Screen, position: CardPosition, style: WidgetBackground): Bitmap {
        val card = position.onScreen(screen.screenWidth)
        val left = (card.left / screen.scale).roundToInt()
        val top = (card.top / screen.scale).roundToInt()
        val width = (card.width / screen.scale).roundToInt().coerceAtLeast(1)
        val height = (card.height / screen.scale).roundToInt().coerceAtLeast(1)
        val veil = WidgetColors.blurVeil(style.dark, style.opacity)
        val pixels = IntArray(width * height) { i ->
            // Outside the screen (a card in the middle of a move) the edge repeats
            val x = (left + i % width).coerceIn(0, screen.width - 1)
            val y = (top + i / width).coerceIn(0, screen.height - 1)
            BlurMath.samsung(screen.pixels[y * screen.width + x], veil)
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    /**
     * The wallpaper, decoded at about the size it is needed. One UI can draw
     * a plain photo with an app of wallpapers ([live]): its image may still
     * be there.
     */
    private fun readWallpaper(manager: WallpaperManager, live: Boolean, width: Int, height: Int): Bitmap? {
        val fromFile = runCatching {
            manager.getWallpaperFile(WallpaperManager.FLAG_SYSTEM)?.let { file ->
                val bytes = ParcelFileDescriptor.AutoCloseInputStream(file).use { it.readBytes() }
                decode(bytes, width, height)
            }
        }.getOrNull()
        if (fromFile != null) return fromFile
        // Without a file a wallpaper drawn by an app has no image: the system
        // would give the wallpaper that comes with the phone instead
        if (live) return null
        // No file, e.g. the wallpaper that comes with the phone
        val drawable = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                manager.getDrawable(WallpaperManager.FLAG_SYSTEM)
            } else {
                manager.drawable
            }
        }.getOrNull() ?: return null
        return toBitmap(drawable, width, height)
    }

    private fun decode(bytes: ByteArray, width: Int, height: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= width && bounds.outHeight / (sample * 2) >= height) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    /** A photo of the gallery, turned as it was taken (ImageDecoder reads its orientation) */
    private fun decodePhoto(file: File, width: Int, height: Int): Bitmap? = runCatching {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            var sample = 1
            while (info.size.width / (sample * 2) >= width && info.size.height / (sample * 2) >= height) sample *= 2
            decoder.setTargetSampleSize(sample)
        }
    }.getOrNull()

    private fun toBitmap(drawable: Drawable, width: Int, height: Int): Bitmap? {
        (drawable as? BitmapDrawable)?.bitmap?.let { bitmap ->
            // A software canvas cannot draw a bitmap kept by the graphics card
            return if (bitmap.config == Bitmap.Config.HARDWARE) bitmap.copy(Bitmap.Config.ARGB_8888, false) else bitmap
        }
        val w = drawable.intrinsicWidth.takeIf { it > 0 } ?: width
        val h = drawable.intrinsicHeight.takeIf { it > 0 } ?: height
        return runCatching {
            createBitmap(w, h).also {
                drawable.setBounds(0, 0, w, h)
                drawable.draw(Canvas(it))
            }
        }.getOrNull()
    }

    /** Width of the blurred screen: the blur leaves no detail a larger image would keep */
    private const val WORK_WIDTH = 270
}
