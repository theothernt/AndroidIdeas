package com.neilturner.arrangethings.ui.editor

import androidx.lifecycle.ViewModel
import com.neilturner.arrangethings.logic.MoveDirection
import com.neilturner.arrangethings.logic.adjacentCorner
import com.neilturner.arrangethings.logic.addOverlay
import com.neilturner.arrangethings.logic.cancelMove
import com.neilturner.arrangethings.logic.isCornerFull
import com.neilturner.arrangethings.logic.moveOverlay
import com.neilturner.arrangethings.logic.overlaysInCorner
import com.neilturner.arrangethings.logic.removeOverlay
import com.neilturner.arrangethings.logic.swapOrderInCorner
import com.neilturner.arrangethings.model.Corner
import com.neilturner.arrangethings.model.OverlayConfig
import com.neilturner.arrangethings.model.OverlayType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Holds the layout and the state of the editor. */
class EditorViewModel : ViewModel() {

	private val _state = MutableStateFlow(EditorUiState(overlays = seed()))
	val state: StateFlow<EditorUiState> = _state.asStateFlow()

	private var nextId = 0

	// --- palette -----------------------------------------------------------------------------

	/**
	 * Adds an overlay of the given type to the first corner with space and picks it up, so the user can move
	 * it straight away.
	 */
	fun addOverlay(type: OverlayType) {
		val current = _state.value
		val id = "overlay-${nextId++}"
		val updated = addOverlay(current.overlays, type, id)

		if (updated == null) {
			_state.update { it.copy(statusMessage = "All four corners are full - remove an overlay first") }
			return
		}

		val added = updated.first { it.id == id }
		_state.update {
			it.copy(
				overlays = updated,
				grabbedId = id,
				originalConfig = added,
				focusedOverlayId = id,
				focusTargetId = id,
				statusMessage = "Added ${type.displayName} - use the D-pad to place it"
			)
		}
	}

	// --- grab mode ---------------------------------------------------------------------------

	/** Picks up the overlay with the given id. */
	fun beginGrab(id: String) {
		val current = _state.value
		val overlay = current.overlays.firstOrNull { it.id == id } ?: return

		_state.update {
			it.copy(
				grabbedId = id,
				originalConfig = overlay,
				focusedOverlayId = id,
				statusMessage = "Picked up ${overlay.type.displayName} - Select to drop, Back to cancel"
			)
		}
	}

	/** Drops the grabbed overlay where it is. */
	fun dropGrab() {
		val current = _state.value
		val grabbed = current.grabbedOverlay ?: return
		_state.update {
			it.copy(
				grabbedId = null,
				originalConfig = null,
				statusMessage = "Dropped ${grabbed.type.displayName} in ${grabbed.corner.displayName}"
			)
		}
	}

	/** Cancels the grab and puts the overlay back where it came from. */
	fun cancelGrab() {
		val current = _state.value
		val original = current.originalConfig
		if (original == null) {
			_state.update { it.copy(grabbedId = null) }
			return
		}

		val restored = cancelMove(current.overlays, original)
		val landed = restored.first { it.id == original.id }

		_state.update {
			it.copy(
				overlays = restored,
				grabbedId = null,
				originalConfig = null,
				focusedOverlayId = original.id,
				focusTargetId = original.id,
				statusMessage = if (landed.corner == original.corner) {
					"Cancelled - ${original.type.displayName} put back in ${original.corner.displayName}"
				} else {
					"Cancelled - ${original.corner.displayName} was full, ${original.type.displayName} placed in ${landed.corner.displayName}"
				}
			)
		}
	}

	/** Moves the grabbed overlay one step in the given direction. */
	fun moveGrabbed(direction: MoveDirection) {
		val current = _state.value
		val grabbed = current.grabbedOverlay ?: return

		val target = adjacentCorner(grabbed.corner, direction)
		if (target == null) {
			_state.update { it.copy(statusMessage = "No corner that way") }
			return
		}

		if (isCornerFull(current.overlays, target)) {
			_state.update { it.copy(statusMessage = "${target.displayName} is full - move refused") }
			return
		}

		val updated = moveOverlay(current.overlays, grabbed.id, target)
		_state.update {
			it.copy(
				overlays = updated,
				focusedOverlayId = grabbed.id,
				focusTargetId = grabbed.id,
				statusMessage = "Moved to ${target.displayName}"
			)
		}
	}

	// --- action panel ------------------------------------------------------------------------

	/** Removes the overlay the action panel is targeting. */
	fun removeFocused() {
		val current = _state.value
		val target = current.focusedOverlay ?: return

		val updated = removeOverlay(current.overlays, target.id)
		val nextFocus = focusNeighbour(updated, target)

		_state.update {
			it.copy(
				overlays = updated,
				grabbedId = null,
				originalConfig = null,
				focusedOverlayId = nextFocus,
				focusTargetId = nextFocus,
				statusMessage = "Removed ${target.type.displayName}"
			)
		}
	}

	/** Swaps the focused overlay with the other overlay in its corner. */
	fun swapFocused() {
		val current = _state.value
		val target = current.focusedOverlay ?: return

		val updated = swapOrderInCorner(current.overlays, target.id)
		_state.update {
			it.copy(
				overlays = updated,
				focusedOverlayId = target.id,
				focusTargetId = target.id,
				statusMessage = "Swapped the order in ${target.corner.displayName}"
			)
		}
	}

	/** Puts the layout back to the starting arrangement. */
	fun reset() {
		nextId = 0
		_state.update {
			it.copy(
				overlays = seed(),
				grabbedId = null,
				originalConfig = null,
				focusedOverlayId = null,
				focusTargetId = null,
				statusMessage = "Layout reset"
			)
		}
	}

	// --- focus -------------------------------------------------------------------------------

	/** Called when an overlay takes focus. */
	fun onOverlayFocused(id: String) {
		_state.update { it.copy(focusedOverlayId = id) }
	}

	/**
	 * Called when focus leaves the overlays for the palette. The action panel keeps its own target while focus
	 * is inside it, so the target is only dropped here.
	 */
	fun onPaletteFocused() {
		_state.update { it.copy(focusedOverlayId = null) }
	}

	/** Called once the requested focus target has actually taken focus. */
	fun onFocusTargetAchieved(id: String) {
		_state.update { state ->
			if (state.focusTargetId == id) state.copy(focusTargetId = null) else state
		}
	}

	// --- helpers -----------------------------------------------------------------------------

	/**
	 * Picks the overlay that should take focus after [removed] was deleted: another overlay in the same corner
	 * first, then one in a neighbouring corner, and finally nothing when the layout is empty.
	 */
	private fun focusNeighbour(overlays: List<OverlayConfig>, removed: OverlayConfig): String? {
		val sameCorner = overlaysInCorner(overlays, removed.corner)
		sameCorner.getOrNull(removed.order)?.let { return it.id }
		sameCorner.firstOrNull()?.let { return it.id }

		for (direction in MoveDirection.entries) {
			val adjacent = adjacentCorner(removed.corner, direction) ?: continue
			overlaysInCorner(overlays, adjacent).firstOrNull()?.let { return it.id }
		}

		return null
	}

	private fun seed(): List<OverlayConfig> = listOf(
		OverlayConfig(id = "seed-clock", type = OverlayType.Clock, corner = Corner.TopStart, order = 0),
		OverlayConfig(id = "seed-weather", type = OverlayType.Weather, corner = Corner.TopEnd, order = 0),
		OverlayConfig(id = "seed-now-playing", type = OverlayType.NowPlaying, corner = Corner.BottomEnd, order = 0)
	)
}

/** Human readable name of an overlay type. */
val OverlayType.displayName: String
	get() = when (this) {
		OverlayType.Clock -> "Clock"
		OverlayType.Date -> "Date"
		OverlayType.Location -> "Location"
		OverlayType.Weather -> "Weather"
		OverlayType.NowPlaying -> "Now Playing"
		OverlayType.CustomText -> "Custom Text"
	}

/** Human readable name of a corner. */
val Corner.displayName: String
	get() = when (this) {
		Corner.TopStart -> "Top Start"
		Corner.TopEnd -> "Top End"
		Corner.BottomStart -> "Bottom Start"
		Corner.BottomEnd -> "Bottom End"
	}
