package com.neilturner.arrangethings.ui.overlays

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.neilturner.arrangethings.model.OverlayHeight
import com.neilturner.arrangethings.model.OverlayVariant
import com.neilturner.arrangethings.model.OverlayWidth
import com.neilturner.arrangethings.model.MetadataConfig

/** Accent colour used for the small type label of every overlay. */
val OverlayAccent = Color(0xFFFFFFFF)

/** Colour of the overlay content. */
val OverlayContent = Color(0xFFFFFFFF)

/** Colour of the secondary line of a two line overlay. */
val OverlaySecondary = Color(0xB3FFFFFF)

/** Colour of a metadata label. */
val OverlayLabel = Color(0x99FFFFFF)

/** Colour of the album art placeholder. */
val AlbumArtBackground = Color(0xFF3A3A3A)

/** The width an overlay of this class takes in its slot. */
val OverlayWidth.size: Dp
	get() = when (this) {
		OverlayWidth.Narrow -> 170.dp
		OverlayWidth.Wide -> 240.dp
		OverlayWidth.ExtraWide -> 300.dp
	}

/** The height an overlay of this class takes in its slot. */
val OverlayHeight.size: Dp
	get() = when (this) {
		OverlayHeight.SingleLine -> 36.dp
		OverlayHeight.TwoLines -> 52.dp
		OverlayHeight.Block -> 60.dp
		OverlayHeight.Tall -> 128.dp
	}

/**
 * The content of an overlay.
 *
 * Each variant draws itself inside the shape it declared, so the editor can lay the slots out without
 * measuring anything.
 */
@Composable
fun OverlayContent(
	variant: OverlayVariant,
	metadata: MetadataConfig? = null,
	modifier: Modifier = Modifier
) {
	when (variant) {
		OverlayVariant.Clock -> LabelValue("TIME", "21:45", modifier)
		OverlayVariant.Date -> LabelValue("DATE", "Mon 19 Oct", modifier)
		OverlayVariant.Location -> LabelValue("PLACE", "Kitchen", modifier)
		OverlayVariant.WeatherNow -> LabelValue("WEATHER", "17° Cloudy", modifier)
		OverlayVariant.Weather3Day -> ForecastRow(modifier)
		OverlayVariant.NowPlayingText -> StackedText("Fleetwood Mac", "Rumours", modifier)
		OverlayVariant.NowPlayingAlbumArt -> AlbumArtRow(modifier)
		OverlayVariant.CustomText -> LabelValue("TEXT", "Overlay text", modifier)
		OverlayVariant.Metadata -> MetadataContent(metadata ?: MetadataConfig.Default, modifier)
	}
}

/** A label and a value on one line. */
@Composable
private fun LabelValue(label: String, value: String, modifier: Modifier = Modifier) {
	Row(
		modifier = modifier,
		verticalAlignment = Alignment.CenterVertically
	) {
		Text(text = label, color = OverlayAccent, fontSize = 11.sp, maxLines = 1)
		Spacer(Modifier.width(8.dp))
		Text(text = value, color = OverlayContent, fontSize = 15.sp, maxLines = 1)
	}
}

/** A primary and a secondary line, one above the other. */
@Composable
private fun StackedText(primary: String, secondary: String, modifier: Modifier = Modifier) {
	Column(
		modifier = modifier,
		verticalArrangement = Arrangement.Center
	) {
		Text(text = primary, color = OverlayContent, fontSize = 15.sp, maxLines = 1)
		Text(text = secondary, color = OverlaySecondary, fontSize = 13.sp, maxLines = 1)
	}
}

/** A square of album art with the track details next to it. */
@Composable
private fun AlbumArtRow(modifier: Modifier = Modifier) {
	Row(
		modifier = modifier,
		verticalAlignment = Alignment.CenterVertically
	) {
		Box(
			modifier = Modifier
				.size(38.dp)
				.clip(RoundedCornerShape(6.dp))
				.background(AlbumArtBackground)
				.border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(6.dp)),
			contentAlignment = Alignment.Center
		) {
			Text(text = "♫", color = OverlayContent, fontSize = 20.sp)
		}

		Spacer(Modifier.width(10.dp))

		StackedText("Fleetwood Mac", "Rumours")
	}
}

/** Three days of weather, each a day label above a temperature. */
@Composable
private fun ForecastRow(modifier: Modifier = Modifier) {
	Row(
		modifier = modifier.fillMaxWidth(),
		horizontalArrangement = Arrangement.SpaceEvenly,
		verticalAlignment = Alignment.CenterVertically
	) {
		ForecastDay("Mon", "18°")
		ForecastDay("Tue", "17°")
		ForecastDay("Wed", "15°")
	}
}

/** One day of the forecast. */
@Composable
private fun ForecastDay(day: String, temperature: String) {
	Column(
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.Center
	) {
		Text(text = day, color = OverlayAccent, fontSize = 11.sp, maxLines = 1)
		Text(text = temperature, color = OverlaySecondary, fontSize = 13.sp, maxLines = 1)
	}
}

@Preview(showBackground = true, widthDp = 640, heightDp = 220)
@Composable
private fun OverlayContentPreview() {
	Column {
		OverlayVariant.Clock.let { OverlayContent(it, modifier = Modifier.width(it.width.size)) }
		OverlayVariant.Weather3Day.let { OverlayContent(it, modifier = Modifier.width(it.width.size)) }
		OverlayVariant.NowPlayingAlbumArt.let { OverlayContent(it, modifier = Modifier.width(it.width.size)) }
		OverlayVariant.Metadata.let {
			OverlayContent(it, modifier = Modifier.width(it.width.size).height(it.height.size))
		}
	}
}
