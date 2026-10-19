package com.neilturner.arrangethings.logic

import com.neilturner.arrangethings.model.Corner
import com.neilturner.arrangethings.model.MAX_PER_CORNER
import com.neilturner.arrangethings.model.OverlayConfig
import com.neilturner.arrangethings.model.OverlayType

/** Direction used when moving a grabbed overlay between corners. */
enum class MoveDirection {
	Left,
	Right,
	Up,
	Down
}

/**
 * The corner reached by moving one step in [direction] from [from], or null when the move would leave the grid.
 *
 * | From        | Left      | Right     | Up         | Down         |
 * |-------------|-----------|-----------|------------|--------------|
 * | TopStart    | -         | TopEnd    | -          | BottomStart  |
 * | TopEnd      | TopStart  | -         | -          | BottomEnd    |
 * | BottomStart | -         | BottomEnd | TopStart   | -            |
 * | BottomEnd   | BottomStart | -       | TopEnd     | -            |
 */
fun adjacentCorner(from: Corner, direction: MoveDirection): Corner? = when (from) {
	Corner.TopStart -> when (direction) {
		MoveDirection.Right -> Corner.TopEnd
		MoveDirection.Down -> Corner.BottomStart
		MoveDirection.Left, MoveDirection.Up -> null
	}

	Corner.TopEnd -> when (direction) {
		MoveDirection.Left -> Corner.TopStart
		MoveDirection.Down -> Corner.BottomEnd
		MoveDirection.Right, MoveDirection.Up -> null
	}

	Corner.BottomStart -> when (direction) {
		MoveDirection.Right -> Corner.BottomEnd
		MoveDirection.Up -> Corner.TopStart
		MoveDirection.Left, MoveDirection.Down -> null
	}

	Corner.BottomEnd -> when (direction) {
		MoveDirection.Left -> Corner.BottomStart
		MoveDirection.Up -> Corner.TopEnd
		MoveDirection.Right, MoveDirection.Down -> null
	}
}

/** The overlays held by [corner], ordered by their position within the corner. */
fun overlaysInCorner(overlays: List<OverlayConfig>, corner: Corner): List<OverlayConfig> =
	overlays.filter { it.corner == corner }.sortedBy { it.order }

/** True when [corner] already holds the maximum number of overlays. */
fun isCornerFull(overlays: List<OverlayConfig>, corner: Corner): Boolean =
	overlaysInCorner(overlays, corner).size >= MAX_PER_CORNER

/**
 * The first corner with space, in the order TopStart, TopEnd, BottomStart, BottomEnd, or null when every corner
 * is full.
 */
fun firstCornerWithSpace(overlays: List<OverlayConfig>): Corner? =
	Corner.entries.firstOrNull { !isCornerFull(overlays, it) }

/**
 * Adds a new overlay of [type] with the given [id] to the first corner with space.
 *
 * Returns null when every corner is full.
 */
fun addOverlay(overlays: List<OverlayConfig>, type: OverlayType, id: String): List<OverlayConfig>? {
	val corner = firstCornerWithSpace(overlays) ?: return null
	val order = overlaysInCorner(overlays, corner).size
	return renumber(overlays + OverlayConfig(id = id, type = type, corner = corner, order = order))
}

/** Removes the overlay with the given [id]. */
fun removeOverlay(overlays: List<OverlayConfig>, id: String): List<OverlayConfig> =
	renumber(overlays.filterNot { it.id == id })

/**
 * Moves the overlay with the given [id] into [target].
 *
 * The move is ignored when the target corner is already full or when the overlay is already in that corner.
 * The moved overlay is appended to the target stack.
 */
fun moveOverlay(overlays: List<OverlayConfig>, id: String, target: Corner): List<OverlayConfig> {
	val current = overlays.firstOrNull { it.id == id } ?: return overlays
	if (current.corner == target) return overlays
	if (isCornerFull(overlays, target)) return overlays

	val without = overlays.filterNot { it.id == id }
	val order = overlaysInCorner(without, target).size
	return renumber(without + current.copy(corner = target, order = order))
}

/**
 * Swaps the position of the overlay with the given [id] with the other overlay in the same corner.
 *
 * Does nothing when the corner holds fewer than two overlays.
 */
fun swapOrderInCorner(overlays: List<OverlayConfig>, id: String): List<OverlayConfig> {
	val current = overlays.firstOrNull { it.id == id } ?: return overlays
	val inCorner = overlaysInCorner(overlays, current.corner)
	if (inCorner.size < 2) return overlays
	val other = inCorner.first { it.id != id }

	return overlays.map {
		when (it.id) {
			current.id -> it.copy(order = other.order)
			other.id -> it.copy(order = current.order)
			else -> it
		}
	}
}

/**
 * Restores the overlay to the corner and position it was grabbed from.
 *
 * The overlay is removed from wherever it currently is and re-inserted into [original]'s corner at
 * [OverlayConfig.order], clamped to the size of that corner.
 *
 * If the original corner filled up while the overlay was away, the overlay is placed in the first corner with
 * space instead, so the capacity invariant always holds. The caller can compare the resulting corner with
 * [OverlayConfig.corner] to detect that case.
 */
fun cancelMove(overlays: List<OverlayConfig>, original: OverlayConfig): List<OverlayConfig> {
	val without = overlays.filterNot { it.id == original.id }
	val target = overlaysInCorner(without, original.corner)

	if (target.size < MAX_PER_CORNER) {
		val insertAt = original.order.coerceIn(0, target.size)
		val restored = target.take(insertAt) + original + target.drop(insertAt)
		val others = without.filterNot { it.corner == original.corner }
		return renumber(others + restored)
	}

	val fallback = Corner.entries.firstOrNull { corner ->
		corner != original.corner && overlaysInCorner(without, corner).size < MAX_PER_CORNER
	} ?: return without

	val restored = overlaysInCorner(without, fallback) + original.copy(corner = fallback)
	val others = without.filterNot { it.corner == fallback }
	return renumber(others + restored)
}

/**
 * Reports any breach of the layout invariants: more than [MAX_PER_CORNER] overlays in a corner, order values
 * that are not contiguous from zero within a corner, or duplicate overlay ids.
 *
 * An empty result means the layout is valid.
 */
fun invariantViolations(overlays: List<OverlayConfig>): List<String> {
	val violations = mutableListOf<String>()

	for (corner in Corner.entries) {
		val items = overlaysInCorner(overlays, corner)
		if (items.size > MAX_PER_CORNER) {
			violations += "$corner holds ${items.size} overlays, maximum is $MAX_PER_CORNER"
		}
		val orders = items.map { it.order }
		if (orders != items.indices.toList()) {
			violations += "$corner has non-contiguous orders $orders"
		}
	}

	overlays.groupBy { it.id }.filterValues { it.size > 1 }.keys.forEach {
		violations += "duplicate overlay id $it"
	}

	return violations
}

/** Re-assigns the order of every corner so the values are contiguous from zero. */
private fun renumber(overlays: List<OverlayConfig>): List<OverlayConfig> =
	Corner.entries.flatMap { corner ->
		overlaysInCorner(overlays, corner).mapIndexed { index, overlay -> overlay.copy(order = index) }
	}
