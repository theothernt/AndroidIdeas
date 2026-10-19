package com.neilturner.arrangethings.model

/** The four corners of the layout. */
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

/** An overlay sitting in one of the slots. */
data class OverlayConfig(
	val type: OverlayType,
	val corner: Corner,
	val order: Int
)

/** The number of slots in every corner. */
const val SLOTS_PER_CORNER = 2

/** A fixed position in the layout. */
data class OverlaySlot(
	val corner: Corner,
	val order: Int
) {
	/** Stable key for the slot, used as the Compose key and for the focus. */
	val key: String get() = "${corner.name}-$order"
}

/** Every slot of the layout, in a stable order. */
val ALL_SLOTS: List<OverlaySlot> = Corner.entries.flatMap { corner ->
	(0 until SLOTS_PER_CORNER).map { OverlaySlot(corner, it) }
}
