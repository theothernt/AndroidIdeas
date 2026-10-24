package com.neilturner.arrangethings.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
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
import com.neilturner.arrangethings.model.OverlayHeight
import com.neilturner.arrangethings.model.OverlaySlot
import com.neilturner.arrangethings.model.OverlayVariant
import com.neilturner.arrangethings.model.OverlayWidth
import com.neilturner.arrangethings.model.MetadataConfig
import com.neilturner.arrangethings.ui.overlays.OverlayContent
import com.neilturner.arrangethings.ui.overlays.size

/** Colour of the ring around the focused slot. */
val FocusedRing = Color(0xFFFFFFFF)

/** Background of a slot that holds an overlay. */
val SlotBackground = Color(0xE6101010)

/** Background of a slot that is empty. */
val EmptySlotBackground = Color(0x14FFFFFF)

/** The shape an empty slot takes. */
private val EmptyWidth = OverlayWidth.Narrow
private val EmptyHeight = OverlayHeight.SingleLine

/**
 * The content of a slot: the overlay and the chevron that says it can be changed.
 *
 * The menu draws a scaled down copy of this, so the preview in the menu looks exactly like the slot.
 */
@Composable
fun OverlaySlotContent(
	variant: OverlayVariant?,
	metadata: MetadataConfig? = null,
	modifier: Modifier = Modifier,
	chevronColor: Color = Color(0x66FFFFFF)
) {
	Row(
		modifier = modifier,
		verticalAlignment = Alignment.CenterVertically
	) {
		Box(Modifier.weight(1f)) {
			if (variant == null) {
				Text(
					text = "Empty",
					color = Color(0x88FFFFFF),
					fontSize = 15.sp,
					maxLines = 1
				)
			} else {
				OverlayContent(variant, metadata)
			}
		}
		Spacer(Modifier.width(6.dp))
		Text(text = "▾", color = chevronColor, fontSize = 13.sp, maxLines = 1)
	}
}

/**
 * One of the eight slots of the layout.
 *
 * The slot takes the shape the overlay declares, so the layout is stable and the editor never has to measure
 * the content.
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

	val variant = overlay?.variant
	val width = variant?.width ?: EmptyWidth
	val height = variant?.height ?: EmptyHeight

	val shape = RoundedCornerShape(12.dp)
	val scale = if (isFocused) 1.03f else 1f

	Box(
		modifier = modifier
			.width(width.size)
			.height(height.size)
			// The focus requester has to sit in front of the focus target so that it can reach it.
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
			.background(if (variant == null) EmptySlotBackground else SlotBackground)
			.border(if (isFocused) 2.dp else 1.dp, if (isFocused) FocusedRing else Color(0x33FFFFFF), shape)
			.padding(horizontal = 8.dp, vertical = 6.dp)
	) {
		OverlaySlotContent(
			variant = variant,
			metadata = overlay?.metadata,
			modifier = Modifier.fillMaxSize(),
			chevronColor = if (isFocused) FocusedRing else Color(0x66FFFFFF)
		)
	}
}

internal val ACTIVATION_KEYS = setOf(Key.DirectionCenter, Key.Enter)
