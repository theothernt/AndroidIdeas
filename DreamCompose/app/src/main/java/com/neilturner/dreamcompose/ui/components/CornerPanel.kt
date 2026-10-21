package com.neilturner.dreamcompose.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * How long the slide in and the slide out of a panel take.
 */
internal const val SLIDE_DURATION_MS = 1000

/**
 * How long the fade in and the fade out of a panel take.
 */
internal const val FADE_DURATION_MS = 600

/**
 * The colour of the glow around the clock and the countdown.
 *
 * It is not fully opaque, because a wide glow at full brightness reads as a hard ring rather
 * than the haze it is meant to be.
 */
internal val GLOW_COLOUR = Color.White.copy(alpha = 0.4f)

/**
 * How opaque the background of the clock and the countdown is.
 */
internal const val PANEL_ALPHA = 0.5f

/**
 * How far the glow reaches out from the panel. This is the radius of the blur it is drawn with,
 * so a bigger one is a wider, softer halo.
 */
internal val GLOW_ELEVATION = 40.dp

/**
 * The glow around a panel, in the shape of the panel.
 */
internal fun Modifier.glow(shape: Shape) =
	shadow(
		elevation = GLOW_ELEVATION,
		shape = shape,
		ambientColor = GLOW_COLOUR,
		spotColor = GLOW_COLOUR,
	)

/**
 * A panel in a corner of the screen that slides in from off-screen.
 *
 * The panel starts off-screen, beyond the corner it is in, and slides on into it. It comes in
 * either sideways or downwards, never diagonally, and which of the two it is, is picked at
 * random.
 *
 * The panel stays hidden until [visible] is true, which is after the black screen that covers
 * the video at the start has faded out. [restartKey] brings the panel back in: every time it
 * changes, the panel slides in again. When [exiting] is true, the panel slides back out the way
 * it came.
 */
@Composable
internal fun CornerPanel(
	corner: ScreenCorner,
	visible: Boolean,
	exiting: Boolean,
	restartKey: Any,
	modifier: Modifier = Modifier,
	content: @Composable () -> Unit,
) {
	BoxWithConstraints(modifier = modifier.fillMaxSize()) {
		val density = LocalDensity.current
		val width = with(density) { maxWidth.toPx() }
		val height = with(density) { maxHeight.toPx() }

		key(restartKey) {
			val sideways = remember { Random.nextBoolean() }
			// The off-screen position the panel comes from, and goes back to.
			val startX =
				if (sideways) {
					if (corner.isEnd) width else -width
				} else {
					0f
				}
			val startY =
				if (sideways) {
					0f
				} else if (corner.isTop) {
					-height
				} else {
					height
				}
			val offsetX = remember { Animatable(startX) }
			val offsetY = remember { Animatable(startY) }
			val fade = remember { Animatable(0f) }

			LaunchedEffect(visible, exiting) {
				if (!visible) {
					return@LaunchedEffect
				}
				val targetX = if (exiting) startX else 0f
				val targetY = if (exiting) startY else 0f
				val targetFade = if (exiting) 0f else 1f
				coroutineScope {
					launch {
						offsetX.animateTo(targetX, tween(durationMillis = SLIDE_DURATION_MS, easing = FastOutSlowInEasing))
					}
					launch {
						offsetY.animateTo(targetY, tween(durationMillis = SLIDE_DURATION_MS, easing = FastOutSlowInEasing))
					}
					launch {
						fade.animateTo(targetFade, tween(durationMillis = FADE_DURATION_MS))
					}
				}
			}

			Box(
				modifier =
					Modifier
						.fillMaxSize()
						.padding(16.dp),
				contentAlignment = corner.alignment,
			) {
				Box(
					modifier =
						Modifier.graphicsLayer {
							translationX = offsetX.value
							translationY = offsetY.value
							alpha = fade.value
						},
				) {
					content()
				}
			}
		}
	}
}
