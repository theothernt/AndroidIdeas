package com.neilturner.arrangethings.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.neilturner.arrangethings.model.OverlayConfig
import com.neilturner.arrangethings.model.OverlaySlot
import com.neilturner.arrangethings.ui.overlays.OverlayVisual

/** Colour of the ring around the focused slot. */
val FocusedRing = Color(0xFF7FD1FF)

/** Background of a slot that holds an overlay. */
val SlotBackground = Color(0xE6101014)

/** Background of a slot that is empty. */
val EmptySlotBackground = Color(0x14FFFFFF)

/**
 * One of the eight slots of the layout.
 *
 * The slot is a button: selecting it opens the menu with the overlay types.
 */
@Composable
fun OverlaySlotCard(
	slot: OverlaySlot,
	overlay: OverlayConfig?,
	enabled: Boolean,
	focusTargetKey: String?,
	onFocused: () -> Unit,
	onFocusTargetAchieved: () -> Unit,
	onOpenMenu: () -> Unit,
	modifier: Modifier = Modifier
) {
	val requester = remember { FocusRequester() }
	var isFocused by remember { mutableStateOf(false) }

	LaunchedEffect(focusTargetKey, slot.key) {
		if (focusTargetKey == slot.key) {
			requester.requestFocus()
			onFocusTargetAchieved()
		}
	}

	val shape = RoundedCornerShape(12.dp)
	val scale = if (isFocused) 1.03f else 1f

	Box(
		modifier = modifier
			.focusRequester(requester)
			.onFocusChanged {
				isFocused = it.isFocused
				if (it.isFocused) onFocused()
			}
			.focusable(enabled = enabled)
			.onPreviewKeyEvent { event ->
				if (enabled && event.type == KeyEventType.KeyDown && event.key in ACTIVATION_KEYS) {
					onOpenMenu()
					true
				} else {
					false
				}
			}
			.graphicsLayer {
				scaleX = scale
				scaleY = scale
			}
			.clip(shape)
			.background(if (overlay == null) EmptySlotBackground else SlotBackground)
			.border(if (isFocused) 2.dp else 1.dp, if (isFocused) FocusedRing else Color(0x33FFFFFF), shape)
			.padding(horizontal = 10.dp, vertical = 8.dp)
	) {
		Row(
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(8.dp)
		) {
			if (overlay == null) {
				Text(
					text = "Empty",
					color = Color(0x88FFFFFF),
					fontSize = 15.sp,
					modifier = Modifier.widthIn(min = 92.dp)
				)
			} else {
				OverlayVisual(type = overlay.type)
			}

			Text(
				text = "▾",
				color = if (isFocused) FocusedRing else Color(0x66FFFFFF),
				fontSize = 13.sp
			)
		}
	}
}

internal val ACTIVATION_KEYS = setOf(Key.DirectionCenter, Key.Enter)
