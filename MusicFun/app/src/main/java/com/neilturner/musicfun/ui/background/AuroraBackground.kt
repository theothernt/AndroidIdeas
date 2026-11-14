package com.neilturner.musicfun.ui.background

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
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

private const val BlobDiameterDp = 800f
private const val BlobAlpha = 0.55f
private const val FieldResolution = 0.5f
private const val PaletteFadeMs = 420
private val DriftX = 160.dp
private val DriftY = 110.dp

/**
 * Paints the three blobs into one bitmap, off the main thread.
 *
 * [widthPx]/[heightPx] are the field size in screen pixels: the viewport plus enough margin for the
 * drift, so the layer never has to be resized or re-rasterised while it moves. The bitmap is drawn
 * at [FieldResolution] and scaled back up; the gradients are soft enough that the difference is
 * invisible and it keeps the one-off raster cheap.
 */
private fun renderField(
	colors: List<Color>,
	widthPx: Int,
	heightPx: Int,
	density: Float,
): ImageBitmap {
	val width = (widthPx * FieldResolution).roundToInt().coerceAtLeast(1)
	val height = (heightPx * FieldResolution).roundToInt().coerceAtLeast(1)
	val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
	val canvas = android.graphics.Canvas(bitmap)
	canvas.drawColor(android.graphics.Color.BLACK)

	val paint = Paint(Paint.ANTI_ALIAS_FLAG)
	val radius = BlobDiameterDp * density * FieldResolution / 2f
	val centerX = width / 2f
	val centerY = height / 2f
	val blobs = if (colors.size >= 3) colors else (colors + BrandColors).take(3)

	for (color in blobs) {
		paint.shader = RadialGradient(
			centerX,
			centerY,
			radius,
			intArrayOf(color.copy(alpha = BlobAlpha).toArgb(), android.graphics.Color.TRANSPARENT),
			null,
			Shader.TileMode.CLAMP,
		)
		canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
	}

	return bitmap.asImageBitmap()
}

/**
 * Slow, ambient background: three soft colour blobs drifting behind the content.
 *
 * The blobs are rasterised once into a single layer. The drift is read inside [graphicsLayer], so a
 * frame only updates that layer's transform: no recomposition, no layout, no re-recording of draw
 * commands, and one full-screen texture blend per frame instead of three scaled gradient layers.
 */
@Composable
fun AuroraBackground(
	colors: List<Color>,
	modifier: Modifier = Modifier,
) {
	val transition = rememberInfiniteTransition(label = "aurora")
	val drift = transition.animateFloat(
		initialValue = 0f,
		targetValue = 1f,
		animationSpec = infiniteRepeatable(
			animation = tween(durationMillis = 24_000, easing = LinearEasing),
			repeatMode = RepeatMode.Restart,
		),
		label = "auroraTime",
	)

	val density = LocalDensity.current
	val driftX = with(density) { DriftX.toPx() }
	val driftY = with(density) { DriftY.toPx() }

	BoxWithConstraints(
		modifier = modifier
			.background(Color.Black)
			.clipToBounds()
	) {
		val fieldWidthPx = constraints.maxWidth + (driftX * 2f).roundToInt()
		val fieldHeightPx = constraints.maxHeight + (driftY * 2f).roundToInt()

		val field by produceState<ImageBitmap?>(
			initialValue = null,
			colors,
			fieldWidthPx,
			fieldHeightPx,
			density.density,
		) {
			value = if (fieldWidthPx <= 0 || fieldHeightPx <= 0) {
				null
			} else {
				withContext(Dispatchers.Default) {
					renderField(colors, fieldWidthPx, fieldHeightPx, density.density)
				}
			}
		}

		// One crossfade per palette change (track change), not per frame.
		Crossfade(
			targetState = field,
			modifier = Modifier.fillMaxSize(),
			animationSpec = tween(durationMillis = PaletteFadeMs, easing = LinearEasing),
			label = "auroraPalette",
		) { shown ->
			Box(modifier = Modifier.fillMaxSize()) {
				if (shown != null) {
					Image(
						bitmap = shown,
						contentDescription = null,
						contentScale = ContentScale.FillBounds,
						modifier = Modifier
							.align(Alignment.Center)
							.requiredSize(
								width = with(density) { fieldWidthPx.toDp() },
								height = with(density) { fieldHeightPx.toDp() },
							)
							.graphicsLayer {
								val phase = drift.value * 2f * PI.toFloat()
								translationX = sin(phase) * driftX
								translationY = cos(phase * 1.3f) * driftY
								val scale = 1f + 0.04f * sin(phase * 0.7f)
								scaleX = scale
								scaleY = scale
							},
					)
				}
			}
		}
	}
}
