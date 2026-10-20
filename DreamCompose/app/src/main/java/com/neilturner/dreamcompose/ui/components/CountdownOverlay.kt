package com.neilturner.dreamcompose.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text

/**
 * The countdown to the end of the current clip, in the corner opposite to the clock.
 *
 * It counts the seconds down to zero, and the video changes when it reaches zero.
 */
@Composable
internal fun CountdownOverlay(
	videoIndex: Int,
	corner: ScreenCorner,
	seconds: Int,
	visible: Boolean,
	exiting: Boolean,
	modifier: Modifier = Modifier,
) {
	CornerPanel(
		corner = corner,
		visible = visible,
		exiting = exiting,
		restartKey = videoIndex,
		modifier = modifier,
	) {
		Text(
			text = seconds.toString(),
			modifier =
				Modifier
					.background(
						color = Color.Black.copy(alpha = 0.8f),
						shape = RoundedCornerShape(10.dp),
					).padding(horizontal = 12.dp, vertical = 6.dp),
			color = Color.White,
			fontFamily = GoogleSans,
			fontWeight = FontWeight.Medium,
			fontSize = 28.sp,
		)
	}
}
