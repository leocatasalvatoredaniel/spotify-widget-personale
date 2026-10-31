package it.personale.spotifywidget.ui

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import it.personale.spotifywidget.R
import it.personale.spotifywidget.playback.Playback
import it.personale.spotifywidget.widget.WidgetRenderer
import kotlinx.coroutines.launch

/**
 * Settings: access to the media sessions and the widget. Also opened by the
 * launcher when the widget is edited.
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
            MaterialTheme(colorScheme = colorScheme(this, isSystemInDarkTheme())) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SettingsScreen(resumes)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumes++
        // The access may have just been granted: the widget can show the music
        lifecycleScope.launch { WidgetRenderer.renderAll(applicationContext) }
    }
}

private fun colorScheme(context: Context, dark: Boolean): ColorScheme = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    dark -> darkColorScheme()
    else -> lightColorScheme()
}

@Composable
private fun SettingsScreen(resumes: Int) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.settings_intro), style = MaterialTheme.typography.bodyMedium)
        AccessCard(resumes)
        WidgetCard()
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun AccessCard(resumes: Int) {
    val context = LocalContext.current
    val hasAccess = remember(resumes) { Playback.hasAccess(context) }
    SettingsCard(stringResource(R.string.access_title)) {
        Text(stringResource(R.string.access_explanation), style = MaterialTheme.typography.bodyMedium)
        if (hasAccess) {
            Text(stringResource(R.string.access_granted), color = MaterialTheme.colorScheme.primary)
        } else {
            Button(onClick = { context.startActivity(notificationAccessIntent(context)) }) {
                Text(stringResource(R.string.access_grant))
            }
        }
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

@Composable
private fun WidgetCard() {
    val context = LocalContext.current
    val manager = AppWidgetManager.getInstance(context)
    SettingsCard(stringResource(R.string.widget_title)) {
        if (manager.isRequestPinAppWidgetSupported) {
            Button(onClick = {
                manager.requestPinAppWidget(WidgetRenderer.component(context), null, null)
            }) { Text(stringResource(R.string.widget_add)) }
        }
        Text(stringResource(R.string.widget_hint), style = MaterialTheme.typography.bodySmall)
        OutputDiagnostics()
    }
}

/** Which panel the output button opens on this phone, to report it if it is wrong */
@Composable
private fun OutputDiagnostics() {
    val context = LocalContext.current
    val kind = remember { Playback.outputSwitcher(context).kind }
    val samsung = remember { Playback.samsungMediaActivities(context) }
    val target = stringResource(
        when (kind) {
            Playback.OutputSwitcher.Kind.SAMSUNG -> R.string.output_samsung
            Playback.OutputSwitcher.Kind.ANDROID -> R.string.output_android
            Playback.OutputSwitcher.Kind.SPOTIFY -> R.string.output_spotify
        },
    )
    Text(stringResource(R.string.output_target, target), style = MaterialTheme.typography.bodySmall)
    if (samsung != null && kind != Playback.OutputSwitcher.Kind.SAMSUNG) {
        Text(
            stringResource(R.string.output_samsung_activities, samsung.joinToString().ifEmpty { "–" }),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
