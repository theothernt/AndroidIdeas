package com.neilturner.arrangethings.logic

import com.neilturner.arrangethings.model.Corner
import com.neilturner.arrangethings.model.MetadataConfig
import com.neilturner.arrangethings.model.OverlayConfig
import com.neilturner.arrangethings.model.OverlayVariant
import com.neilturner.arrangethings.model.SLOTS_PER_CORNER

/** The overlays of a corner, ordered by their slot. */
fun overlaysInCorner(overlays: List<OverlayConfig>, corner: Corner): List<OverlayConfig> =
	overlays.filter { it.corner == corner }.sortedBy { it.order }

/** The overlay in a slot, or null when the slot is empty. */
fun slotOverlay(overlays: List<OverlayConfig>, corner: Corner, order: Int): OverlayConfig? =
	overlays.firstOrNull { it.corner == corner && it.order == order }

/**
 * Puts an overlay of the given variant in a slot.
 *
 * A null variant empties the slot. Any overlay that was in the slot is replaced. The metadata
 * configuration only belongs to a Metadata overlay, so it is kept for that variant and dropped for
 * every other one.
 */
fun setSlot(
	overlays: List<OverlayConfig>,
	corner: Corner,
	order: Int,
	variant: OverlayVariant?,
	metadata: MetadataConfig? = null
): List<OverlayConfig> {
	val without = overlays.filterNot { it.corner == corner && it.order == order }
	if (variant == null) return without

	val config = if (variant == OverlayVariant.Metadata) metadata ?: MetadataConfig.Default else null
	return without + OverlayConfig(
		variant = variant,
		corner = corner,
		order = order,
		metadata = config
	)
}

/**
 * Reports any breach of the layout invariants: more than one overlay in a slot, a slot index outside the
 * corner, or more overlays in a corner than there are slots.
 *
 * An empty result means the layout is valid.
 */
fun invariantViolations(overlays: List<OverlayConfig>): List<String> {
	val violations = mutableListOf<String>()

	for (corner in Corner.entries) {
		val items = overlaysInCorner(overlays, corner)
		if (items.size > SLOTS_PER_CORNER) {
			violations += "$corner holds ${items.size} overlays, maximum is $SLOTS_PER_CORNER"
		}
		items.groupBy { it.order }.filterValues { it.size > 1 }.keys.forEach {
			violations += "$corner has more than one overlay in slot $it"
		}
		items.filter { it.order < 0 || it.order >= SLOTS_PER_CORNER }.forEach {
			violations += "$corner has an overlay in slot ${it.order}, which is outside the corner"
		}
	}

	return violations
}
