package com.neilturner.arrangethings.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
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
import com.neilturner.arrangethings.model.MetadataConfig
import com.neilturner.arrangethings.model.OverlaySlot
import com.neilturner.arrangethings.model.OverlayVariant
import com.neilturner.arrangethings.ui.overlays.size

/** Background of the menu panel. */
val MenuBackground = Color(0xF21A1A1A)

/** Colour of the scrim behind the menu. */
val MenuScrim = Color(0xCC000000)

/** Width of the little preview in a menu row. */
private val PreviewWidth = 116.dp

/** Height of the little preview in a menu row. */
private val PreviewHeight = 36.dp

/**
 * The overlay menu of a slot.
 *
 * Every row shows a small copy of the slot the overlay would fill, so the differences between the variants
 * can be seen before one is picked.
 */
@Composable
fun OverlayMenu(
	slot: OverlaySlot,
	currentVariant: OverlayVariant?,
	metadata: MetadataConfig?,
	onChoose: (OverlayVariant?) -> Unit,
	onDismiss: () -> Unit,
	modifier: Modifier = Modifier
) {
	val variants = OverlayVariant.entries
	val startIndex = variants.indexOf(currentVariant).let { if (it < 0) 0 else it }
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
				.width(480.dp)
				.clip(RoundedCornerShape(16.dp))
				.background(MenuBackground)
				.border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp))
				.padding(vertical = 10.dp)
		) {
			Text(
				text = slot.displayName,
				color = FocusedRing,
				fontSize = 14.sp,
				modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 6.dp)
			)

			variants.forEachIndexed { index, variant ->
				MenuRow(
					variant = variant,
					selected = variant == currentVariant,
					metadata = metadata,
					modifier = if (index == startIndex) {
						Modifier.focusRequester(requester)
					} else {
						Modifier
					},
					onChoose = { onChoose(variant) },
					onDismiss = onDismiss
				)
			}
		}
	}
}

/** One entry of the overlay menu. */
@Composable
private fun MenuRow(
	variant: OverlayVariant,
	selected: Boolean,
	metadata: MetadataConfig?,
	modifier: Modifier = Modifier,
	onChoose: () -> Unit,
	onDismiss: () -> Unit
) {
	var isFocused by remember { mutableStateOf(false) }
	val shape = RoundedCornerShape(8.dp)

	Row(
		modifier = modifier
			.padding(horizontal = 10.dp, vertical = 1.dp)
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
			.padding(horizontal = 10.dp, vertical = 4.dp),
		verticalAlignment = Alignment.CenterVertically
	) {
		VariantPreview(variant, metadata)

		Text(
			text = variant.title,
			color = Color(0xFFFFFFFF),
			fontSize = 15.sp,
			modifier = Modifier
				.weight(1f)
				.padding(start = 14.dp)
		)

		if (variant == OverlayVariant.Metadata) {
			Text(
				text = "FIELDS",
				color = Color(0x99FFFFFF),
				fontSize = 10.sp,
				modifier = Modifier.padding(end = 10.dp)
			)
		}

		Text(
			text = if (selected) "✓" else "",
			color = FocusedRing,
			fontSize = 15.sp
		)
	}
}

/**
 * A small copy of the slot the overlay would fill.
 *
 * The overlay is drawn at the size it declares and then scaled down, so the preview shows the real
 * proportions.
 */
@Composable
private fun VariantPreview(variant: OverlayVariant, metadata: MetadataConfig?) {
	val scale = minOf(
		PreviewWidth / variant.width.size,
		PreviewHeight / variant.height.size
	).coerceAtMost(0.8f)

	Box(
		modifier = Modifier
			.size(PreviewWidth, PreviewHeight)
			.clipToBounds(),
		contentAlignment = Alignment.Center
	) {
		Box(Modifier.scale(scale)) {
			OverlaySlotContent(
				variant = variant,
				metadata = metadata,
				modifier = Modifier
					// The preview box is smaller than the slot, so the size has to be forced.
					.requiredWidth(variant.width.size)
					.requiredHeight(variant.height.size)
			)
		}
	}
}
