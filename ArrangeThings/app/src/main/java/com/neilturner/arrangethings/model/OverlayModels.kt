package com.neilturner.arrangethings.model

/** The four corner slots an overlay can occupy. */
enum class Corner {
	TopStart,
	TopEnd,
	BottomStart,
	BottomEnd
}

/** The kind of fake overlay shown in the editor. */
enum class OverlayType {
	Clock,
	Date,
	Location,
	Weather,
	NowPlaying,
	CustomText
}

/**
 * A single overlay placed in a corner.
 *
 * [order] is the position within the corner, 0 or 1. The overlay with order 0 is rendered above the one with
 * order 1.
 */
data class OverlayConfig(
	val id: String,
	val type: OverlayType,
	val corner: Corner,
	val order: Int
)

/** Maximum number of overlays a single corner can hold. */
const val MAX_PER_CORNER = 2

/** Total number of overlays the layout can hold. */
const val MAX_OVERLAYS = MAX_PER_CORNER * 4
