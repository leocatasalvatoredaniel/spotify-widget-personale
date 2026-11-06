package it.personale.spotifywidget.ui

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import it.personale.spotifywidget.R
import it.personale.spotifywidget.app
import it.personale.spotifywidget.data.WidgetBackground
import it.personale.spotifywidget.playback.Playback
import it.personale.spotifywidget.widget.SpotifyWidgetReceiver
import it.personale.spotifywidget.widget.FontReport
import it.personale.spotifywidget.widget.SystemFont
import it.personale.spotifywidget.widget.Wallpaper
import it.personale.spotifywidget.widget.WallpaperBlur
import it.personale.spotifywidget.widget.WidgetRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Settings, drawn as a page of One UI 9. Also opened by the launcher when
 * the widget is edited.
 */
class MainActivity : ComponentActivity() {
    /** Changes every time the screen comes back, e.g. from the system settings */
    private var resumes by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Opened to configure a widget: nothing to choose, accept at once
        val widgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        )
        if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        }
        setContent {
            OneUiTheme { SettingsScreen(resumes) }
        }
    }

    override fun onResume() {
        super.onResume()
        resumes++
        // The access may have just been granted: the widget can show the music
        lifecycleScope.launch { WidgetRenderer.renderAll(applicationContext) }
    }
}

@Composable
private fun SettingsScreen(resumes: Int) {
    val context = LocalContext.current
    val hasAccess = remember(resumes) { Playback.hasAccess(context) }
    val output = remember(resumes) { Playback.outputSwitcher(context) }
    val samsungActivities = remember(resumes) { Playback.samsungMediaActivities(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LocalOneUi.current.background)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        OneUiTitle(stringResource(R.string.settings_title))

        OneUiCard(
            listOf {
                OneUiRow(
                    title = stringResource(R.string.access_title),
                    checked = hasAccess,
                    onClick = { open(context, notificationAccessIntent(context)) },
                )
            },
        )
        OneUiFooter(stringResource(R.string.access_explanation))

        OneUiSectionHeader(stringResource(R.string.widget_title))
        OneUiCard(
            listOf(
                {
                    OneUiRow(
                        title = stringResource(R.string.widget_add),
                        summary = stringResource(R.string.widget_add_summary),
                        onClick = { pinWidget(context) },
                    )
                },
                {
                    OneUiRow(
                        title = stringResource(R.string.output_title),
                        summary = stringResource(
                            when (output.kind) {
                                Playback.OutputSwitcher.Kind.SAMSUNG -> R.string.output_samsung
                                Playback.OutputSwitcher.Kind.ANDROID -> R.string.output_android
                                Playback.OutputSwitcher.Kind.SPOTIFY -> R.string.output_spotify
                            },
                        ),
                        summaryIsValue = true,
                        onClick = { openOutputSwitcher(context, output) },
                    )
                },
            ),
        )
        val font = remember { context.app.systemFont }
        // Without the font, what the phone has: to find the name of the Samsung font
        val fontReport by produceState<FontReport?>(null) {
            if (font == null) value = withContext(Dispatchers.IO) { SystemFont.report() }
        }
        val report = fontReport
        OneUiFooter(
            when {
                font != null -> stringResource(R.string.font_samsung, font.family, font.file)
                report == null -> stringResource(R.string.font_android)
                else -> stringResource(
                    R.string.font_android_report,
                    report.lists.joinToString().ifEmpty { "–" },
                    report.samsungFiles.take(8).joinToString().ifEmpty { "–" },
                    report.family?.let { stringResource(R.string.font_not_loaded, it) }.orEmpty(),
                )
            },
        )
        // Whether the launcher leaves the title of the widget scrolling while the bar moves
        remember(resumes) { WidgetRenderer.titleSeparate }?.let { separate ->
            OneUiFooter(stringResource(if (separate) R.string.title_separate else R.string.title_redrawn))
        }
        if (samsungActivities != null && output.kind != Playback.OutputSwitcher.Kind.SAMSUNG) {
            OneUiFooter(
                stringResource(R.string.output_samsung_activities, samsungActivities.joinToString().ifEmpty { "–" }),
            )
        }

        BackgroundSection(resumes)

        OneUiSectionHeader(stringResource(R.string.spotify_title))
        OneUiCard(
            listOf {
                OneUiRow(
                    title = stringResource(R.string.liked_title),
                    summary = stringResource(R.string.liked_summary),
                    onClick = { Playback.likedSongsIntent(context)?.let { open(context, it) } },
                )
            },
        )
        Spacer(modifier = Modifier.height(32.dp))
    }
}

/**
 * Background of the widget: the cover, or the veil of the Samsung widgets
 * with the same «Opacità» and «Colori» as their settings, and their blur
 */
@Composable
private fun BackgroundSection(resumes: Int) {
    val context = LocalContext.current
    val app = context.app
    val scope = rememberCoroutineScope()
    var background by remember { mutableStateOf(WidgetBackground()) }
    LaunchedEffect(Unit) { background = app.store.read().background }
    val canRead = remember(resumes) { WallpaperBlur.canRead() }
    // What the blur can do now: reading the wallpaper takes a moment
    var blur by remember { mutableStateOf<BlurInfo?>(null) }
    var picked by remember { mutableIntStateOf(0) }
    LaunchedEffect(resumes, background, picked) {
        blur = if (background.cover || !background.blur) null else withContext(Dispatchers.IO) { blurInfo(context) }
    }
    // The photo of the wallpaper, when the phone does not give it
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val saved = withContext(Dispatchers.IO) { WallpaperBlur.savePicked(context, uri) }
            if (!saved) Toast.makeText(context, R.string.open_failed, Toast.LENGTH_SHORT).show()
            picked++
            WidgetRenderer.renderAll(context.applicationContext)
        }
    }

    // A screenshot of the Home screen: how One UI frames the wallpaper (see Alignment)
    val pickScreenshot = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        Toast.makeText(context, R.string.align_working, Toast.LENGTH_SHORT).show()
        scope.launch {
            val widgets = app.store.read().positions.values
            val found = withContext(Dispatchers.Default) { WallpaperBlur.align(context, uri, widgets) }
            val text = when {
                found == null -> context.getString(R.string.align_unreadable)
                found.match < WallpaperBlur.MIN_MATCH ->
                    context.getString(R.string.align_failed, (found.match * 100).roundToInt())
                else -> context.getString(
                    R.string.align_done,
                    (found.matchBefore * 100).roundToInt(),
                    (found.match * 100).roundToInt(),
                )
            }
            Toast.makeText(context, text, Toast.LENGTH_LONG).show()
            picked++
            WidgetRenderer.renderAll(context.applicationContext)
        }
    }

    fun change(updated: WidgetBackground) {
        background = updated
        scope.launch {
            app.store.update { it.copy(background = updated) }
            WidgetRenderer.renderAll(context.applicationContext)
        }
    }

    OneUiSectionHeader(stringResource(R.string.background_title))
    val rows = mutableListOf<@Composable () -> Unit>(
        {
            OneUiRow(
                title = stringResource(R.string.background_cover),
                checked = background.cover,
                onClick = { change(background.copy(cover = !background.cover)) },
            )
        },
    )
    if (!background.cover) {
        rows += {
            OneUiSliderRow(
                title = stringResource(R.string.background_opacity),
                stops = WidgetBackground.OPACITIES.size,
                value = WidgetBackground.OPACITIES.indexOf(background.opacity).coerceAtLeast(0),
                onChange = { change(background.copy(opacity = WidgetBackground.OPACITIES[it])) },
            )
        }
        rows += {
            OneUiRow(
                title = stringResource(R.string.background_colors),
                summary = stringResource(if (background.dark) R.string.background_dark else R.string.background_light),
                summaryIsValue = true,
                onClick = { change(background.copy(dark = !background.dark)) },
            )
        }
        if (WallpaperBlur.supported()) {
            rows += {
                OneUiRow(
                    title = stringResource(R.string.blur_title),
                    summary = stringResource(
                        if (background.blur && !canRead) R.string.blur_summary_access else R.string.blur_summary,
                    ),
                    checked = background.blur && canRead,
                    onClick = {
                        if (canRead) {
                            change(background.copy(blur = !background.blur))
                        } else {
                            // On again once the access is granted
                            change(background.copy(blur = true))
                            open(context, allFilesAccessIntent(context))
                        }
                    },
                )
            }
            val status = blur?.status
            // The photo is placed exactly as the Home screen shows it: offered
            // also when the phone gives its own image, which may be framed otherwise
            if (background.blur && status != null && status != WallpaperBlur.Status.NO_ACCESS &&
                status != WallpaperBlur.Status.NOT_SUPPORTED
            ) {
                rows += {
                    OneUiRow(
                        title = stringResource(R.string.wallpaper_image_title),
                        summary = stringResource(
                            if (status == WallpaperBlur.Status.PICKED) R.string.wallpaper_image_picked
                            else R.string.wallpaper_image_pick,
                        ),
                        summaryIsValue = status == WallpaperBlur.Status.PICKED,
                        onClick = {
                            pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                    )
                }
            }
            if (background.blur && status == WallpaperBlur.Status.READY) {
                rows += {
                    OneUiRow(
                        title = stringResource(R.string.align_title),
                        summary = stringResource(
                            if (blur?.aligned == true) R.string.align_summary_done else R.string.align_summary,
                        ),
                        onClick = {
                            pickScreenshot.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                    )
                }
            }
        }
    }
    OneUiCard(rows)
    if (background.cover) {
        OneUiFooter(stringResource(R.string.background_cover_footer))
    } else {
        // Whether the phone tells the color of its wallpaper, which tints the veil
        val wallpaper = remember(resumes) {
            when {
                Wallpaper.color(context) != null -> R.string.wallpaper_color_found
                Wallpaper.isLive(context) -> R.string.wallpaper_color_live
                // The image of the wallpaper gives its color
                canRead -> R.string.wallpaper_color_found
                else -> R.string.wallpaper_color_missing
            }
        }
        val info = blur
        val where = if (info?.positionsKnown == true) R.string.blur_moved else R.string.blur_tap
        val explanation = when {
            !background.blur -> stringResource(wallpaper)
            !canRead && info?.status != WallpaperBlur.Status.PICKED -> stringResource(R.string.blur_access)
            info == null -> null
            info.status == WallpaperBlur.Status.READY ->
                stringResource(R.string.blur_ready) + " " + stringResource(where)
            info.status == WallpaperBlur.Status.PICKED ->
                stringResource(R.string.blur_picked) + " " + stringResource(where)
            info.status == WallpaperBlur.Status.NO_IMAGE -> stringResource(
                R.string.blur_no_image,
                info.drawnBy?.let { (name, pkg) -> stringResource(R.string.blur_drawn_by, name, pkg) }.orEmpty(),
            )
            else -> null
        }
        OneUiFooter(listOfNotNull(stringResource(R.string.background_samsung_footer), explanation).joinToString(" "))
    }
}

/** Whether the widgets can blur the wallpaper, or what is missing */
private class BlurInfo(
    val status: WallpaperBlur.Status,
    /** A tap told where each widget is */
    val positionsKnown: Boolean,
    /** App that draws the wallpaper, when it is not a plain image */
    val drawnBy: Pair<String, String>?,
    /** How One UI frames the wallpaper was found on a screenshot */
    val aligned: Boolean,
)

private suspend fun blurInfo(context: Context): BlurInfo {
    val positions = context.app.store.read().positions
    val ids = AppWidgetManager.getInstance(context).getAppWidgetIds(WidgetRenderer.component(context))
    return BlurInfo(
        WallpaperBlur.status(context),
        ids.all { it in positions },
        WallpaperBlur.drawnBy(context),
        WallpaperBlur.isAligned(context),
    )
}

/** The page of the system where the app gets the access to all files */
private fun allFilesAccessIntent(context: Context): Intent {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return Intent(Settings.ACTION_SETTINGS)
    val app = Intent(
        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
        Uri.fromParts("package", context.packageName, null),
    )
    return if (app.resolveActivity(context.packageManager) != null) app
    else Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
}

private fun open(context: Context, intent: Intent) {
    try {
        context.startActivity(intent)
    } catch (error: ActivityNotFoundException) {
        Toast.makeText(context, R.string.open_failed, Toast.LENGTH_SHORT).show()
    }
}

private fun notificationAccessIntent(context: Context): Intent =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
            Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
            Playback.listenerComponent(context).flattenToString(),
        )
    } else {
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
    }

/** The launcher asks where to put the widget; a toast confirms it once added */
private fun pinWidget(context: Context) {
    val manager = AppWidgetManager.getInstance(context)
    // The launcher adds the id of the new widget to this intent: it must be mutable
    val added = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, SpotifyWidgetReceiver::class.java).setAction(SpotifyWidgetReceiver.ACTION_PINNED),
        PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
    val asked = manager.isRequestPinAppWidgetSupported &&
        manager.requestPinAppWidget(WidgetRenderer.component(context), null, added)
    if (!asked) Toast.makeText(context, R.string.widget_pin_unsupported, Toast.LENGTH_LONG).show()
}

/** The same panel as the output button of the widget, to try it from here */
private fun openOutputSwitcher(context: Context, output: Playback.OutputSwitcher) {
    when (output) {
        is Playback.OutputSwitcher.Activity -> output.intent?.let { open(context, it) }
        is Playback.OutputSwitcher.Broadcast -> context.sendBroadcast(output.intent)
    }
}
