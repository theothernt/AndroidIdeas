package com.neilturner.arrangethings.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.neilturner.arrangethings.logic.metadataConfigFromNames
import com.neilturner.arrangethings.model.Corner
import com.neilturner.arrangethings.model.MetadataConfig
import com.neilturner.arrangethings.model.MetadataField
import com.neilturner.arrangethings.model.OverlayConfig
import com.neilturner.arrangethings.model.OverlayVariant
import kotlinx.serialization.Serializable
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

private val Context.arrangeThingsStore: DataStore<Preferences> by preferencesDataStore(name = "arrange_things")

/**
 * The stored form of a metadata configuration.
 *
 * Fields are stored by name rather than by enum so that a field can be renamed or removed without
 * losing the rest of the configuration.
 */
@Serializable
private data class StoredMetadata(
	val fields: List<String> = emptyList()
)

@Serializable
private data class StoredOverlay(
	val variant: OverlayVariant,
	val corner: Corner,
	val order: Int,
	val metadata: StoredMetadata? = null
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
				OverlayConfig(
					variant = it.variant,
					corner = it.corner,
					order = it.order,
					metadata = it.metadata?.let { stored ->
						metadataConfigFromNames(stored.fields)
					}
				)
			}
		}.getOrNull()
	}

	/** Writes the layout. */
	suspend fun save(overlays: List<OverlayConfig>) {
		val stored = overlays.map {
			StoredOverlay(
				variant = it.variant,
				corner = it.corner,
				order = it.order,
				metadata = it.metadata?.let { config ->
					StoredMetadata(fields = config.fields.map(MetadataField::name))
				}
			)
		}
		context.arrangeThingsStore.edit { preferences ->
			preferences[OVERLAYS_KEY] = json.encodeToString(stored)
		}
	}

	private companion object {
		val OVERLAYS_KEY = stringPreferencesKey("overlays")
	}
}
