package it.personale.spotifywidget.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.updateAll

class SpotifyWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SpotifyWidget()
}

/** Redraws every widget with the current data */
suspend fun updateWidgets(context: Context) {
    SpotifyWidget().updateAll(context)
}
