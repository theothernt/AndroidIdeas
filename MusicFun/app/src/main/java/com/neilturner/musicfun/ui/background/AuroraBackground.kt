package com.neilturner.musicfun.ui.background

import android.graphics.Bitmap
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val BrandColors = listOf(
	Color(0xFF6650A4),
	Color(0xFFB04A8C),
	Color(0xFFFF8A3D),
)

private fun Color.luminanceValue(): Float {
	val argb = toArgb()
	val red = ((argb shr 16) and 0xFF) / 255f
	val green = ((argb shr 8) and 0xFF) / 255f
	val blue = (argb and 0xFF) / 255f
	return 0.2126f * red + 0.7152f * green + 0.0722f * blue
}

/**
 * Pulls a few usable colours out of the album art. Falls back to the brand gradient when the art is
 * missing or produces nothing usable (for example a plain white cover).
 */
private object ArtworkPalette {

	suspend fun colors(bitmap: Bitmap?): List<Color> = withContext(Dispatchers.Default) {
		if (bitmap == null) return@withContext BrandColors

		val palette = runCatching {
			Palette.from(bitmap).clearFilters().generate()
		}.getOrNull() ?: return@withContext BrandColors

		listOf(
			palette.vibrantSwatch,
			palette.darkVibrantSwatch,
			palette.lightVibrantSwatch,
			palette.darkMutedSwatch,
		).mapNotNull { swatch -> swatch?.rgb?.let { Color(it.toLong()) } }
			.filter { it.luminanceValue() in 0.05f..0.9f }
			.distinct()
			.take(3)
			.ifEmpty { BrandColors }
	}
}

/** Extracts the aurora colours from [artwork], re-running only when the bitmap changes. */
@Composable
fun rememberAuroraColors(artwork: Bitmap?): List<Color> {
	val colors by produceState(initialValue = BrandColors, artwork) {
		value = ArtworkPalette.colors(artwork)
	}
	return colors
}

/**
 * Slow, ambient background: three soft colour blobs drifting behind the content.
 *
 * The gradients are rasterised once and only their [graphicsLayer] transform changes per frame, so
 * this stays cheap on TV hardware.
 */
@Composable
fun AuroraBackground(
	colors: List<Color>,
	modifier: Modifier = Modifier,
) {
	val transition = rememberInfiniteTransition(label = "aurora")
	val time by transition.animateFloat(
		initialValue = 0f,
		targetValue = 1f,
		animationSpec = infiniteRepeatable(
			animation = tween(durationMillis = 24_000, easing = LinearEasing),
			repeatMode = RepeatMode.Restart,
		),
		label = "auroraTime",
	)

	val density = LocalDensity.current
	val maxOffsetX = with(density) { 160.dp.toPx() }
	val maxOffsetY = with(density) { 110.dp.toPx() }

	Box(modifier = modifier.background(Color.Black)) {
		colors.take(3).forEachIndexed { index, color ->
			val phase = time * 2f * PI.toFloat() + index * (2f * PI.toFloat() / 3f)
			val offsetX = sin(phase) * maxOffsetX
			val offsetY = cos(phase * 1.3f) * maxOffsetY
			val scale = 1f + 0.1f * sin(phase * 0.7f)

			Box(
				modifier = Modifier
					.align(Alignment.Center)
					.size(800.dp)
					.graphicsLayer {
						translationX = offsetX
						translationY = offsetY
						scaleX = scale
						scaleY = scale
					}
					.background(
						Brush.radialGradient(
							colors = listOf(color.copy(alpha = 0.55f), Color.Transparent),
						)
					)
			)
		}
	}
}