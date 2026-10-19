package com.neilturner.arrangethings.ui.editor

import com.neilturner.arrangethings.model.OverlayConfig
import com.neilturner.arrangethings.model.OverlaySlot

/** The whole state of the layout editor. */
data class EditorUiState(
	/** The overlay in every slot that is filled. */
	val overlays: List<OverlayConfig> = emptyList(),
	/** The slot whose overlay menu is open. */
	val menuSlot: OverlaySlot? = null,
	/** The slot that should take focus next. */
	val focusTargetKey: String? = null,
	/** Short message shown under the preview. */
	val statusMessage: String? = null
) {
	val isMenuOpen: Boolean get() = menuSlot != null
}
