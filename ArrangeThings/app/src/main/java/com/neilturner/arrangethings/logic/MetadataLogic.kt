package com.neilturner.arrangethings.logic

import com.neilturner.arrangethings.model.MAX_METADATA_FIELDS
import com.neilturner.arrangethings.model.MetadataCategory
import com.neilturner.arrangethings.model.MetadataConfig
import com.neilturner.arrangethings.model.MetadataField

/** A category and the fields of it that are selected, in canonical order. */
data class MetadataSection(
	val category: MetadataCategory,
	val fields: List<MetadataField>
)

/** The sections of a config, in category order, skipping empty categories. */
fun metadataSections(config: MetadataConfig): List<MetadataSection> =
	MetadataCategory.entries.mapNotNull { category ->
		val fields = selectedFields(config, category)
		if (fields.isEmpty()) null else MetadataSection(category, fields)
	}

/** The selected fields of a category, in canonical order. */
fun selectedFields(config: MetadataConfig, category: MetadataCategory): List<MetadataField> =
	MetadataField.entries.filter { it.category == category && it in config.fields }

/**
 * Toggles one field.
 *
 * A field is never added when the config already holds the maximum number of fields.
 */
fun toggleField(config: MetadataConfig, field: MetadataField): MetadataConfig {
	val fields = config.fields
	return when {
		field in fields -> config.copy(fields = fields - field)
		fields.size >= MAX_METADATA_FIELDS -> config
		else -> config.copy(fields = fields + field)
	}
}

/**
 * Toggles every field of a category.
 *
 * A fully selected category is cleared. An empty or partially selected category is completed,
 * up to the maximum number of fields.
 */
fun toggleCategory(config: MetadataConfig, category: MetadataCategory): MetadataConfig {
	val all = MetadataField.entries.filter { it.category == category }
	val selected = all.filter { it in config.fields }
	if (selected.size == all.size) return config.copy(fields = config.fields - all.toSet())

	val room = MAX_METADATA_FIELDS - config.fields.size
	if (room <= 0) return config

	val missing = all.filterNot { it in config.fields }.take(room)
	return config.copy(fields = config.fields + missing)
}
