package com.neilturner.arrangethings.ui.editor

import com.neilturner.arrangethings.model.MetadataConfig
import com.neilturner.arrangethings.model.MetadataField
import com.neilturner.arrangethings.model.OverlayConfig
import com.neilturner.arrangethings.model.OverlaySlot

/** The state of the full screen metadata setup. */
data class MetadataEditorState(
	/** The slot the metadata will be applied to. */
	val slot: OverlaySlot,
	/** The ordered field selection that Done applies. */
	val draft: MetadataConfig,
	/** The field that has been picked up in the preview, or null when none is held. */
	val pickedUp: MetadataField? = null
) {
	/** The number of fields in the draft. */
	val selectedCount: Int get() = draft.fields.size
}

/** The whole state of the layout editor. */
data class EditorUiState(
	/** The overlay in every slot that is filled. */
	val overlays: List<OverlayConfig> = emptyList(),
	/** The slot whose overlay menu is open. */
	val menuSlot: OverlaySlot? = null,
	/** The slot whose metadata setup is open. */
	val metadataEditor: MetadataEditorState? = null,
	/** The slot that should take focus next. */
	val focusTargetKey: String? = null
) {
	val isMenuOpen: Boolean get() = menuSlot != null
	val isMetadataEditorOpen: Boolean get() = metadataEditor != null

	/** True while any full screen dialog is open. */
	val isDialogOpen: Boolean get() = isMenuOpen || isMetadataEditorOpen
}
