package com.neilturner.dreamcompose.ui.components

import android.text.format.DateFormat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.neilturner.dreamcompose.R
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Date

/**
 * The font of the clock. Google Sans is free to use, see
 * `licenses/GoogleSans-OFL.txt` in the project root.
 */
private val GoogleSans =
	FontFamily(
		Font(R.font.google_sans_regular, FontWeight.Normal),
		Font(R.font.google_sans_medium, FontWeight.Medium),
	)

/**
 * The corners of the screen the clock can be in. They are used in order, so the clock moves on to
 * the next corner whenever the video changes. The clock always slides in from off-screen, from
 * the left for the start corners and from the right for the end corners.
 */
private enum class ScreenCorner(
	val alignment: Alignment,
	val isEnd: Boolean,
) {
	BottomStart(Alignment.BottomStart, isEnd = false),
	BottomEnd(Alignment.BottomEnd, isEnd = true),
	TopEnd(Alignment.TopEnd, isEnd = true),
	TopStart(Alignment.TopStart, isEnd = false),
}

/**
 * The clock that is shown over the video.
 *
 * The clock is small and sits close to a corner of the screen. Whenever the video changes
 * ([videoIndex]) it moves on to the next corner, sliding in from off-screen.
 */
@Composable
fun ClockOverlay(
	videoIndex: Int,
	modifier: Modifier = Modifier,
) {
	val time = currentTime()
	val corner = ScreenCorner.entries[videoIndex % ScreenCorner.entries.size]

	BoxWithConstraints(modifier = modifier.fillMaxSize()) {
		val density = LocalDensity.current
		val width = with(density) { maxWidth.toPx() }

		Box(
			modifier =
				Modifier
					.fillMaxSize()
					.padding(16.dp),
			contentAlignment = corner.alignment,
		) {
			// The clock is laid out in the corner of the current video and slid in from
			// off-screen. Keying it on the video index starts the slide from off-screen
			// without showing the clock in the middle of the screen first.
			key(videoIndex) {
				val offsetX = remember { Animatable(if (corner.isEnd) width else -width) }
				val offsetY = remember { Animatable(0f) }
				val fade = remember { Animatable(0f) }

				LaunchedEffect(Unit) {
					coroutineScope {
						launch {
							offsetX.animateTo(0f, tween(durationMillis = 1000, easing = FastOutSlowInEasing))
						}
						launch {
							offsetY.animateTo(0f, tween(durationMillis = 1000, easing = FastOutSlowInEasing))
						}
						launch {
							fade.animateTo(1f, tween(durationMillis = 600))
						}
					}
				}

				Text(
					text = time,
					modifier =
						Modifier
							.graphicsLayer {
								translationX = offsetX.value
								translationY = offsetY.value
								alpha = fade.value
							}.background(color = Color.Black, shape = RoundedCornerShape(10.dp))
							.padding(horizontal = 12.dp, vertical = 6.dp),
					color = Color.White,
					fontFamily = GoogleSans,
					fontWeight = FontWeight.Medium,
					fontSize = 32.sp,
				)
			}
		}
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
