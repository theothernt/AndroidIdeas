package com.neilturner.exifblur.ui.components

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest

data class SlideshowData(
    val bitmap: Bitmap,
    val rotation: Float
)

@Composable
fun Slideshow(
    currentImage: SlideshowData?,
    currentBackgroundImage: SlideshowData?,
    transitionDuration: Int,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    Log.d("Slideshow", "Current image: ${currentImage?.bitmap?.width}x${currentImage?.bitmap?.height}")
    Log.d("Slideshow", "Background image: ${currentBackgroundImage?.bitmap?.width}x${currentBackgroundImage?.bitmap?.height}")
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Background Image (fills screen - use blurred version on Android 11 and below)
        Crossfade(
            targetState = currentBackgroundImage,
            animationSpec = tween(durationMillis = transitionDuration, easing = LinearEasing),
            label = "Background Crossfade"
        ) { backgroundImage ->
            if (backgroundImage != null) {
                Log.d("Slideshow", "Displaying background image")
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(backgroundImage)
                        .build(),
                    contentDescription = "Blurred Background Image",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .rotate(backgroundImage.rotation)
                        .alpha(0.4f)
                )
            } else {
                Log.d("Slideshow", "No background image, using fallback")
                // Fallback: Use main image with blur modifier for Android 12+
                currentImage?.let { image ->
                    AsyncImage(
                        model = image.bitmap,
                        contentDescription = "Fallback Background Image",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .rotate(image.rotation)
                            .blur(radius = 25.dp)
                            .alpha(0.4f)
                    )
                }
            }
        }
                    
        // Foreground Image (fills screen - always sharp)
        Crossfade(
            targetState = currentImage,
            animationSpec = tween(durationMillis = transitionDuration, easing = LinearEasing),
            label = "Foreground Crossfade"
        ) { image ->
            if (image != null) {
                AsyncImage(
                    model = image.bitmap,
                    contentDescription = "Foreground Image",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .rotate(image.rotation)
                )
            }
        }
    }
}
