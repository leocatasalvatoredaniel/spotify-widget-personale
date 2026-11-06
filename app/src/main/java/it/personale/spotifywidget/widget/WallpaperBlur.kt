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
import androidx.core.graphics.scale
import it.personale.spotifywidget.app
import it.personale.spotifywidget.data.CardPosition
import it.personale.spotifywidget.data.WidgetBackground
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

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

    /**
     * Photo chosen in the settings: the one used as wallpaper. Preferred to
     * the image the phone gives, which One UI may show framed otherwise.
     */
    fun pickedFile(context: Context) = File(context.filesDir, "wallpaper-picked")

    /**
     * Copy of the photo for the other widgets of the phone (the agenda of
     * PoliTo), next to the file of the alignment
     */
    private fun sharedFile() = File(Environment.getExternalStorageDirectory(), ".sfondo-widget.jpg")

    /** Keeps the photo at [uri] as the image to blur */
    fun savePicked(context: Context, uri: Uri): Boolean = runCatching {
        val file = pickedFile(context)
        val temporary = File(context.filesDir, "wallpaper-picked.tmp")
        context.contentResolver.openInputStream(uri)?.use { input ->
            temporary.outputStream().use { input.copyTo(it) }
        } ?: return false
        val saved = temporary.renameTo(file)
        if (saved && canRead()) runCatching { file.copyTo(sharedFile(), overwrite = true) }
        context.app.blurredScreen = null
        saved
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

    /** The image of the wallpaper as read, before it is placed on the screen and blurred */
    private class WallpaperImage(val key: String, val image: Bitmap, val source: Source, val live: Boolean)

    /**
     * The image of the wallpaper: the one of the phone, or else the photo
     * chosen in the settings; null when there is neither. [width] x [height]:
     * about the size it is needed.
     */
    private fun source(context: Context, width: Int, height: Int): WallpaperImage? {
        // A wallpaper drawn by an app is blurred on the Home screen too
        val live = Wallpaper.isLive(context)
        pickedFile(context).takeIf { it.exists() }?.let { picked ->
            // Chosen before the access to the files: shared now
            if (canRead() && !sharedFile().exists()) runCatching { picked.copyTo(sharedFile()) }
            decodePhoto(picked, width, height)?.let {
                return WallpaperImage("$PICKED_PREFIX${picked.lastModified()}", it, Source.PICKED, live)
            }
        }
        if (!canRead()) return null
        val manager = WallpaperManager.getInstance(context)
        val id = runCatching { manager.getWallpaperId(WallpaperManager.FLAG_SYSTEM) }.getOrDefault(-1)
        return readWallpaper(manager, live, width, height)?.let { WallpaperImage("system:$id", it, Source.SYSTEM, live) }
    }

    /** Key of the image [source] would read, without reading it */
    fun sourceKey(context: Context): String? {
        pickedFile(context).takeIf { it.exists() }?.let { return "$PICKED_PREFIX${it.lastModified()}" }
        if (canRead()) {
            val manager = WallpaperManager.getInstance(context)
            val id = runCatching { manager.getWallpaperId(WallpaperManager.FLAG_SYSTEM) }.getOrDefault(-1)
            return "system:$id"
        }
        return null
    }

    /**
     * The blurred wallpaper, kept until the wallpaper, its alignment or the
     * screen changes; null when there is no image of the wallpaper
     */
    fun screen(context: Context): Screen? {
        if (!supported()) return null
        val (width, height) = realScreen(context)?.let { (w, h) -> min(w, h) to max(w, h) } ?: return null
        val density = context.resources.displayMetrics.density
        val scale = width.toFloat() / WORK_WIDTH
        val smallHeight = (height / scale).roundToInt()
        val live = Wallpaper.isLive(context)
        // The cache is checked before reading the image, which takes a while
        sourceKey(context)?.let { imageKey ->
            val alignment = alignmentOf(imageKey)
            val key = "$imageKey:${alignment.format()}:${width}x$height:$density:$live"
            context.app.blurredScreen?.takeIf { it.key == key }?.let { return it }
        }
        val source = source(context, WORK_WIDTH, smallHeight) ?: return null
        val alignment = alignmentOf(source.key)
        val key = "${source.key}:${alignment.format()}:${width}x$height:$density:${source.live}"
        return blur(context, key, source, alignment, width, height)
    }

    /**
     * How the image [key] is placed on the screen. The photo chosen in the
     * settings is the one the Home screen shows, filling the screen as it is
     * (measured on a Galaxy S24 Ultra: the Samsung widgets blur exactly it);
     * the image the phone gives may be framed otherwise (see [Alignment]).
     */
    private fun alignmentOf(key: String): Alignment =
        if (key.startsWith(PICKED_PREFIX)) Alignment.NONE else WallpaperAlignment.read(key) ?: Alignment.NONE

    /** [source] on a screen [width] x [height], as One UI Home shows it, blurred */
    private fun blur(
        context: Context,
        key: String,
        source: WallpaperImage,
        alignment: Alignment,
        width: Int,
        height: Int,
    ): Screen {
        val wallpaper = source.image
        val density = context.resources.displayMetrics.density
        val scale = width.toFloat() / WORK_WIDTH
        val smallWidth = WORK_WIDTH
        val smallHeight = (height / scale).roundToInt()
        val small = createBitmap(smallWidth, smallHeight)
        // Filling the screen and centered, then as One UI frames it (see [Alignment])
        val fit = max(smallWidth.toFloat() / wallpaper.width, smallHeight.toFloat() / wallpaper.height)
        val matrix = Matrix().apply {
            setScale(fit, fit)
            postTranslate((smallWidth - wallpaper.width * fit) / 2, (smallHeight - wallpaper.height * fit) / 2)
            postScale(alignment.zoom, alignment.zoom, smallWidth / 2f, smallHeight / 2f)
            postTranslate(alignment.shiftX * smallWidth, alignment.shiftY * smallWidth)
        }
        Canvas(small).drawBitmap(wallpaper, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
        val pixels = IntArray(smallWidth * smallHeight)
        small.getPixels(pixels, 0, smallWidth, 0, 0, smallWidth, smallHeight)
        small.recycle()

        val blurred = BlurMath.gaussian(pixels, smallWidth, smallHeight, BlurMath.blurDp(source.live) * density / scale)
        return Screen(key, smallWidth, smallHeight, scale, blurred, width, BlurMath.average(pixels), source.source)
            .also { context.app.blurredScreen = it }
    }

    /**
     * Finds how One UI frames the wallpaper ([Alignment]) on the [screenshot]
     * of a page of the Home screen, and keeps it for this wallpaper. Null
     * when the screenshot is not of this screen, or there is no image of the
     * wallpaper.
     */
    fun align(context: Context, screenshot: Uri, widgets: Collection<CardPosition>): Alignment.Found? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
        val (width, height) = realScreen(context)?.let { (w, h) -> min(w, h) to max(w, h) } ?: return null
        val scale = width.toFloat() / WORK_WIDTH
        val smallHeight = (height / scale).roundToInt()
        val shot = runCatching {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, screenshot)) { decoder, info, _ ->
                // A screenshot of this screen, not cropped
                val ratio = info.size.height.toFloat() / info.size.width
                require(abs(ratio - height.toFloat() / width) < 0.02f * height / width)
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.setTargetSize(WORK_WIDTH, smallHeight)
            }
        }.getOrNull() ?: return null
        val source = source(context, WORK_WIDTH, smallHeight) ?: return null
        // The image the same size as the screenshot when it fills it
        val fit = max(WORK_WIDTH.toFloat() / source.image.width, smallHeight.toFloat() / source.image.height)
        val imageWidth = (source.image.width * fit).roundToInt().coerceAtLeast(1)
        val imageHeight = (source.image.height * fit).roundToInt().coerceAtLeast(1)
        val image = source.image.scale(imageWidth, imageHeight)
        val density = context.resources.displayMetrics.density
        // The screenshot has the blur of the Home screen: the image gets it too
        val homeBlur = if (source.live) BlurMath.HOME_BLUR_DP * density / scale else 0f
        val shotLight = lightness(shot, ALIGN_BLUR)
        val imageLight = lightness(image, sqrt(ALIGN_BLUR * ALIGN_BLUR + homeBlur * homeBlur))
        // Without the status bar, the dock and the widgets of this app
        val use = BooleanArray(WORK_WIDTH * smallHeight) { i ->
            val x = i % WORK_WIDTH
            val y = i / WORK_WIDTH
            y > smallHeight * 0.04f && y < smallHeight * 0.68f && widgets.none { widget ->
                val card = widget.onScreen(width)
                x * scale in card.left.toFloat()..card.right.toFloat() && y * scale in card.top.toFloat()..card.bottom.toFloat()
            }
        }
        val found = Alignment.find(shotLight, WORK_WIDTH, smallHeight, imageLight, imageWidth, imageHeight, use)
        // Not this wallpaper, or a page full of widgets
        if (found.match < MIN_MATCH) return found
        WallpaperAlignment.write(source.key, found.alignment)
        context.app.blurredScreen = null
        return found
    }

    /** Lightness of a [bitmap] (0-255), blurred by [sigma] pixels */
    private fun lightness(bitmap: Bitmap, sigma: Float): FloatArray {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val blurred = BlurMath.gaussian(pixels, bitmap.width, bitmap.height, sigma)
        return FloatArray(blurred.size) {
            val c = blurred[it]
            0.2126f * ((c shr 16) and 0xFF) + 0.7152f * ((c shr 8) and 0xFF) + 0.0722f * (c and 0xFF)
        }
    }

    /** The framing of the wallpaper was found on a screenshot */
    fun isAligned(context: Context): Boolean = sourceKey(context)?.let { WallpaperAlignment.read(it) } != null

    /** An alignment below this likeness of image and screenshot is not kept */
    const val MIN_MATCH = 0.85f
    private const val ALIGN_BLUR = 2f

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

    private const val PICKED_PREFIX = "picked:"
}
