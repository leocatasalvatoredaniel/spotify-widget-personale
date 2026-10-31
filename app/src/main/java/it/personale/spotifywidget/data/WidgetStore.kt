package it.personale.spotifywidget.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.widgetDataStore by preferencesDataStore("widget")
private val STATE_KEY = stringPreferencesKey("state")

/**
 * Data shown by the widgets, shared by the app, the widget and the media
 * listener
 */
class WidgetStore(private val context: Context) {

    suspend fun read(): WidgetState {
        val json = context.widgetDataStore.data.first()[STATE_KEY] ?: return WidgetState()
        return decode(json) ?: WidgetState()
    }

    suspend fun update(transform: (WidgetState) -> WidgetState): WidgetState {
        var result = WidgetState()
        context.widgetDataStore.edit { preferences ->
            val current = preferences[STATE_KEY]?.let(::decode) ?: WidgetState()
            result = transform(current)
            preferences[STATE_KEY] = StateJson.encodeToString(WidgetState.serializer(), result)
        }
        return result
    }

    private fun decode(json: String): WidgetState? =
        runCatching { StateJson.decodeFromString(WidgetState.serializer(), json) }.getOrNull()
}
