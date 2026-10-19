package com.neilturner.arrangethings.ui.editor

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.neilturner.arrangethings.data.OverlayStorage
import com.neilturner.arrangethings.logic.setSlot
import com.neilturner.arrangethings.model.Corner
import com.neilturner.arrangethings.model.OverlayConfig
import com.neilturner.arrangethings.model.OverlaySlot
import com.neilturner.arrangethings.model.OverlayVariant
import com.neilturner.arrangethings.model.SLOTS_PER_CORNER
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Holds the layout and the state of the editor. */
class EditorViewModel(application: Application) : AndroidViewModel(application) {

	private val _state = MutableStateFlow(EditorUiState(overlays = seed()))
	val state: StateFlow<EditorUiState> = _state.asStateFlow()

	private val storage = OverlayStorage(application)

	init {
		viewModelScope.launch {
			// The stored layout is read first so that it is not overwritten by the seed.
			storage.load()?.let { saved ->
				_state.update { it.copy(overlays = saved) }
			}

			_state.map { it.overlays }
				.distinctUntilChanged()
				.collect { overlays -> storage.save(overlays) }
		}
	}

	// --- menu --------------------------------------------------------------------------------

	/** Opens the overlay menu for a slot. */
	fun openMenu(slot: OverlaySlot) {
		_state.update { it.copy(menuSlot = slot) }
	}

	/** Closes the overlay menu without changing the slot. */
	fun closeMenu() {
		val slot = _state.value.menuSlot ?: return
		_state.update { it.copy(menuSlot = null, focusTargetKey = slot.key) }
	}

	/**
	 * Puts the given overlay in the open slot. A null variant empties the slot.
	 */
	fun chooseVariant(variant: OverlayVariant?) {
		val slot = _state.value.menuSlot ?: return
		val updated = setSlot(_state.value.overlays, slot.corner, slot.order, variant)

		_state.update {
			it.copy(
				overlays = updated,
				menuSlot = null,
				focusTargetKey = slot.key,
				statusMessage = if (variant == null) {
					"${slot.corner.displayName()} slot ${slot.order + 1} cleared"
				} else {
					"${slot.corner.displayName()} slot ${slot.order + 1} set to ${variant.title}"
				}
			)
		}
	}

	// --- actions -----------------------------------------------------------------------------

	/** Puts the layout back to the starting arrangement. */
	fun reset() {
		_state.update {
			it.copy(
				overlays = seed(),
				menuSlot = null,
				focusTargetKey = null,
				statusMessage = "Layout reset"
			)
		}
	}

	// --- focus -------------------------------------------------------------------------------

	/** Called when a slot takes focus. */
	fun onSlotFocused(slot: OverlaySlot) {
		_state.update { it.copy(focusTargetKey = null) }
	}

	/** Called once the requested focus target has taken focus. */
	fun onFocusTargetAchieved(key: String) {
		_state.update { state ->
			if (state.focusTargetKey == key) state.copy(focusTargetKey = null) else state
		}
	}

	// --- helpers -----------------------------------------------------------------------------

	private fun seed(): List<OverlayConfig> = listOf(
		OverlayConfig(variant = OverlayVariant.Clock, corner = Corner.TopStart, order = 0),
		OverlayConfig(variant = OverlayVariant.WeatherNow, corner = Corner.TopEnd, order = 0),
		OverlayConfig(variant = OverlayVariant.NowPlayingText, corner = Corner.BottomEnd, order = 0)
	)
}

/** Human readable name of a corner. */
fun Corner.displayName(): String = when (this) {
	Corner.TopStart -> "Top Start"
	Corner.TopEnd -> "Top End"
	Corner.BottomStart -> "Bottom Start"
	Corner.BottomEnd -> "Bottom End"
}

/** Human readable name of a slot. */
val OverlaySlot.displayName: String
	get() = "${corner.displayName()} slot ${order + 1} of $SLOTS_PER_CORNER"
