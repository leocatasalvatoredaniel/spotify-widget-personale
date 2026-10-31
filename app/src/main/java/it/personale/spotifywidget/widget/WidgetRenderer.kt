package it.personale.spotifywidget.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import it.personale.spotifywidget.R
import it.personale.spotifywidget.app
import it.personale.spotifywidget.data.ImageCache
import it.personale.spotifywidget.data.NowPlaying
import it.personale.spotifywidget.data.PROGRESS_MAX
import it.personale.spotifywidget.data.WidgetState
import it.personale.spotifywidget.data.formatTime
import it.personale.spotifywidget.data.progressOf
import it.personale.spotifywidget.playback.Playback
import it.personale.spotifywidget.ui.LaunchSpotifyActivity
import it.personale.spotifywidget.ui.MainActivity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.roundToInt

/**
 * Draws the widget as the media player of the system: the cover fills the
 * background, with the output device, title • artist, the progress and the
 * controls. Spotify's own buttons (shuffle, add to library) keep its icons.
 */
object WidgetRenderer {
    private val mutex = Mutex()

    /** The widgets were drawn whole at least once since the app started */
    @Volatile
    private var drawn = false

    fun component(context: Context) = ComponentName(context, SpotifyWidgetReceiver::class.java)

    suspend fun renderAll(context: Context) = render(context, null)

    /** Draws the widgets [ids], or all of them */
    suspend fun render(context: Context, ids: IntArray?) = mutex.withLock {
        val manager = AppWidgetManager.getInstance(context)
        val targets = ids ?: manager.getAppWidgetIds(component(context))
        if (targets.isEmpty()) return@withLock
        val data = WidgetData.load(context)
        targets.forEach { id ->
            manager.updateAppWidget(id, build(context, data, manager.getAppWidgetOptions(id)))
        }
        drawn = true
    }

    /** Moves only the progress bar and the time, once a second while playing */
    fun renderProgress(context: Context, position: Long, duration: Long) {
        if (!drawn) return
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(component(context))
        if (ids.isEmpty()) return
        val views = RemoteViews(context.packageName, R.layout.widget_player).apply {
            setProgressBar(R.id.progress, PROGRESS_MAX, progressOf(position, duration), false)
            setTextViewText(R.id.elapsed, formatTime(position))
        }
        manager.partiallyUpdateAppWidget(ids, views)
    }

    private fun build(context: Context, data: WidgetData, options: Bundle): RemoteViews {
        if (!data.hasAccess) {
            return message(context, R.string.widget_no_access, activity(context, REQUEST_APP, appIntent(context)))
        }
        val nowPlaying = data.state.nowPlaying
            ?: return message(context, R.string.widget_open_spotify, openSpotify(context))
        return player(context, data, nowPlaying, options)
    }

    private fun player(context: Context, data: WidgetData, nowPlaying: NowPlaying, options: Bundle): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_player)

        // Sizes in portrait, where the home screen of a phone is used
        val widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
        val density = context.resources.displayMetrics.density
        val metrics = PlayerMetrics.forSize(widthDp, heightDp)
        views.applyMetrics(metrics, density)

        val background = data.cover?.let {
            PlayerBackground.render(
                it,
                ((widthDp.takeIf { w -> w > 0 } ?: DEFAULT_WIDTH_DP) * density).toInt(),
                ((heightDp.takeIf { h -> h > 0 } ?: DEFAULT_HEIGHT_DP) * density).toInt(),
            )
        }
        if (background != null) views.setImageViewBitmap(R.id.art, background)
        else views.setImageViewResource(R.id.art, 0)
        views.setOnClickPendingIntent(R.id.content, openSpotify(context))

        views.setTextViewText(R.id.device, data.device)
        views.setContentDescription(R.id.output, context.getString(R.string.output_description, data.device))
        views.setOnClickPendingIntent(R.id.output, outputSwitcher(context, data.output))

        views.setTextViewText(
            R.id.title,
            if (nowPlaying.artist.isBlank()) nowPlaying.title else "${nowPlaying.title} • ${nowPlaying.artist}",
        )
        val appIcon = data.state.appIcon
        if (appIcon != null) {
            views.setImageViewIcon(R.id.app_icon, Icon.createWithResource(Playback.SPOTIFY_PACKAGE, appIcon))
        }
        views.setViewVisibility(R.id.app_icon, if (appIcon != null) View.VISIBLE else View.GONE)

        val position = nowPlaying.positionAt(System.currentTimeMillis())
        views.setProgressBar(R.id.progress, PROGRESS_MAX, progressOf(position, nowPlaying.durationMs), false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            views.setColorStateList(
                R.id.progress,
                "setProgressTintList",
                ColorStateList.valueOf(nowPlaying.accent ?: WidgetColors.WHITE),
            )
        }
        views.setViewVisibility(
            R.id.times,
            if (!metrics.showTimes || nowPlaying.durationMs <= 0) View.GONE else View.VISIBLE,
        )
        views.setTextViewText(R.id.elapsed, formatTime(position))
        views.setTextViewText(R.id.duration, formatTime(nowPlaying.durationMs))

        val playing = nowPlaying.isPlaying
        views.setImageViewResource(R.id.play_pause, if (playing) R.drawable.ic_pause else R.drawable.ic_play)
        views.setContentDescription(
            R.id.play_pause,
            context.getString(if (playing) R.string.action_pause else R.string.action_play),
        )
        if (data.sessionActive) {
            views.setOnClickPendingIntent(R.id.play_pause, broadcast(context, ACTION_PLAY_PAUSE, REQUEST_PLAY_PAUSE))
            views.setOnClickPendingIntent(R.id.previous, broadcast(context, ACTION_PREVIOUS, REQUEST_PREVIOUS))
            views.setOnClickPendingIntent(R.id.next, broadcast(context, ACTION_NEXT, REQUEST_NEXT))
        } else {
            // Spotify closed: play opens it and starts the music when it is ready
            views.setOnClickPendingIntent(
                R.id.play_pause,
                activity(context, REQUEST_LAUNCH_AND_PLAY, LaunchSpotifyActivity.intent(context)),
            )
        }
        val skip = if (data.sessionActive) View.VISIBLE else View.INVISIBLE
        views.setViewVisibility(R.id.previous, skip)
        views.setViewVisibility(R.id.next, skip)

        // Spotify's buttons at the sides, as in the system player
        listOf(R.id.action_start, R.id.action_end).forEachIndexed { index, id ->
            val action = nowPlaying.actions.getOrNull(index)?.takeIf { data.sessionActive }
            if (action == null) {
                views.setViewVisibility(id, View.INVISIBLE)
                return@forEachIndexed
            }
            views.setViewVisibility(id, View.VISIBLE)
            views.setImageViewIcon(id, Icon.createWithResource(Playback.SPOTIFY_PACKAGE, action.icon))
            views.setContentDescription(id, action.name)
            views.setOnClickPendingIntent(
                id,
                broadcast(context, ACTION_CUSTOM, REQUEST_CUSTOM + index, action.action),
            )
        }
        return views
    }

    private fun message(context: Context, text: Int, click: PendingIntent): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_message).apply {
            setTextViewText(R.id.message, context.getString(text))
            setOnClickPendingIntent(android.R.id.background, click)
        }

    private fun outputSwitcher(context: Context, output: Playback.OutputSwitcher): PendingIntent =
        when (output) {
            is Playback.OutputSwitcher.Broadcast -> PendingIntent.getBroadcast(
                context,
                REQUEST_OUTPUT,
                output.intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            is Playback.OutputSwitcher.Activity ->
                activity(context, REQUEST_OUTPUT, output.intent ?: appIntent(context))
        }

    private fun openSpotify(context: Context): PendingIntent =
        activity(context, REQUEST_OPEN, Playback.spotifyLaunchIntent(context) ?: appIntent(context))

    private fun appIntent(context: Context) =
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun activity(context: Context, request: Int, intent: Intent): PendingIntent =
        PendingIntent.getActivity(
            context,
            request,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun broadcast(context: Context, action: String, request: Int, customAction: String? = null): PendingIntent {
        val intent = Intent(context, SpotifyWidgetReceiver::class.java).setAction(action)
        customAction?.let { intent.putExtra(EXTRA_CUSTOM_ACTION, it) }
        return PendingIntent.getBroadcast(
            context,
            request,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    const val ACTION_PLAY_PAUSE = "it.personale.spotifywidget.PLAY_PAUSE"
    const val ACTION_PREVIOUS = "it.personale.spotifywidget.PREVIOUS"
    const val ACTION_NEXT = "it.personale.spotifywidget.NEXT"
    const val ACTION_CUSTOM = "it.personale.spotifywidget.CUSTOM_ACTION"
    const val EXTRA_CUSTOM_ACTION = "custom_action"

    // Size of the player when the launcher does not report it
    private const val DEFAULT_WIDTH_DP = 360
    private const val DEFAULT_HEIGHT_DP = 160

    private const val REQUEST_OPEN = 1
    private const val REQUEST_APP = 2
    private const val REQUEST_OUTPUT = 3
    private const val REQUEST_PLAY_PAUSE = 4
    private const val REQUEST_PREVIOUS = 5
    private const val REQUEST_NEXT = 6
    private const val REQUEST_LAUNCH_AND_PLAY = 7
    private const val REQUEST_CUSTOM = 10
}

/**
 * Scales the player to the size of the widget: text everywhere, sizes and
 * margins from Android 12, where widgets can change them
 */
internal fun RemoteViews.applyMetrics(metrics: PlayerMetrics, density: Float) {
    val scale = metrics.scale
    fun px(dp: Float) = (dp * scale * density).roundToInt()

    setViewVisibility(R.id.output, if (metrics.showOutput) View.VISIBLE else View.GONE)
    // The output button stays at the end even without the name of the device
    setViewVisibility(R.id.device, if (metrics.showDeviceName) View.VISIBLE else View.INVISIBLE)
    val actions = if (metrics.showActions) View.VISIBLE else View.GONE
    setViewVisibility(R.id.cell_start, actions)
    setViewVisibility(R.id.cell_end, actions)

    setViewPadding(R.id.content, px(24f), px(14f), px(20f), px(8f))
    setTextViewTextSize(R.id.device, TypedValue.COMPLEX_UNIT_SP, 12f * scale)
    setTextViewTextSize(R.id.title, TypedValue.COMPLEX_UNIT_SP, 15f * scale)
    setTextViewTextSize(R.id.elapsed, TypedValue.COMPLEX_UNIT_SP, 12f * scale)
    setTextViewTextSize(R.id.duration, TypedValue.COMPLEX_UNIT_SP, 12f * scale)
    BUTTONS.forEach { (id, padding) ->
        val inset = px(padding)
        setViewPadding(id, inset, inset, inset, inset)
    }
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return

    val dip = TypedValue.COMPLEX_UNIT_DIP
    setViewLayoutWidth(R.id.output_icon, 24f * scale, dip)
    setViewLayoutHeight(R.id.output_icon, 24f * scale, dip)
    setViewLayoutMargin(R.id.title_row, RemoteViews.MARGIN_TOP, 4f * scale, dip)
    setViewLayoutWidth(R.id.app_icon, 16f * scale, dip)
    setViewLayoutHeight(R.id.app_icon, 16f * scale, dip)
    setViewLayoutMargin(R.id.app_icon, RemoteViews.MARGIN_END, 6f * scale, dip)
    setViewLayoutHeight(R.id.progress, 4f * scale, dip)
    setViewLayoutMargin(R.id.times, RemoteViews.MARGIN_TOP, 6f * scale, dip)
    setViewLayoutHeight(R.id.controls, 48f * scale, dip)
    setViewLayoutMargin(R.id.controls, RemoteViews.MARGIN_START, 12f * scale, dip)
    setViewLayoutMargin(R.id.controls, RemoteViews.MARGIN_END, 16f * scale, dip)
    BUTTONS.forEach { (id, _) ->
        setViewLayoutWidth(id, 48f * scale, dip)
        setViewLayoutHeight(id, 48f * scale, dip)
    }
}

// Buttons of the controls and the padding (dp) around their icon
private val BUTTONS = listOf(
    R.id.action_start to 12f,
    R.id.previous to 13f,
    R.id.play_pause to 12f,
    R.id.next to 13f,
    R.id.action_end to 12f,
)

/** What a drawing of the widget needs, read once for all the widgets */
private class WidgetData(
    val state: WidgetState,
    val hasAccess: Boolean,
    /** Spotify has a media session: the buttons can control it */
    val sessionActive: Boolean,
    val cover: Bitmap?,
    val device: String,
    val output: Playback.OutputSwitcher,
) {
    companion object {
        suspend fun load(context: Context): WidgetData {
            val state = context.app.store.read()
            val controller = Playback.spotifyController(context)
            return WidgetData(
                state = state,
                hasAccess = Playback.hasAccess(context),
                sessionActive = controller != null,
                cover = ImageCache.load(state.nowPlaying?.artPath),
                device = Playback.outputName(context, controller),
                output = Playback.outputSwitcher(context),
            )
        }
    }
}
