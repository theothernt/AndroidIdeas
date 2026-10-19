package com.neilturner.arrangethings.ui.editor

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import com.neilturner.arrangethings.model.OverlayType

/**
 * The row of overlay types that can be added, followed by the reset action.
 *
 * The palette is disabled when every corner is full.
 */
@Composable
fun PaletteRow(
	enabled: Boolean,
	onSelect: (OverlayType) -> Unit,
	onReset: () -> Unit,
	onFocused: () -> Unit,
	modifier: Modifier = Modifier
) {
	Row(
		modifier = modifier
			.horizontalScroll(rememberScrollState())
			.focusGroup(),
		horizontalArrangement = Arrangement.spacedBy(12.dp)
	) {
		OverlayType.entries.forEach { type ->
			Button(
				onClick = { onSelect(type) },
				enabled = enabled,
				modifier = Modifier
					.padding(vertical = 4.dp)
					.onFocusChanged { if (it.isFocused) onFocused() }
			) {
				Text(text = type.displayName)
			}
		}

		Button(
			onClick = onReset,
			modifier = Modifier
				.padding(vertical = 4.dp)
				.onFocusChanged { if (it.isFocused) onFocused() }
		) {
			Text(text = "Reset")
		}
	}
}
