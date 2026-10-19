package com.neilturner.arrangethings.model

/** The four corners of the layout. */
enum class Corner {
	TopStart,
	TopEnd,
	BottomStart,
	BottomEnd
}

/** How wide an overlay is in its slot. */
enum class OverlayWidth {
	Narrow,
	Wide
}

/** How tall an overlay is in its slot. */
enum class OverlayHeight {
	SingleLine,
	TwoLines,
	Block
}

/**
 * The kind of overlay a slot can show.
 *
 * Every variant declares the shape it takes, so the editor can lay the slots out without measuring the
 * content. The content itself is drawn to fit that shape.
 */
enum class OverlayVariant(
	val title: String,
	val width: OverlayWidth,
	val height: OverlayHeight
) {
	Clock("Clock", OverlayWidth.Narrow, OverlayHeight.SingleLine),
	Date("Date", OverlayWidth.Narrow, OverlayHeight.SingleLine),
	Location("Location", OverlayWidth.Narrow, OverlayHeight.SingleLine),
	WeatherNow("Weather - now", OverlayWidth.Narrow, OverlayHeight.SingleLine),
	Weather3Day("Weather - 3 day forecast", OverlayWidth.Wide, OverlayHeight.Block),
	NowPlayingText("Now playing - text", OverlayWidth.Narrow, OverlayHeight.TwoLines),
	NowPlayingAlbumArt("Now playing - album art", OverlayWidth.Wide, OverlayHeight.TwoLines),
	CustomText("Custom text", OverlayWidth.Narrow, OverlayHeight.SingleLine)
}

/** An overlay sitting in one of the slots. */
data class OverlayConfig(
	val variant: OverlayVariant,
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
