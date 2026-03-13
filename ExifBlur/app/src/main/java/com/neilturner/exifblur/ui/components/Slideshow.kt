package com.neilturner.exifblur.ui.components

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Size
import coil3.transform.Transformation
import com.neilturner.exifblur.utils.FastBlurCompat

data class SlideshowData(
	val bitmap: Bitmap,
	val rotation: Float
)

/**
 * Software-based blur for Android 11 and below.
 * Compose's blur modifier requires Android 12+ for hardware rendering.
 * Uses FastBlurCompat for a high-quality box blur approximation.
 */
private fun blurBitmap(bitmap: Bitmap, radius: Int = 25): Bitmap {
	// FastBlurCompat handles HARDWARE bitmaps by creating a mutable copy
	return FastBlurCompat.applyBlur(bitmap, radius)
}

@Composable
fun Slideshow(
	currentImage: SlideshowData?,
	transitionDuration: Int,
	modifier: Modifier = Modifier
) {
	val context = LocalContext.current
	val isAndroid12OrHigher = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
	
	Box(
		modifier = modifier
			.fillMaxSize()
			.background(Color.Black)
	) {
		Crossfade(
			targetState = currentImage,
			animationSpec = tween(durationMillis = transitionDuration, easing = LinearEasing),
			label = "Slideshow Crossfade"
		) { image ->
			if (image != null) {
				Box(modifier = Modifier.fillMaxSize()) {
					// Background Image (fills screen and blurred)
					Box(
						modifier = Modifier
							.fillMaxSize()
							.clipToBounds()
					) {
						if (isAndroid12OrHigher) {
							// Android 12+: Use Compose blur modifier (hardware accelerated)
							AsyncImage(
								model = ImageRequest.Builder(context)
									.data(image.bitmap)
									.build(),
								contentDescription = "Blurred Background Image",
								contentScale = ContentScale.Crop,
								modifier = Modifier
									.fillMaxSize()
									.scale(2f)
									.rotate(image.rotation)
									.blur(radius = 25.dp)
									.alpha(0.5f)
							)
						} else {
							// Android 11 and below: Use software stack blur
							val blurredBitmap = remember { blurBitmap(image.bitmap) }
							val rotatedBitmap = remember {
								if (image.rotation != 0f) {
									val matrix = Matrix().apply { postRotate(image.rotation) }
									Bitmap.createBitmap(blurredBitmap, 0, 0, blurredBitmap.width, blurredBitmap.height, matrix, true)
								} else {
									blurredBitmap
								}
							}
							AsyncImage(
								model = rotatedBitmap,
								contentDescription = "Blurred Background Image",
								contentScale = ContentScale.Crop,
								modifier = Modifier
									.fillMaxSize()
									.scale(2f)
									.alpha(0.5f)
							)
						}
					}

					// Foreground Image (fitted)
					AsyncImage(
						model = image.bitmap,
						contentDescription = "Foreground Image",
						contentScale = ContentScale.None,
						modifier = Modifier
							.fillMaxSize()
							.rotate(image.rotation)
					)
				}
			} else {
				Box(modifier = Modifier
					.fillMaxSize()
					.background(Color.Black))
			}
		}
	}
}