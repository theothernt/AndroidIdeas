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

data class SlideshowData(
	val bitmap: Bitmap,
	val rotation: Float
)

/**
 * Software-based stack blur for Android 11 and below.
 * Compose's blur modifier requires Android 12+ for hardware rendering.
 * This is a fast approximation that looks similar to Gaussian blur.
 */
private fun blurBitmap(bitmap: Bitmap, radius: Int = 25): Bitmap {
	// Convert HARDWARE bitmap to SOFTWARE for pixel access
	val softwareBitmap = if (bitmap.config == Bitmap.Config.HARDWARE) {
		bitmap.copy(Bitmap.Config.ARGB_8888, false)
	} else {
		bitmap
	}

	val width = softwareBitmap.width
	val height = softwareBitmap.height
	val pixels = IntArray(width * height)
	softwareBitmap.getPixels(pixels, 0, width, 0, 0, width, height)
	
	val stackSize = radius * 2 + 1
	
	// Horizontal pass
	val horizontallyBlurred = IntArray(width * height)
	for (y in 0 until height) {
		var r = 0
		var g = 0
		var b = 0
		var a = 0
		
		// Initialize stack with first pixel
		for (i in 0 until radius) {
			val pixel = pixels[y * width + 0]
			a += pixel ushr 24 and 0xFF
			r += pixel ushr 16 and 0xFF
			g += pixel ushr 8 and 0xFF
			b += pixel and 0xFF
		}
		
		for (x in 0 until width) {
			// Add rightmost pixel to stack
			val addIndex = minOf(x + radius, width - 1)
			val addPixel = pixels[y * width + addIndex]
			a += addPixel ushr 24 and 0xFF
			r += addPixel ushr 16 and 0xFF
			g += addPixel ushr 8 and 0xFF
			b += addPixel and 0xFF
			
			// Remove leftmost pixel from stack
			if (x > radius) {
				val removeIndex = x - radius - 1
				val removePixel = pixels[y * width + removeIndex]
				a -= removePixel ushr 24 and 0xFF
				r -= removePixel ushr 16 and 0xFF
				g -= removePixel ushr 8 and 0xFF
				b -= removePixel and 0xFF
			}
			
			val stackCount = minOf(x + radius + 1, width, stackSize)
			horizontallyBlurred[y * width + x] = 
				(a / stackCount and 0xFF shl 24) or
				(r / stackCount and 0xFF shl 16) or
				(g / stackCount and 0xFF shl 8) or
				(b / stackCount and 0xFF)
		}
	}
	
	// Vertical pass
	val result = IntArray(width * height)
	for (x in 0 until width) {
		var r = 0
		var g = 0
		var b = 0
		var a = 0
		
		for (i in 0 until radius) {
			val pixel = horizontallyBlurred[0 * width + x]
			a += pixel ushr 24 and 0xFF
			r += pixel ushr 16 and 0xFF
			g += pixel ushr 8 and 0xFF
			b += pixel and 0xFF
		}
		
		for (y in 0 until height) {
			val addIndex = minOf(y + radius, height - 1)
			val addPixel = horizontallyBlurred[addIndex * width + x]
			a += addPixel ushr 24 and 0xFF
			r += addPixel ushr 16 and 0xFF
			g += addPixel ushr 8 and 0xFF
			b += addPixel and 0xFF
			
			if (y > radius) {
				val removeIndex = y - radius - 1
				val removePixel = horizontallyBlurred[removeIndex * width + x]
				a -= removePixel ushr 24 and 0xFF
				r -= removePixel ushr 16 and 0xFF
				g -= removePixel ushr 8 and 0xFF
				b -= removePixel and 0xFF
			}
			
			val stackCount = minOf(y + radius + 1, height, stackSize)
			result[y * width + x] = 
				(a / stackCount and 0xFF shl 24) or
				(r / stackCount and 0xFF shl 16) or
				(g / stackCount and 0xFF shl 8) or
				(b / stackCount and 0xFF)
		}
	}
	
	val outputBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
	outputBitmap.setPixels(result, 0, width, 0, 0, width, height)
	return outputBitmap
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
									.alpha(0.2f)
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
									.alpha(0.2f)
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