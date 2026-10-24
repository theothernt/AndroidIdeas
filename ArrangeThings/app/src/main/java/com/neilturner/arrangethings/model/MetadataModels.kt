package com.neilturner.arrangethings.model

import kotlinx.serialization.Serializable

/**
 * A piece of metadata that the Metadata overlay can show.
 *
 * The demo value stands in for the value that would be read from a photo or video.
 */
enum class MetadataField(
	val title: String,
	val label: String,
	val demoValue: String
) {
	Iso("ISO", "ISO", "400"),
	Aperture("Aperture", "APERTURE", "f/4"),
	ShutterSpeed("Shutter speed", "SHUTTER", "1/250s"),
	FocalLength("Focal length", "FOCAL", "35mm"),
	Place("Location (Place)", "PLACE", "Brighton, UK")
}

/**
 * The fields that a Metadata overlay shows in one slot.
 *
 * The list is ordered: the overlay draws the fields from left to right in exactly this order, and
 * the setup screen lets the order be changed.
 */
@Serializable
data class MetadataConfig(
	val fields: List<MetadataField> = DEFAULT_FIELDS
) {
	companion object {
		val DEFAULT_FIELDS: List<MetadataField> = MetadataField.entries

		val Default: MetadataConfig = MetadataConfig()
	}
}
