package com.neilturner.arrangethings.ui.overlays

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.neilturner.arrangethings.logic.selectedFields
import com.neilturner.arrangethings.model.MetadataConfig
import com.neilturner.arrangethings.model.MetadataField

/** Background of a metadata chip. */
private val ChipBackground = Color(0x1FFFFFFF)

/** Border of a metadata chip that is not focused. */
private val ChipBorder = Color(0x40FFFFFF)

/** Background of a focused metadata chip. */
private val ChipFocusBackground = Color(0x3DFFFFFF)

/** Background of a chip that is being moved. */
private val ChipPickedUpBackground = Color(0xFFFFFFFF)

/** The shape of every metadata chip. */
private val ChipShape = RoundedCornerShape(6.dp)

/** Space between two metadata chips. */
private val ChipGap = 4.dp

/**
 * The fields of a Metadata overlay, side by side in the order of the config.
 */
@Composable
fun MetadataContent(config: MetadataConfig, modifier: Modifier = Modifier) {
	val fields = selectedFields(config)

	if (fields.isEmpty()) {
		Text(
			text = "No metadata",
			color = OverlaySecondary,
			fontSize = 12.sp,
			modifier = modifier
		)
		return
	}

	Row(
		modifier = modifier,
		horizontalArrangement = Arrangement.spacedBy(ChipGap),
		verticalAlignment = Alignment.CenterVertically
	) {
		fields.forEach { field ->
			MetadataChip(field = field)
		}
	}
}

/**
 * One field drawn as a button: a small label above its value.
 *
 * The setup screen wraps the same chip in focus handling, so the preview and the overlay look the
 * same.
 */
@Composable
fun MetadataChip(
	field: MetadataField,
	modifier: Modifier = Modifier,
	focused: Boolean = false,
	pickedUp: Boolean = false
) {
	val labelColor = if (pickedUp) Color.Black else if (focused) Color.White else OverlayLabel
	val valueColor = if (pickedUp) Color.Black else OverlayContent

	Column(
		modifier = modifier
			.clip(ChipShape)
			.background(
				if (pickedUp) ChipPickedUpBackground else if (focused) ChipFocusBackground else ChipBackground
			)
			.border(
				width = if (focused || pickedUp) 2.dp else 1.dp,
				color = if (focused || pickedUp) Color.White else ChipBorder,
				shape = ChipShape
			)
			.padding(horizontal = 5.dp, vertical = 4.dp),
		horizontalAlignment = Alignment.CenterHorizontally
	) {
		Text(
			text = field.label,
			color = labelColor,
			fontSize = 8.sp,
			letterSpacing = 0.5.sp,
			maxLines = 1
		)

		Spacer(Modifier.height(1.dp))

		Text(
			text = field.demoValue,
			color = valueColor,
			fontSize = 10.sp,
			maxLines = 1,
			overflow = TextOverflow.Ellipsis
		)
	}
}
