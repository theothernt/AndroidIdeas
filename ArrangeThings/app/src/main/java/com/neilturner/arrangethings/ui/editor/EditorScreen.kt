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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Button
import androidx.tv.material3.Text

/**
 * The layout editor.
 *
 * Every one of the eight slots is a button that opens a menu with the overlay types.
 */
@Composable
fun EditorScreen(viewModel: EditorViewModel = viewModel()) {
	val state by viewModel.state.collectAsStateWithLifecycle()

	// Back closes the overlay menu instead of leaving the app.
	BackHandler(enabled = state.isMenuOpen) {
		viewModel.closeMenu()
	}

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
				enabled = !state.isMenuOpen
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
				onChooseType = viewModel::chooseType,
				onDismissMenu = viewModel::closeMenu,
				modifier = Modifier.align(Alignment.Center)
			)
		}

		Spacer(Modifier.height(12.dp))

		Text(
			text = state.statusMessage ?: "Select a slot to choose the overlay it shows",
			fontSize = 15.sp,
			color = Color(0xCCFFFFFF)
		)

		Spacer(Modifier.height(4.dp))

		Text(
			text = "D-pad: choose an overlay   -   Select: pick it   -   Back: close the menu",
			fontSize = 13.sp,
			color = Color(0x88FFFFFF)
		)
	}
}
