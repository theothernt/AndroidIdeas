package com.neilturner.arrangethings.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.neilturner.arrangethings.model.Corner
import com.neilturner.arrangethings.model.OverlayConfig
import com.neilturner.arrangethings.model.OverlayType
import kotlinx.serialization.Serializable
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

private val Context.arrangeThingsStore: DataStore<Preferences> by preferencesDataStore(name = "arrange_things")

@Serializable
private data class StoredOverlay(
	val type: OverlayType,
	val corner: Corner,
	val order: Int
)

/**
 * Stores the layout in a preferences data store as a json list.
 */
class OverlayStorage(private val context: Context) {

	private val json = Json { ignoreUnknownKeys = true }

	/** Reads the stored layout, or null when nothing has been stored yet. */
	suspend fun load(): List<OverlayConfig>? {
		val raw = context.arrangeThingsStore.data.first().get(OVERLAYS_KEY) ?: return null

		return runCatching {
			json.decodeFromString<List<StoredOverlay>>(raw).map {
				OverlayConfig(type = it.type, corner = it.corner, order = it.order)
			}
		}.getOrNull()
	}

	/** Writes the layout. */
	suspend fun save(overlays: List<OverlayConfig>) {
		val stored = overlays.map { StoredOverlay(it.type, it.corner, it.order) }
		context.arrangeThingsStore.edit { preferences ->
			preferences[OVERLAYS_KEY] = json.encodeToString(stored)
		}
	}

	private companion object {
		val OVERLAYS_KEY = stringPreferencesKey("overlays")
	}
}
