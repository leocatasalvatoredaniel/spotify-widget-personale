package it.personale.spotifywidget.ui

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import it.personale.spotifywidget.R
import it.personale.spotifywidget.api.SpotifyPlaylist
import it.personale.spotifywidget.api.SpotifyUser
import it.personale.spotifywidget.api.bestFor
import it.personale.spotifywidget.app
import it.personale.spotifywidget.data.COVER_SIZE_PX
import it.personale.spotifywidget.data.MAX_FAVORITES
import it.personale.spotifywidget.data.PlaylistItem
import it.personale.spotifywidget.playback.Playback
import it.personale.spotifywidget.widget.RefreshWorker
import it.personale.spotifywidget.widget.SpotifyWidgetReceiver
import it.personale.spotifywidget.widget.updateWidgets
import kotlinx.coroutines.launch

/**
 * Settings: Spotify account, access to the media sessions, favorite
 * playlists. Also opened by the launcher when the widget is edited.
 */
class MainActivity : ComponentActivity() {
    /** Changes every time the screen comes back, e.g. from the system settings */
    private var resumes by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Opened to configure a widget: the settings are shared, accept at once
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
        AccountCard(resumes)
        PlaylistsCard(resumes)
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
private fun AccountCard(resumes: Int) {
    val context = LocalContext.current
    val app = context.app
    val scope = rememberCoroutineScope()
    var loggedIn by remember(resumes) { mutableStateOf(app.auth.isLoggedIn) }
    var user by remember { mutableStateOf<SpotifyUser?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(loggedIn, resumes) {
        if (!loggedIn) return@LaunchedEffect
        runCatching { app.api.me() }
            .onSuccess { user = it; error = null }
            .onFailure { error = it.message }
    }

    SettingsCard(stringResource(R.string.account_title)) {
        when {
            !app.auth.isConfigured ->
                Text(stringResource(R.string.account_missing_client_id), color = MaterialTheme.colorScheme.error)
            !loggedIn -> {
                Text(stringResource(R.string.account_logged_out), style = MaterialTheme.typography.bodyMedium)
                Button(onClick = {
                    CustomTabsIntent.Builder().build().launchUrl(context, app.auth.authorizationUri())
                }) { Text(stringResource(R.string.account_connect)) }
            }
            else -> {
                user?.let {
                    Text(stringResource(R.string.account_connected, it.displayName ?: it.id))
                    if (it.product != null && it.product != "premium") {
                        Text(stringResource(R.string.account_not_premium), color = MaterialTheme.colorScheme.error)
                    }
                }
                error?.let { Text(stringResource(R.string.account_error, it), color = MaterialTheme.colorScheme.error) }
                OutlinedButton(onClick = {
                    app.auth.logout()
                    loggedIn = false
                    user = null
                    scope.launch {
                        app.store.update { it.copy(recent = emptyList(), liked = null, likedUri = null) }
                        updateWidgets(context)
                    }
                }) { Text(stringResource(R.string.account_disconnect)) }
            }
        }
    }
}

@Composable
private fun PlaylistsCard(resumes: Int) {
    val context = LocalContext.current
    val app = context.app
    val scope = rememberCoroutineScope()
    var favorites by remember { mutableStateOf<List<PlaylistItem>>(emptyList()) }
    var playlists by remember { mutableStateOf<List<SpotifyPlaylist>?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(resumes) { favorites = app.store.read().favorites }
    if (!app.auth.isLoggedIn) return

    fun toggle(playlist: SpotifyPlaylist) {
        scope.launch {
            val selected = favorites.any { it.uri == playlist.uri }
            val updated = app.store.update { state ->
                val list = if (selected) {
                    state.favorites.filterNot { it.uri == playlist.uri }
                } else {
                    state.favorites + PlaylistItem(
                        uri = playlist.uri,
                        name = playlist.name,
                        imageUrl = playlist.images.bestFor(COVER_SIZE_PX),
                    )
                }
                state.copy(favorites = list.take(MAX_FAVORITES))
            }
            favorites = updated.favorites
            updateWidgets(context)
            // Covers are downloaded in the background
            RefreshWorker.refreshSoon(context, 0)
        }
    }

    SettingsCard(stringResource(R.string.playlists_title)) {
        Text(
            pluralStringResource(R.plurals.playlists_explanation, MAX_FAVORITES, MAX_FAVORITES),
            style = MaterialTheme.typography.bodyMedium,
        )
        favorites.forEach { favorite ->
            Text("• ${favorite.name}", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        val list = playlists
        when {
            loading -> Text(stringResource(R.string.playlists_loading))
            list == null -> Button(onClick = {
                loading = true
                scope.launch {
                    runCatching { app.api.myPlaylists() }
                        .onSuccess { playlists = it; error = null }
                        .onFailure { error = it.message }
                    loading = false
                }
            }) { Text(stringResource(R.string.playlists_load)) }
            list.isEmpty() -> Text(stringResource(R.string.playlists_empty))
            else -> list.forEach { playlist ->
                val checked = favorites.any { it.uri == playlist.uri }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = checked,
                        enabled = checked || favorites.size < MAX_FAVORITES,
                        onCheckedChange = { toggle(playlist) },
                    )
                    Text(playlist.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        error?.let { Text(stringResource(R.string.playlists_error, it), color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun WidgetCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val manager = AppWidgetManager.getInstance(context)
    SettingsCard(stringResource(R.string.widget_title)) {
        if (manager.isRequestPinAppWidgetSupported) {
            Button(onClick = {
                manager.requestPinAppWidget(
                    ComponentName(context, SpotifyWidgetReceiver::class.java),
                    null,
                    null,
                )
            }) { Text(stringResource(R.string.widget_add)) }
        }
        OutlinedButton(onClick = {
            RefreshWorker.refreshSoon(context, 0)
            scope.launch { updateWidgets(context) }
        }) { Text(stringResource(R.string.widget_refresh)) }
        Text(stringResource(R.string.widget_hint), style = MaterialTheme.typography.bodySmall)
    }
}
