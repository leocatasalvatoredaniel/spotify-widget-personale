package it.personale.spotifywidget.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Process
import android.widget.RemoteViews
import android.widget.RemoteViewsService

/**
 * The scrolling title of each widget, as the one item of a list
 * (title_list in widget_player). The launcher applies every update of a
 * widget again to all its views, and a title set again starts scrolling from
 * the beginning: several times a second while the progress bar moves. The
 * items of a list are left as they are, until the app says they changed
 * ([WidgetRenderer.render] does when the title, its size or its color change).
 */
class TitleService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        TitleFactory(
            applicationContext,
            intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID),
        )
}

private class TitleFactory(private val context: Context, private val widgetId: Int) :
    RemoteViewsService.RemoteViewsFactory {

    override fun onCreate() = Unit

    override fun onDataSetChanged() = noteCaller()

    override fun onDestroy() = Unit

    override fun getCount() = 1

    /**
     * Always the title of now: Android keeps a list made earlier and may
     * show it again (e.g. when the widget is drawn anew) without saying
     * that its data changed
     */
    override fun getViewAt(position: Int): RemoteViews {
        noteCaller()
        return WidgetRenderer.titleViews(context, widgetId)
    }

    /** The title itself while the launcher loads it, instead of the text "Loading…" of Android */
    override fun getLoadingView(): RemoteViews = WidgetRenderer.titleViews(context, widgetId)

    override fun getViewTypeCount() = 1

    override fun getItemId(position: Int) = position.toLong()

    override fun hasStableIds() = true

    /**
     * The launcher asks for the title from its own process. Some versions of
     * Android instead turn the list into plain views inside the app, before
     * sending the update: then the title is applied again with every update,
     * as without the list.
     */
    private fun noteCaller() {
        WidgetRenderer.titleSeparate = Binder.getCallingUid() != Process.myUid()
    }
}
