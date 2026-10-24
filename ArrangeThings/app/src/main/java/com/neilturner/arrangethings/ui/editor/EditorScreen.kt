package com.neilturner.arrangethings.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import com.neilturner.arrangethings.logic.slotOverlay

/**
 * The layout editor.
 *
 * Every one of the eight slots is a button that opens a menu with the overlay it can show.
 */
@Composable
fun EditorScreen(viewModel: EditorViewModel = viewModel()) {
	val state by viewModel.state.collectAsStateWithLifecycle()

	// Back drops a held metadata chip first, then closes the setup or the overlay menu.
	BackHandler(enabled = state.isMetadataEditorOpen) {
		if (state.metadataEditor?.pickedUp != null) {
			viewModel.dropMetadataPickup()
		} else {
			viewModel.cancelMetadata()
		}
	}

	BackHandler(enabled = state.isMenuOpen) {
		viewModel.closeMenu()
	}

	Box(modifier = Modifier.fillMaxSize()) {
		Column(
			modifier = Modifier
				.fillMaxSize()
				.padding(horizontal = 24.dp, vertical = 20.dp)
		) {
			Row(
				modifier = Modifier.fillMaxWidth(),
				verticalAlignment = Alignment.CenterVertically
			) {
				Text(text = "Arrange Things", fontSize = 30.sp)

				Spacer(Modifier.weight(1f))

				Button(
					onClick = viewModel::reset,
					enabled = !state.isDialogOpen
				) {
					Text(text = "Reset")
				}
			}

			Spacer(Modifier.height(12.dp))

			Box(
				modifier = Modifier
					.fillMaxWidth()
					.weight(1f)
			) {
				PreviewStage(
					state = state,
					onSlotFocused = viewModel::onSlotFocused,
					onFocusTargetAchieved = viewModel::onFocusTargetAchieved,
					onOpenMenu = viewModel::openMenu,
					modifier = Modifier.align(Alignment.Center)
				)
			}

			Spacer(Modifier.height(12.dp))

			ActiveOverlayBar(overlays = state.overlays)
		}

		val menuSlot = state.menuSlot
		if (menuSlot != null) {
			OverlayMenu(
				slot = menuSlot,
				currentVariant = slotOverlay(state.overlays, menuSlot.corner, menuSlot.order)?.variant,
				metadata = slotOverlay(state.overlays, menuSlot.corner, menuSlot.order)?.metadata,
				onChoose = viewModel::chooseVariant,
				onDismiss = viewModel::closeMenu
			)
		}

		val metadataEditor = state.metadataEditor
		if (metadataEditor != null) {
			MetadataSetupScreen(
				editor = metadataEditor,
				onToggleField = viewModel::toggleMetadataField,
				onTogglePickup = viewModel::toggleMetadataPickup,
				onMoveField = viewModel::moveMetadataField,
				onDropPickup = viewModel::dropMetadataPickup,
				onDefaults = viewModel::resetMetadataDraft,
				onCancel = viewModel::cancelMetadata,
				onApply = viewModel::applyMetadata
			)
		}
	}
}
