package it.personale.spotifywidget.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import it.personale.spotifywidget.R
import it.personale.spotifywidget.app
import it.personale.spotifywidget.data.ImageCache
import it.personale.spotifywidget.data.WidgetState
import it.personale.spotifywidget.playback.Playback
import it.personale.spotifywidget.ui.MainActivity
import it.personale.spotifywidget.ui.PlayActivity

/**
 * Data of a widget drawing, with the covers already loaded
 */
class WidgetModel(
    val state: WidgetState,
    val loggedIn: Boolean,
    val hasAccess: Boolean,
    /** Spotify has a media session: the buttons can control it */
    val sessionActive: Boolean,
    val cover: Bitmap?,
    val favoriteCovers: List<Bitmap?>,
    val recentCovers: List<Bitmap?>,
    val colors: WidgetColors,
) {
    companion object {
        suspend fun load(context: Context): WidgetModel {
            val app = context.app
            val state = app.store.read()
            return WidgetModel(
                state = state,
                loggedIn = app.auth.isLoggedIn,
                hasAccess = Playback.hasAccess(context),
                sessionActive = Playback.spotifyController(context) != null,
                cover = ImageCache.load(state.nowPlaying?.artPath),
                favoriteCovers = state.favorites.map { ImageCache.load(it.imagePath) },
                recentCovers = state.recent.map { ImageCache.load(it.imagePath) },
                colors = WidgetColors.fromCover(state.nowPlaying?.color),
            )
        }
    }
}

/**
 * Spotify widget: cover, title and buttons; in the large size also the
 * favorite playlists and the recently played tracks
 */
class SpotifyWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(SMALL, WIDE, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val model = WidgetModel.load(context)
        provideContent { WidgetContent(model) }
    }

    companion object {
        val SMALL = DpSize(110.dp, 110.dp)
        val WIDE = DpSize(250.dp, 110.dp)
        val LARGE = DpSize(250.dp, 260.dp)
    }
}

private const val LIKED_COLOR = 0xFF1ED760.toInt()

@Composable
private fun WidgetContent(model: WidgetModel) {
    val size = LocalSize.current
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(color(model.colors.background))
            .cornerRadius(20.dp)
            .padding(12.dp),
    ) {
        when {
            !model.hasAccess -> Message(model, R.string.widget_no_access, openAppAction())
            size.width < SpotifyWidget.WIDE.width -> SmallLayout(model)
            size.height < SpotifyWidget.LARGE.height -> WideLayout(model)
            else -> LargeLayout(model)
        }
    }
}

@Composable
private fun SmallLayout(model: WidgetModel) {
    val nowPlaying = model.state.nowPlaying
    if (nowPlaying == null) {
        Message(model, R.string.widget_nothing_playing, openSpotifyAction())
        return
    }
    Box(modifier = GlanceModifier.fillMaxSize()) {
        Cover(
            bitmap = model.cover,
            size = null,
            model = model,
            modifier = GlanceModifier.fillMaxSize().clickable(openSpotifyAction()),
        )
        Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
            PlayPauseButton(model, 48.dp)
        }
    }
}

@Composable
private fun WideLayout(model: WidgetModel) {
    val coverSize = minOf(LocalSize.current.height - 24.dp, 104.dp)
    Row(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Cover(
            bitmap = model.cover,
            size = coverSize,
            model = model,
            modifier = GlanceModifier.clickable(openSpotifyAction()),
        )
        Spacer(modifier = GlanceModifier.width(12.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TrackTitle(model, modifier = GlanceModifier.defaultWeight())
                LikeButton(model, 40.dp)
            }
            Spacer(modifier = GlanceModifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ControlButton(model, R.drawable.ic_previous, R.string.action_previous, previousAction(model), 40.dp)
                Spacer(modifier = GlanceModifier.width(8.dp))
                PlayPauseButton(model, 44.dp)
                Spacer(modifier = GlanceModifier.width(8.dp))
                ControlButton(model, R.drawable.ic_next, R.string.action_next, nextAction(model), 40.dp)
            }
        }
    }
}

@Composable
private fun LargeLayout(model: WidgetModel) {
    val context = LocalContext.current
    Column(modifier = GlanceModifier.fillMaxSize()) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Cover(
                bitmap = model.cover,
                size = 64.dp,
                model = model,
                modifier = GlanceModifier.clickable(openSpotifyAction()),
            )
            Spacer(modifier = GlanceModifier.width(12.dp))
            TrackTitle(model, modifier = GlanceModifier.defaultWeight())
            LikeButton(model, 48.dp)
        }
        Spacer(modifier = GlanceModifier.height(8.dp))
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ControlButton(model, R.drawable.ic_previous, R.string.action_previous, previousAction(model), 48.dp)
            Spacer(modifier = GlanceModifier.width(16.dp))
            PlayPauseButton(model, 56.dp)
            Spacer(modifier = GlanceModifier.width(16.dp))
            ControlButton(model, R.drawable.ic_next, R.string.action_next, nextAction(model), 48.dp)
        }
        if (!model.loggedIn) {
            Spacer(modifier = GlanceModifier.height(12.dp))
            Text(
                text = context.getString(R.string.widget_not_logged_in),
                style = textStyle(model.colors.secondary, 12),
                modifier = GlanceModifier.clickable(openAppAction()),
            )
            return@Column
        }
        if (model.state.favorites.isNotEmpty()) {
            Spacer(modifier = GlanceModifier.height(12.dp))
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                model.state.favorites.forEachIndexed { index, playlist ->
                    Column(
                        modifier = GlanceModifier
                            .defaultWeight()
                            .clickable(playAction(context, playlist.uri, null))
                            .semantics { contentDescription = playlist.name },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Cover(model.favoriteCovers.getOrNull(index), 48.dp, model)
                        Spacer(modifier = GlanceModifier.height(4.dp))
                        Text(
                            text = playlist.name,
                            maxLines = 1,
                            style = textStyle(model.colors.text, 11),
                        )
                    }
                }
            }
        }
        if (model.state.recent.isNotEmpty()) {
            Spacer(modifier = GlanceModifier.height(12.dp))
            Text(
                text = context.getString(R.string.widget_recent),
                style = textStyle(model.colors.secondary, 12, bold = true),
            )
            LazyColumn(modifier = GlanceModifier.defaultWeight()) {
                items(model.state.recent.indices.toList(), itemId = { it.toLong() }) { index ->
                    val item = model.state.recent[index]
                    Row(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable(playAction(context, item.contextUri, item.trackUri))
                            .semantics { contentDescription = "${item.title}, ${item.artist}" },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Cover(model.recentCovers.getOrNull(index), 40.dp, model)
                        Spacer(modifier = GlanceModifier.width(8.dp))
                        Column(modifier = GlanceModifier.defaultWeight()) {
                            Text(
                                text = item.title,
                                maxLines = 1,
                                style = textStyle(model.colors.text, 13, bold = true),
                            )
                            Text(
                                text = item.artist,
                                maxLines = 1,
                                style = textStyle(model.colors.secondary, 12),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackTitle(model: WidgetModel, modifier: GlanceModifier) {
    val context = LocalContext.current
    val nowPlaying = model.state.nowPlaying
    Column(modifier = modifier.clickable(openSpotifyAction())) {
        Text(
            text = nowPlaying?.title ?: context.getString(R.string.widget_nothing_playing),
            maxLines = 1,
            style = textStyle(model.colors.text, 15, bold = true),
        )
        Text(
            text = nowPlaying?.artist ?: context.getString(R.string.widget_open_spotify),
            maxLines = 1,
            style = textStyle(model.colors.secondary, 13),
        )
    }
}

@Composable
private fun Cover(bitmap: Bitmap?, size: Dp?, model: WidgetModel, modifier: GlanceModifier = GlanceModifier) {
    val context = LocalContext.current
    val sized = if (size != null) modifier.size(size) else modifier
    if (bitmap != null) {
        Image(
            provider = ImageProvider(bitmap),
            contentDescription = context.getString(R.string.cover),
            contentScale = ContentScale.Crop,
            modifier = sized.cornerRadius(8.dp),
        )
    } else {
        Box(
            modifier = sized
                .cornerRadius(8.dp)
                .background(color(WidgetColors.withAlpha(model.colors.text, 0.12))),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_music_note),
                contentDescription = null,
                colorFilter = ColorFilter.tint(color(model.colors.secondary)),
                modifier = GlanceModifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun PlayPauseButton(model: WidgetModel, size: Dp) {
    val playing = model.state.nowPlaying?.isPlaying == true
    ControlButton(
        model = model,
        icon = if (playing) R.drawable.ic_pause else R.drawable.ic_play,
        description = if (playing) R.string.action_pause else R.string.action_play,
        action = if (model.sessionActive) actionRunCallback<PlayPauseAction>() else openSpotifyAction(),
        size = size,
        filled = true,
    )
}

@Composable
private fun LikeButton(model: WidgetModel, size: Dp) {
    if (!model.loggedIn || model.state.nowPlaying == null) return
    val liked = model.state.nowPlayingLiked == true
    ControlButton(
        model = model,
        icon = if (liked) R.drawable.ic_favorite else R.drawable.ic_favorite_border,
        description = if (liked) R.string.action_unlike else R.string.action_like,
        action = actionRunCallback<LikeAction>(),
        size = size,
        tint = if (liked) LIKED_COLOR else model.colors.text,
    )
}

@Composable
private fun ControlButton(
    model: WidgetModel,
    icon: Int,
    description: Int,
    action: Action,
    size: Dp,
    filled: Boolean = false,
    tint: Int = if (filled) model.colors.onButton else model.colors.text,
) {
    val context = LocalContext.current
    val base = GlanceModifier.size(size).cornerRadius(size / 2)
    Box(
        modifier = (if (filled) base.background(color(model.colors.button)) else base)
            .clickable(action),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(icon),
            contentDescription = context.getString(description),
            colorFilter = ColorFilter.tint(color(tint)),
            modifier = GlanceModifier.size(size * 0.5f),
        )
    }
}

@Composable
private fun Message(model: WidgetModel, text: Int, action: Action) {
    val context = LocalContext.current
    Column(
        modifier = GlanceModifier.fillMaxSize().clickable(action),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            provider = ImageProvider(R.drawable.ic_music_note),
            contentDescription = null,
            colorFilter = ColorFilter.tint(color(model.colors.secondary)),
            modifier = GlanceModifier.size(32.dp),
        )
        Spacer(modifier = GlanceModifier.height(8.dp))
        Text(
            text = context.getString(text),
            style = textStyle(model.colors.text, 13),
        )
    }
}

@Composable
private fun previousAction(model: WidgetModel): Action =
    if (model.sessionActive) actionRunCallback<PreviousAction>() else openSpotifyAction()

@Composable
private fun nextAction(model: WidgetModel): Action =
    if (model.sessionActive) actionRunCallback<NextAction>() else openSpotifyAction()

@Composable
private fun openSpotifyAction(): Action {
    val context = LocalContext.current
    return actionStartActivity(Playback.spotifyLaunchIntent(context) ?: Intent(context, MainActivity::class.java))
}

@Composable
private fun openAppAction(): Action {
    val context = LocalContext.current
    return actionStartActivity(Intent(context, MainActivity::class.java))
}

private fun playAction(context: Context, contextUri: String?, trackUri: String?): Action =
    actionStartActivity(PlayActivity.intent(context, contextUri, trackUri))

private fun color(argb: Int) = ColorProvider(Color(argb))

private fun textStyle(argb: Int, size: Int, bold: Boolean = false) = TextStyle(
    color = color(argb),
    fontSize = size.sp,
    fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
)
