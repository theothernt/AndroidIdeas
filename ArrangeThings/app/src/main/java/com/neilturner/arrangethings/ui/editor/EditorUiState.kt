package com.neilturner.arrangethings.ui.editor

import com.neilturner.arrangethings.logic.firstCornerWithSpace
import com.neilturner.arrangethings.logic.overlaysInCorner
import com.neilturner.arrangethings.model.OverlayConfig

/** The whole state of the layout editor. */
data class EditorUiState(
	/** Every overlay in the layout. */
	val overlays: List<OverlayConfig> = emptyList(),
	/** Id of the overlay that is currently picked up, if any. */
	val grabbedId: String? = null,
	/** Where the grabbed overlay came from, so a cancel can restore it. */
	val originalConfig: OverlayConfig? = null,
	/** The overlay the action panel acts on. */
	val focusedOverlayId: String? = null,
	/** The overlay that should take focus next. */
	val focusTargetId: String? = null,
	/** Short message shown under the preview. */
	val statusMessage: String? = null
) {
	val isGrabbing: Boolean get() = grabbedId != null

	val grabbedOverlay: OverlayConfig? get() = overlays.firstOrNull { it.id == grabbedId }

	val focusedOverlay: OverlayConfig? get() = overlays.firstOrNull { it.id == focusedOverlayId }

	/** True when every corner is full and no further overlay can be added. */
	val isFull: Boolean get() = firstCornerWithSpace(overlays) == null

	/** True when the focused overlay shares its corner with another one. */
	val canSwapFocused: Boolean
		get() = focusedOverlay?.let { overlaysInCorner(overlays, it.corner).size > 1 } ?: false
}
