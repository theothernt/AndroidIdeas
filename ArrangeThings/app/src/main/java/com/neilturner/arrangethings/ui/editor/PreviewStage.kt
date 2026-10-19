package com.neilturner.arrangethings.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.neilturner.arrangethings.logic.MoveDirection
import com.neilturner.arrangethings.logic.overlaysInCorner
import com.neilturner.arrangethings.model.Corner
import com.neilturner.arrangethings.model.MAX_PER_CORNER
import com.neilturner.arrangethings.model.OverlayConfig
import com.neilturner.arrangethings.model.OverlayType

/** Colour of the area outside the safe area. */
val StageBackground = Color(0xFF05070C)

/** Colour of the safe area. */
val SafeAreaBackground = Color(0xFF10141C)

/** Colour of the safe area outline. */
val SafeAreaBorder = Color(0x33FFFFFF)

/** Colour of the empty corner drop target. */
val DropTargetBorder = Color(0x557FD1FF)

/** Colour of a corner that cannot take another overlay. */
val FullCornerBorder = Color(0x66FFC24B)

/**
 * The 16:9 preview with the four corner slots.
 *
 * The safe area is inset from the edges to stand in for the overscan of a television, and every overlay is
 * placed inside it.
 */
@Composable
fun PreviewStage(
	state: EditorUiState,
	onOverlayFocused: (String) -> Unit,
	onFocusTargetAchieved: (String) -> Unit,
	onGrab: (String) -> Unit,
	onDrop: () -> Unit,
	onCancel: () -> Unit,
	onMove: (MoveDirection) -> Unit,
	onRemove: () -> Unit,
	onSwap: () -> Unit,
	modifier: Modifier = Modifier
) {
	val safeAreaShape = RoundedCornerShape(10.dp)

	Box(
		modifier = modifier
			.aspectRatio(16f / 9f)
			.clip(RoundedCornerShape(16.dp))
			.background(StageBackground)
	) {
		Box(
			modifier = Modifier
				.fillMaxSize()
				.padding(horizontal = 18.dp, vertical = 14.dp)
				.clip(safeAreaShape)
				.background(SafeAreaBackground)
				.border(1.dp, SafeAreaBorder, safeAreaShape)
		) {
			Column(
				modifier = Modifier
					.fillMaxSize()
					.focusGroup()
			) {
				Row(
					modifier = Modifier
						.fillMaxWidth()
						.weight(1f)
				) {
					CornerCell(
						corner = Corner.TopStart,
						state = state,
						onOverlayFocused = onOverlayFocused,
						onFocusTargetAchieved = onFocusTargetAchieved,
						onGrab = onGrab,
						onDrop = onDrop,
						onCancel = onCancel,
						onMove = onMove,
						onRemove = onRemove,
						onSwap = onSwap,
						modifier = Modifier
							.weight(1f)
							.fillMaxHeight()
					)
					CornerCell(
						corner = Corner.TopEnd,
						state = state,
						onOverlayFocused = onOverlayFocused,
						onFocusTargetAchieved = onFocusTargetAchieved,
						onGrab = onGrab,
						onDrop = onDrop,
						onCancel = onCancel,
						onMove = onMove,
						onRemove = onRemove,
						onSwap = onSwap,
						modifier = Modifier
							.weight(1f)
							.fillMaxHeight()
					)
				}
				Row(
					modifier = Modifier
						.fillMaxWidth()
						.weight(1f)
				) {
					CornerCell(
						corner = Corner.BottomStart,
						state = state,
						onOverlayFocused = onOverlayFocused,
						onFocusTargetAchieved = onFocusTargetAchieved,
						onGrab = onGrab,
						onDrop = onDrop,
						onCancel = onCancel,
						onMove = onMove,
						onRemove = onRemove,
						onSwap = onSwap,
						modifier = Modifier
							.weight(1f)
							.fillMaxHeight()
					)
					CornerCell(
						corner = Corner.BottomEnd,
						state = state,
						onOverlayFocused = onOverlayFocused,
						onFocusTargetAchieved = onFocusTargetAchieved,
						onGrab = onGrab,
						onDrop = onDrop,
						onCancel = onCancel,
						onMove = onMove,
						onRemove = onRemove,
						onSwap = onSwap,
						modifier = Modifier
							.weight(1f)
							.fillMaxHeight()
					)
				}
			}
		}
	}
}

/** One of the four corner slots. */
@Composable
private fun CornerCell(
	corner: Corner,
	state: EditorUiState,
	onOverlayFocused: (String) -> Unit,
	onFocusTargetAchieved: (String) -> Unit,
	onGrab: (String) -> Unit,
	onDrop: () -> Unit,
	onCancel: () -> Unit,
	onMove: (MoveDirection) -> Unit,
	onRemove: () -> Unit,
	onSwap: () -> Unit,
	modifier: Modifier = Modifier
) {
	val overlays = overlaysInCorner(state.overlays, corner)
	val isGrabbing = state.isGrabbing
	val isFull = overlays.size >= MAX_PER_CORNER
	val showDropTarget = isGrabbing && overlays.isEmpty()
	val showFull = isGrabbing && isFull

	// The overlay the actions apply to, when it lives in this corner.
	val target = state.focusedOverlay?.takeIf { it.corner == corner }
	val canSwap = target != null && overlays.size > 1

	val horizontalAlignment = when (corner) {
		Corner.TopStart, Corner.BottomStart -> Alignment.Start
		Corner.TopEnd, Corner.BottomEnd -> Alignment.End
	}
	val verticalArrangement = when (corner) {
		Corner.TopStart, Corner.TopEnd -> Arrangement.Top
		Corner.BottomStart, Corner.BottomEnd -> Arrangement.Bottom
	}
	// The hint sits on the edge of the cell that the overlays do not use.
	val hintAlignment = when (corner) {
		Corner.TopStart -> Alignment.BottomEnd
		Corner.TopEnd -> Alignment.BottomStart
		Corner.BottomStart -> Alignment.TopEnd
		Corner.BottomEnd -> Alignment.TopStart
	}
	val actionAlignment = when (corner) {
		Corner.TopStart, Corner.BottomStart -> Alignment.BottomStart
		Corner.TopEnd, Corner.BottomEnd -> Alignment.BottomEnd
	}

	Box(
		modifier = modifier
			.padding(10.dp)
			.then(
				if (showDropTarget) {
					Modifier.border(2.dp, DropTargetBorder, RoundedCornerShape(12.dp))
				} else if (showFull) {
					Modifier.border(2.dp, FullCornerBorder, RoundedCornerShape(12.dp))
				} else {
					Modifier
				}
			)
	) {
		Column(
			modifier = Modifier.fillMaxSize(),
			horizontalAlignment = horizontalAlignment,
			verticalArrangement = verticalArrangement
		) {
			overlays.forEach { overlay ->
				OverlayCard(
					overlay = overlay,
					isGrabbed = state.grabbedId == overlay.id,
					isTargeted = state.focusedOverlayId == overlay.id,
					focusTargetId = state.focusTargetId,
					onFocused = { onOverlayFocused(overlay.id) },
					onFocusTargetAchieved = { onFocusTargetAchieved(overlay.id) },
					onGrab = { onGrab(overlay.id) },
					onDrop = onDrop,
					onCancel = onCancel,
					onMove = onMove
				)
				Spacer(Modifier.height(8.dp))
			}

			// The action row sits at the end of the cell. The space for it is always kept so that nothing
			// moves when the actions appear.
			Box(
				modifier = Modifier.height(ActionRowHeight),
				contentAlignment = Alignment.BottomStart
			) {
				if (target != null) {
					Row(
						modifier = Modifier.align(actionAlignment),
						verticalAlignment = Alignment.Bottom
					) {
						Text(
							text = target.type.displayName,
							color = FocusedRing,
							fontSize = 12.sp,
							modifier = Modifier.padding(end = 10.dp)
						)
						MiniButton(
							text = "Remove",
							onClick = onRemove,
							onFocused = { onOverlayFocused(target.id) }
						)
						if (canSwap) {
							Spacer(Modifier.width(8.dp))
							MiniButton(
								text = "Swap",
								onClick = onSwap,
								onFocused = { onOverlayFocused(target.id) }
							)
						}
					}
				}
			}
		}

		if (showDropTarget || showFull) {
			Text(
				text = if (showFull) "Full" else "Drop here",
				color = if (showFull) FullCornerBorder else DropTargetBorder,
				fontSize = 13.sp,
				modifier = Modifier
					.align(hintAlignment)
					.padding(6.dp)
			)
		}
	}
}

@Preview(showBackground = true, widthDp = 640, heightDp = 360)
@Composable
private fun PreviewStagePreview() {
	PreviewStage(
		state = EditorUiState(
			overlays = listOf(
				OverlayConfig("a", OverlayType.Clock, Corner.TopStart, 0),
				OverlayConfig("b", OverlayType.Weather, Corner.TopEnd, 0),
				OverlayConfig("c", OverlayType.NowPlaying, Corner.BottomEnd, 0),
				OverlayConfig("d", OverlayType.Location, Corner.BottomEnd, 1)
			),
			focusedOverlayId = "d"
		),
		onOverlayFocused = {},
		onFocusTargetAchieved = {},
		onGrab = {},
		onDrop = {},
		onCancel = {},
		onMove = {},
		onRemove = {},
		onSwap = {}
	)
}
