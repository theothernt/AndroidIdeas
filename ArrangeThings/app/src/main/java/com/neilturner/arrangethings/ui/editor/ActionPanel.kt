package com.neilturner.arrangethings.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.Text

/** Colour of the action panel background. */
val PanelBackground = Color(0xFF141A24)

/**
 * The panel with the actions that apply to the overlay the editor is targeting.
 *
 * The panel keeps its target while the focus is inside it, so the user can move to it and act on the overlay
 * that was focused last.
 */
@Composable
fun ActionPanel(
	state: EditorUiState,
	onRemove: () -> Unit,
	onSwap: () -> Unit,
	onReset: () -> Unit,
	modifier: Modifier = Modifier
) {
	Column(
		modifier = modifier
			.fillMaxHeight()
			.focusGroup()
			.clip(RoundedCornerShape(16.dp))
			.background(PanelBackground)
			.border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(16.dp))
			.padding(20.dp),
		verticalArrangement = Arrangement.spacedBy(12.dp)
	) {
		Text(text = "Actions", fontSize = 20.sp)

		val target = state.focusedOverlay
		Text(
			text = if (target == null) "Focus an overlay to edit it" else "Editing: ${target.type.displayName}",
			fontSize = 14.sp,
			color = if (target == null) Color(0x99FFFFFF) else FocusedRing
		)

		Button(
			onClick = onRemove,
			enabled = target != null
		) {
			Text(text = "Remove")
		}

		Button(
			onClick = onSwap,
			enabled = state.canSwapFocused
		) {
			Text(text = "Swap order")
		}

		Button(
			onClick = onReset
		) {
			Text(text = "Reset")
		}
	}
}
