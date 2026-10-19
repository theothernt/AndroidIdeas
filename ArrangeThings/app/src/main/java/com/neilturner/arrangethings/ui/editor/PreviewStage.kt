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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neilturner.arrangethings.logic.slotOverlay
import com.neilturner.arrangethings.model.Corner
import com.neilturner.arrangethings.model.OverlayConfig
import com.neilturner.arrangethings.model.OverlaySlot
import com.neilturner.arrangethings.model.OverlayType
import com.neilturner.arrangethings.model.SLOTS_PER_CORNER

/** Colour of the area outside the safe area. */
val StageBackground = Color(0xFF05070C)

/** Colour of the safe area. */
val SafeAreaBackground = Color(0xFF10141C)

/** Colour of the safe area outline. */
val SafeAreaBorder = Color(0x33FFFFFF)

/**
 * The 16:9 preview with the four corners and their slots.
 *
 * The safe area is inset from the edges to stand in for the overscan of a television, and every slot sits
 * inside it.
 */
@Composable
fun PreviewStage(
	state: EditorUiState,
	onSlotFocused: (OverlaySlot) -> Unit,
	onFocusTargetAchieved: (String) -> Unit,
	onOpenMenu: (OverlaySlot) -> Unit,
	onChooseType: (OverlayType?) -> Unit,
	onDismissMenu: () -> Unit,
	modifier: Modifier = Modifier
) {
	val safeAreaShape = RoundedCornerShape(10.dp)
	val menuOpen = state.isMenuOpen

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
						enabled = !menuOpen,
						onSlotFocused = onSlotFocused,
						onFocusTargetAchieved = onFocusTargetAchieved,
						onOpenMenu = onOpenMenu,
						modifier = Modifier
							.weight(1f)
							.fillMaxHeight()
					)
					CornerCell(
						corner = Corner.TopEnd,
						state = state,
						enabled = !menuOpen,
						onSlotFocused = onSlotFocused,
						onFocusTargetAchieved = onFocusTargetAchieved,
						onOpenMenu = onOpenMenu,
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
						enabled = !menuOpen,
						onSlotFocused = onSlotFocused,
						onFocusTargetAchieved = onFocusTargetAchieved,
						onOpenMenu = onOpenMenu,
						modifier = Modifier
							.weight(1f)
							.fillMaxHeight()
					)
					CornerCell(
						corner = Corner.BottomEnd,
						state = state,
						enabled = !menuOpen,
						onSlotFocused = onSlotFocused,
						onFocusTargetAchieved = onFocusTargetAchieved,
						onOpenMenu = onOpenMenu,
						modifier = Modifier
							.weight(1f)
							.fillMaxHeight()
					)
				}
			}
		}

		if (state.menuSlot != null) {
			OverlayMenu(
				slot = state.menuSlot,
				currentType = slotOverlay(state.overlays, state.menuSlot.corner, state.menuSlot.order)?.type,
				onChoose = onChooseType,
				onDismiss = onDismissMenu
			)
		}
	}
}

/** One of the four corners, with its slots. */
@Composable
private fun CornerCell(
	corner: Corner,
	state: EditorUiState,
	enabled: Boolean,
	onSlotFocused: (OverlaySlot) -> Unit,
	onFocusTargetAchieved: (String) -> Unit,
	onOpenMenu: (OverlaySlot) -> Unit,
	modifier: Modifier = Modifier
) {
	val horizontalAlignment = when (corner) {
		Corner.TopStart, Corner.BottomStart -> Alignment.Start
		Corner.TopEnd, Corner.BottomEnd -> Alignment.End
	}
	val verticalArrangement = when (corner) {
		Corner.TopStart, Corner.TopEnd -> Arrangement.Top
		Corner.BottomStart, Corner.BottomEnd -> Arrangement.Bottom
	}

	Column(
		modifier = modifier.padding(10.dp),
		horizontalAlignment = horizontalAlignment,
		verticalArrangement = verticalArrangement
	) {
		repeat(SLOTS_PER_CORNER) { index ->
			val slot = OverlaySlot(corner, index)
			OverlaySlotCard(
				slot = slot,
				overlay = slotOverlay(state.overlays, corner, index),
				enabled = enabled,
				focusTargetKey = state.focusTargetKey,
				onFocused = { onSlotFocused(slot) },
				onFocusTargetAchieved = { onFocusTargetAchieved(slot.key) },
				onOpenMenu = { onOpenMenu(slot) }
			)
			Spacer(Modifier.height(10.dp))
		}
	}
}

@Preview(showBackground = true, widthDp = 640, heightDp = 360)
@Composable
private fun PreviewStagePreview() {
	PreviewStage(
		state = EditorUiState(
			overlays = listOf(
				OverlayConfig(OverlayType.Clock, Corner.TopStart, 0),
				OverlayConfig(OverlayType.Weather, Corner.TopEnd, 0),
				OverlayConfig(OverlayType.NowPlaying, Corner.BottomEnd, 1)
			)
		),
		onSlotFocused = {},
		onFocusTargetAchieved = {},
		onOpenMenu = {},
		onChooseType = {},
		onDismissMenu = {}
	)
}
