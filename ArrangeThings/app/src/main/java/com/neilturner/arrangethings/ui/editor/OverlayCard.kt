package com.neilturner.arrangethings.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import com.neilturner.arrangethings.logic.MoveDirection
import com.neilturner.arrangethings.model.OverlayConfig
import com.neilturner.arrangethings.ui.overlays.OverlayVisual

/** Colour of the ring around the focused overlay. */
val FocusedRing = Color(0xFF7FD1FF)

/** Colour of the ring around the overlay that is picked up. */
val GrabbedRing = Color(0xFFFFC24B)

/** Colour of the dimmer ring around the overlay the action panel is targeting. */
val TargetedRing = Color(0x557FD1FF)

/**
 * A single overlay inside the preview.
 *
 * The card owns the focus: it is the only element that reacts to the D-pad while it is picked up.
 */
@Composable
fun OverlayCard(
	overlay: OverlayConfig,
	isGrabbed: Boolean,
	isTargeted: Boolean,
	focusTargetId: String?,
	onFocused: () -> Unit,
	onFocusTargetAchieved: () -> Unit,
	onGrab: () -> Unit,
	onDrop: () -> Unit,
	onCancel: () -> Unit,
	onMove: (MoveDirection) -> Unit,
	modifier: Modifier = Modifier
) {
	val requester = remember { FocusRequester() }
	var isFocused by remember { mutableStateOf(false) }

	LaunchedEffect(focusTargetId, overlay.id) {
		if (focusTargetId == overlay.id) {
			requester.requestFocus()
			onFocusTargetAchieved()
		}
	}

	val shape = RoundedCornerShape(12.dp)
	val ringColor = when {
		isGrabbed -> GrabbedRing
		isFocused -> FocusedRing
		isTargeted -> TargetedRing
		else -> Color.Transparent
	}
	val ringWidth = when {
		isGrabbed -> 3.dp
		isFocused -> 2.dp
		isTargeted -> 1.dp
		else -> 0.dp
	}
	val scale = when {
		isGrabbed -> 1.06f
		isFocused -> 1.02f
		else -> 1f
	}

	Box(
		modifier = modifier
			// The focus requester has to sit in front of the focus target so that it can reach it.
			.focusRequester(requester)
			.onFocusChanged {
				isFocused = it.isFocused
				if (it.isFocused) onFocused()
			}
			.focusable()
			.onPreviewKeyEvent { event -> handleKeyEvent(event, isGrabbed, onGrab, onDrop, onCancel, onMove) }
			.graphicsLayer {
				scaleX = scale
				scaleY = scale
			}
			.clip(shape)
			.border(ringWidth, ringColor, shape)
			.padding(4.dp)
	) {
		OverlayVisual(overlay)
	}
}

/**
 * Handles the keys for the card.
 *
 * Only key down events are acted on so that a single press is not processed twice.
 */
private fun handleKeyEvent(
	event: KeyEvent,
	isGrabbed: Boolean,
	onGrab: () -> Unit,
	onDrop: () -> Unit,
	onCancel: () -> Unit,
	onMove: (MoveDirection) -> Unit
): Boolean {
	if (event.type != KeyEventType.KeyDown) return false

	if (!isGrabbed) {
		return when (event.key) {
			Key.DirectionCenter, Key.Enter -> {
				onGrab()
				true
			}

			else -> false
		}
	}

	return when (event.key) {
		Key.DirectionLeft -> {
			onMove(MoveDirection.Left)
			true
		}

		Key.DirectionRight -> {
			onMove(MoveDirection.Right)
			true
		}

		Key.DirectionUp -> {
			onMove(MoveDirection.Up)
			true
		}

		Key.DirectionDown -> {
			onMove(MoveDirection.Down)
			true
		}

		Key.DirectionCenter, Key.Enter -> {
			onDrop()
			true
		}

		Key.Back, Key.Escape -> {
			onCancel()
			true
		}

		else -> false
	}
}
