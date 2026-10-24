package com.neilturner.arrangethings.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.neilturner.arrangethings.logic.slotOverlay
import com.neilturner.arrangethings.model.ALL_SLOTS
import com.neilturner.arrangethings.model.OverlayConfig

/** Background of a name button. */
private val NameButtonBackground = Color(0xFF141414)

/** Border of a name button. */
private val NameButtonBorder = Color(0x66FFFFFF)

/** Text of a name button. */
private val NameButtonText = Color(0xFFFFFFFF)

/**
 * The overlays that are in the layout, named at the bottom of the screen.
 *
 * Every overlay that is set gets a button with its name, in the order of the slots. The buttons are
 * a summary, so they do not take focus.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ActiveOverlayBar(
	overlays: List<OverlayConfig>,
	modifier: Modifier = Modifier
) {
	val active = ALL_SLOTS.mapNotNull { slot -> slotOverlay(overlays, slot.corner, slot.order) }
	if (active.isEmpty()) return

	FlowRow(
		modifier = modifier.fillMaxWidth(),
		horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
		verticalArrangement = Arrangement.spacedBy(8.dp)
	) {
		active.forEach { overlay ->
			OverlayNameButton(title = overlay.variant.title)
		}
	}
}

/** A button shaped label with the name of an overlay. */
@Composable
private fun OverlayNameButton(title: String) {
	val shape = RoundedCornerShape(8.dp)

	Box(
		modifier = Modifier
			.height(34.dp)
			.clip(shape)
			.background(NameButtonBackground)
			.border(1.dp, NameButtonBorder, shape)
			.padding(horizontal = 16.dp),
		contentAlignment = Alignment.Center
	) {
		Text(
			text = title,
			color = NameButtonText,
			fontSize = 14.sp,
			maxLines = 1
		)
	}
}
