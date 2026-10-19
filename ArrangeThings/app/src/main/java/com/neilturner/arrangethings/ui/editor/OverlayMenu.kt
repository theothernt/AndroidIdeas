package com.neilturner.arrangethings.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.neilturner.arrangethings.model.OverlaySlot
import com.neilturner.arrangethings.model.OverlayType

/** Background of the menu panel. */
val MenuBackground = Color(0xF21A2029)

/** Colour of the scrim behind the menu. */
val MenuScrim = Color(0xCC000000)

/**
 * The overlay menu of a slot.
 *
 * The menu lists every overlay type plus "None", which empties the slot. The item for the type that is
 * currently in the slot starts focused.
 */
@Composable
fun OverlayMenu(
	slot: OverlaySlot,
	currentType: OverlayType?,
	onChoose: (OverlayType?) -> Unit,
	onDismiss: () -> Unit,
	modifier: Modifier = Modifier
) {
	val items: List<OverlayType?> = OverlayType.entries.toList() + null
	val startIndex = items.indexOf(currentType).let { if (it < 0) items.lastIndex else it }
	val requester = remember { FocusRequester() }

	LaunchedEffect(Unit) {
		requester.requestFocus()
	}

	Box(
		modifier = modifier
			.fillMaxSize()
			.background(MenuScrim),
		contentAlignment = Alignment.Center
	) {
		Column(
			modifier = Modifier
				.width(320.dp)
				.clip(RoundedCornerShape(16.dp))
				.background(MenuBackground)
				.border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp))
				.padding(vertical = 10.dp)
		) {
			Text(
				text = slot.displayName,
				color = FocusedRing,
				fontSize = 14.sp,
				modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 4.dp)
			)

			items.forEachIndexed { index, type ->
				MenuItem(
					label = type?.displayName ?: "None",
					selected = type == currentType,
					modifier = if (index == startIndex) {
						Modifier.focusRequester(requester)
					} else {
						Modifier
					},
					onChoose = { onChoose(type) },
					onDismiss = onDismiss
				)
			}
		}
	}
}

/** One entry of the overlay menu. */
@Composable
private fun MenuItem(
	label: String,
	selected: Boolean,
	modifier: Modifier = Modifier,
	onChoose: () -> Unit,
	onDismiss: () -> Unit
) {
	var isFocused by remember { mutableStateOf(false) }
	val shape = RoundedCornerShape(8.dp)

	Row(
		modifier = modifier
			.padding(horizontal = 10.dp, vertical = 2.dp)
			.clip(shape)
			.background(if (isFocused) FocusedRing.copy(alpha = 0.22f) else Color.Transparent)
			.border(1.dp, if (isFocused) FocusedRing else Color.Transparent, shape)
			.onFocusChanged { isFocused = it.isFocused }
			.focusable()
			.onPreviewKeyEvent { event ->
				if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
				when {
					event.key in ACTIVATION_KEYS -> {
						onChoose()
						true
					}

					event.key == Key.Back || event.key == Key.Escape -> {
						onDismiss()
						true
					}

					else -> false
				}
			}
			.padding(horizontal = 12.dp, vertical = 9.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(12.dp)
	) {
		Text(
			text = if (selected) "●" else "○",
			color = if (selected) FocusedRing else Color(0x55FFFFFF),
			fontSize = 12.sp
		)
		Text(
			text = label,
			color = Color(0xFFF2F2F5),
			fontSize = 16.sp
		)
	}
}
