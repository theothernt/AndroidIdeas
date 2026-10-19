package com.neilturner.arrangethings.ui.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Text

/**
 * The layout editor.
 *
 * Everything is driven with the D-pad: the palette adds overlays, the preview moves them around and the action
 * row at the end of a corner cell removes them or swaps their order.
 */
@Composable
fun EditorScreen(viewModel: EditorViewModel = viewModel()) {
	val state by viewModel.state.collectAsStateWithLifecycle()

	Column(
		modifier = Modifier
			.fillMaxSize()
			.padding(horizontal = 24.dp, vertical = 20.dp)
	) {
		Text(text = "Arrange Things", fontSize = 30.sp)

		Spacer(Modifier.height(12.dp))

		PaletteRow(
			enabled = !state.isFull,
			onSelect = viewModel::addOverlay,
			onReset = viewModel::reset,
			onFocused = viewModel::onPaletteFocused,
			modifier = Modifier.fillMaxWidth()
		)

		Spacer(Modifier.height(12.dp))

		Box(
			modifier = Modifier
				.fillMaxWidth()
				.weight(1f)
		) {
			PreviewStage(
				state = state,
				onOverlayFocused = viewModel::onOverlayFocused,
				onFocusTargetAchieved = viewModel::onFocusTargetAchieved,
				onGrab = viewModel::beginGrab,
				onDrop = viewModel::dropGrab,
				onCancel = viewModel::cancelGrab,
				onMove = viewModel::moveGrabbed,
				onRemove = viewModel::removeFocused,
				onSwap = viewModel::swapFocused,
				modifier = Modifier.align(Alignment.Center)
			)
		}

		Spacer(Modifier.height(12.dp))

		Text(
			text = buildString {
				append(state.statusMessage ?: "Select an overlay in the preview to pick it up")
				if (state.overlays.isEmpty()) {
					append("   -   No overlays yet, add one from the palette above")
				}
				if (state.isFull) {
					append("   -   All four corners are full, remove an overlay to add another")
				}
			},
			fontSize = 15.sp,
			color = Color(0xCCFFFFFF)
		)

		Spacer(Modifier.height(4.dp))

		Text(
			text = "D-pad: move   -   Select: pick up / drop   -   Back: cancel   -   Down: overlay actions",
			fontSize = 13.sp,
			color = Color(0x88FFFFFF)
		)
	}
}
