package com.neilturner.arrangethings.ui.overlays

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.neilturner.arrangethings.model.Corner
import com.neilturner.arrangethings.model.OverlayConfig
import com.neilturner.arrangethings.model.OverlayType

/** Accent colour used for the small type label of every overlay. */
val OverlayAccent = Color(0xFF7FD1FF)

private val OverlayScrim = Color(0xE6101014)
private val OverlayBorder = Color(0x33FFFFFF)
private val OverlayContent = Color(0xFFF2F2F5)

/**
 * Renders the fake content of an overlay.
 *
 * Every type shows a small label and a hard coded value. The widths differ on purpose so that stacking and
 * corner alignment are exercised.
 */
@Composable
fun OverlayVisual(overlay: OverlayConfig, modifier: Modifier = Modifier) {
	val label: String
	val value: String
	val minWidth: Int

	when (overlay.type) {
		OverlayType.Clock -> {
			label = "TIME"
			value = "21:45"
			minWidth = 92
		}

		OverlayType.Date -> {
			label = "DATE"
			value = "Mon 19 Oct"
			minWidth = 118
		}

		OverlayType.Location -> {
			label = "PLACE"
			value = "Kitchen"
			minWidth = 128
		}

		OverlayType.Weather -> {
			label = "WEATHER"
			value = "17° Cloudy"
			minWidth = 146
		}

		OverlayType.NowPlaying -> {
			label = "NOW"
			value = "▶ Fleetwood Mac - Rumours"
			minWidth = 250
		}

		OverlayType.CustomText -> {
			label = "TEXT"
			value = "Overlay text"
			minWidth = 168
		}
	}

	Box(
		modifier = modifier
			.widthIn(min = minWidth.dp)
			.clip(RoundedCornerShape(10.dp))
			.background(OverlayScrim)
			.border(1.dp, OverlayBorder, RoundedCornerShape(10.dp))
			.padding(horizontal = 10.dp, vertical = 8.dp)
	) {
		Row(
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.Start
		) {
			Text(text = label, color = OverlayAccent, fontSize = 11.sp, maxLines = 1)
			Spacer(Modifier.width(8.dp))
			Text(text = value, color = OverlayContent, fontSize = 15.sp, maxLines = 1)
		}
	}
}

@Preview(showBackground = true, widthDp = 420, heightDp = 200)
@Composable
private fun OverlayVisualsPreview() {
	Box(Modifier.padding(16.dp)) {
		Column {
			OverlayVisual(OverlayConfig("a", OverlayType.Clock, Corner.TopStart, 0))
			OverlayVisual(OverlayConfig("b", OverlayType.NowPlaying, Corner.TopStart, 1))
		}
	}
}
