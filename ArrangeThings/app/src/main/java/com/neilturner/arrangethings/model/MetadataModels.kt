package com.neilturner.arrangethings.model

import kotlinx.serialization.Serializable

/** The fixed groups of metadata that the setup screen offers. */
enum class MetadataCategory(val title: String) {
	Camera("Camera"),
	Exposure("Exposure"),
	Location("Location"),
	File("File"),
	Video("Video")
}

/**
 * A piece of metadata that the Metadata overlay can show.
 *
 * The demo value stands in for the value that would be read from a photo or video.
 */
enum class MetadataField(
	val category: MetadataCategory,
	val label: String,
	val demoValue: String
) {
	CameraMake(MetadataCategory.Camera, "MAKE", "Sony"),
	CameraModel(MetadataCategory.Camera, "MODEL", "A7 IV"),
	Lens(MetadataCategory.Camera, "LENS", "FE 24-70mm F2.8"),

	Iso(MetadataCategory.Exposure, "ISO", "400"),
	Aperture(MetadataCategory.Exposure, "APERTURE", "f/4"),
	Shutter(MetadataCategory.Exposure, "SHUTTER", "1/250s"),
	FocalLength(MetadataCategory.Exposure, "FOCAL", "35mm"),
	ExposureCompensation(MetadataCategory.Exposure, "EV", "+0.3"),

	Place(MetadataCategory.Location, "PLACE", "Brighton, UK"),
	Coordinates(MetadataCategory.Location, "GPS", "50.82° N 0.13° W"),
	Altitude(MetadataCategory.Location, "ALT", "52 m"),

	FileName(MetadataCategory.File, "FILE", "DSC_0421.JPG"),
	Format(MetadataCategory.File, "FORMAT", "JPEG"),
	Dimensions(MetadataCategory.File, "PIXELS", "6000 × 4000"),
	FileSize(MetadataCategory.File, "SIZE", "24.1 MB"),
	DateTaken(MetadataCategory.File, "DATE", "19 Oct 2026"),

	VideoDuration(MetadataCategory.Video, "DURATION", "00:02:14"),
	VideoResolution(MetadataCategory.Video, "RES", "3840 × 2160"),
	VideoFrameRate(MetadataCategory.Video, "FPS", "30"),
	VideoCodec(MetadataCategory.Video, "CODEC", "H.265"),
	VideoBitrate(MetadataCategory.Video, "BITRATE", "45 Mbps")
}

/** The most fields a Tall overlay can show at once. */
const val MAX_METADATA_FIELDS = 8

/**
 * The fields that a Metadata overlay shows in one slot.
 *
 * Fields are stored as a set; the overlay and the setup screen always present them in the
 * canonical order of [MetadataCategory] and [MetadataField].
 */
@Serializable
data class MetadataConfig(
	val fields: Set<MetadataField> = DEFAULT_FIELDS
) {
	companion object {
		val DEFAULT_FIELDS: Set<MetadataField> = setOf(
			MetadataField.CameraMake,
			MetadataField.CameraModel,
			MetadataField.Lens,
			MetadataField.Iso,
			MetadataField.Aperture,
			MetadataField.Shutter,
			MetadataField.Place,
			MetadataField.Coordinates
		)

		val Default: MetadataConfig = MetadataConfig()
	}
}
