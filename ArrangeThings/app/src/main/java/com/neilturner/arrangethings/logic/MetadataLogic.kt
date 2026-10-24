package com.neilturner.arrangethings.logic

import com.neilturner.arrangethings.model.MetadataConfig
import com.neilturner.arrangethings.model.MetadataField

/** The selected fields of a config, in the order the overlay draws them. */
fun selectedFields(config: MetadataConfig): List<MetadataField> = config.fields

/**
 * Toggles one field.
 *
 * A field that is not shown is appended to the end, so the order in the preview decides where it
 * appears in the overlay.
 */
fun toggleField(config: MetadataConfig, field: MetadataField): MetadataConfig {
	val fields = config.fields
	return if (field in fields) {
		config.copy(fields = fields - field)
	} else {
		config.copy(fields = fields + field)
	}
}

/**
 * Moves a field one place to the left (negative delta) or right (positive delta).
 *
 * A move that would leave the list changes nothing.
 */
fun moveField(config: MetadataConfig, field: MetadataField, delta: Int): MetadataConfig {
	val fields = config.fields
	val from = fields.indexOf(field)
	if (from < 0) return config

	val to = (from + delta).coerceIn(0, fields.lastIndex)
	if (from == to) return config

	val reordered = fields.toMutableList()
	reordered.removeAt(from)
	reordered.add(to, field)
	return config.copy(fields = reordered)
}

/** The field with a given stored name, including the names used by older versions. */
fun metadataFieldFromName(name: String): MetadataField? {
	if (name.equals("Shutter", ignoreCase = true)) return MetadataField.ShutterSpeed

	return MetadataField.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
}

/**
 * Turns stored field names into a config.
 *
 * Names that no longer map to a field are dropped. An empty stored list means the user cleared
 * every field, but a list that maps to nothing at all comes from an older default and falls back to
 * the current defaults.
 */
fun metadataConfigFromNames(names: List<String>): MetadataConfig {
	val fields = names.mapNotNull(::metadataFieldFromName).distinct()

	return MetadataConfig(
		fields = if (fields.isEmpty() && names.isNotEmpty()) {
			MetadataConfig.DEFAULT_FIELDS
		} else {
			fields
		}
	)
}
