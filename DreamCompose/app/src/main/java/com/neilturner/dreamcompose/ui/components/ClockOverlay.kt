package com.neilturner.dreamcompose.ui.components

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.neilturner.dreamcompose.R
import kotlinx.coroutines.delay
import java.util.Date

/**
 * The font of the clock and the countdown. Google Sans is free to use, see
 * `licenses/GoogleSans-OFL.txt` in the project root.
 */
internal val GoogleSans =
	FontFamily(
		Font(R.font.google_sans_regular, FontWeight.Normal),
		Font(R.font.google_sans_medium, FontWeight.Medium),
	)

/**
 * The clock that is shown over the video.
 *
 * The clock is small and sits close to a corner of the screen. It slides in from off-screen
 * every time the video changes.
 */
@Composable
internal fun ClockOverlay(
	videoIndex: Int,
	corner: ScreenCorner,
	visible: Boolean,
	exiting: Boolean,
	modifier: Modifier = Modifier,
) {
	val time = currentTime()

	CornerPanel(
		corner = corner,
		visible = visible,
		exiting = exiting,
		restartKey = videoIndex,
		modifier = modifier,
	) {
		Text(
			text = time,
			modifier =
				Modifier
					.glow(RoundedCornerShape(10.dp))
					.background(
						color = Color.Black.copy(alpha = PANEL_ALPHA),
						shape = RoundedCornerShape(10.dp),
					).padding(horizontal = 12.dp, vertical = 6.dp),
			color = Color.White,
			fontFamily = GoogleSans,
			fontWeight = FontWeight.Medium,
			fontSize = 32.sp,
		)
	}
}

/**
 * The current time, as a string, following the 12 or 24 hour setting of the device. It is
 * updated whenever the minute changes.
 */
@Composable
private fun currentTime(): String {
	val context = LocalContext.current
	val format = remember(context) { DateFormat.getTimeFormat(context) }
	var time by remember { mutableStateOf(format.format(Date())) }
	LaunchedEffect(format) {
		while (true) {
			val now = Date()
			delay(60_000 - now.time % 60_000)
			time = format.format(Date())
		}
	}
	return time
}
