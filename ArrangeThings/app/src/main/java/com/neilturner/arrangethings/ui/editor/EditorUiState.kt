package com.neilturner.arrangethings.ui.editor

import com.neilturner.arrangethings.model.MAX_METADATA_FIELDS
import com.neilturner.arrangethings.model.MetadataCategory
import com.neilturner.arrangethings.model.MetadataConfig
import com.neilturner.arrangethings.model.OverlayConfig
import com.neilturner.arrangethings.model.OverlaySlot

/** The state of the full screen metadata setup. */
data class MetadataEditorState(
	/** The slot the metadata will be applied to. */
	val slot: OverlaySlot,
	/** The field selection that Done applies. */
	val draft: MetadataConfig,
	/** The category whose fields are listed on the right. */
	val activeCategory: MetadataCategory = MetadataCategory.Camera
) {
	/** The number of fields in the draft. */
	val selectedCount: Int get() = draft.fields.size

	/** True when no more fields can be added. */
	val isFull: Boolean get() = selectedCount >= MAX_METADATA_FIELDS
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
