package it.personale.spotifywidget.ui

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import it.personale.spotifywidget.widget.WidgetRenderer
import kotlinx.coroutines.launch

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
        OneUiFooter(
            if (font != null) stringResource(R.string.font_samsung, font.family, font.file)
            else stringResource(R.string.font_android),
        )
        if (samsungActivities != null && output.kind != Playback.OutputSwitcher.Kind.SAMSUNG) {
            OneUiFooter(
                stringResource(R.string.output_samsung_activities, samsungActivities.joinToString().ifEmpty { "–" }),
            )
        }

        BackgroundSection()

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
 * with the same «Opacità» and «Colori» as their settings
 */
@Composable
private fun BackgroundSection() {
    val context = LocalContext.current
    val app = context.app
    val scope = rememberCoroutineScope()
    var background by remember { mutableStateOf(WidgetBackground()) }
    LaunchedEffect(Unit) { background = app.store.read().background }

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
    }
    OneUiCard(rows)
    OneUiFooter(
        stringResource(if (background.cover) R.string.background_cover_footer else R.string.background_samsung_footer),
    )
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
