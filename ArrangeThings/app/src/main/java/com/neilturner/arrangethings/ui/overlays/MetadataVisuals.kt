package com.neilturner.arrangethings.ui.overlays

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.neilturner.arrangethings.logic.MetadataSection
import com.neilturner.arrangethings.logic.metadataSections
import com.neilturner.arrangethings.model.MetadataConfig

/**
 * The fields of a Metadata overlay in a two column grid.
 *
 * Sections are laid out in pairs so the categories stay in their canonical order. A section that
 * is alone on the last row keeps the left column.
 */
@Composable
fun MetadataContent(config: MetadataConfig, modifier: Modifier = Modifier) {
	val sections = metadataSections(config)

	if (sections.isEmpty()) {
		Text(
			text = "No metadata",
			color = OverlaySecondary,
			fontSize = 12.sp,
			modifier = modifier
		)
		return
	}

	Column(
		modifier = modifier,
		verticalArrangement = Arrangement.spacedBy(6.dp)
	) {
		sections.chunked(2).forEach { pair ->
			Row(
				modifier = Modifier.fillMaxWidth(),
				horizontalArrangement = Arrangement.spacedBy(12.dp)
			) {
				pair.forEach { section ->
					MetadataSectionBlock(section = section, modifier = Modifier.weight(1f))
				}
				if (pair.size == 1) Spacer(Modifier.weight(1f))
			}
		}
	}
}

/** One category and its selected fields. */
@Composable
private fun MetadataSectionBlock(
	section: MetadataSection,
	modifier: Modifier = Modifier
) {
	Column(modifier = modifier) {
		Text(
			text = section.category.title.uppercase(),
			color = OverlayAccent,
			fontSize = 9.sp,
			letterSpacing = 0.6.sp,
			maxLines = 1
		)

		Spacer(Modifier.height(2.dp))

		section.fields.forEach { field ->
			Row(
				modifier = Modifier.height(13.dp),
				verticalAlignment = Alignment.CenterVertically
			) {
				Text(
					text = field.label,
					color = OverlayLabel,
					fontSize = 9.sp,
					maxLines = 1,
					modifier = Modifier.width(44.dp)
				)
				Text(
					text = field.demoValue,
					color = OverlayContent,
					fontSize = 10.sp,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis
				)
			}
		}
	}
}
