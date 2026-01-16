package com.neilturner.exifblur.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest

@Composable
fun Slideshow(
    currentBitmap: Bitmap?,
    transitionDuration: Int,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Crossfade(
            targetState = currentBitmap,
            animationSpec = tween(durationMillis = transitionDuration),
            label = "Slideshow Crossfade"
        ) { bitmap ->
            if (bitmap != null) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Background Image (fills screen and blurred)
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(bitmap)
                            .build(),
                        contentDescription = "Blurred Background Image",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(radius = 25.dp)
                            .alpha(0.4f)
                    )

                    // Fallback for Android 11 or lower where blur might not work
                    if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.R) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.7f))
                        )
                    }
                    
                    // Foreground Image (fitted)
                    AsyncImage(
                        model = bitmap,
                        contentDescription = "Foreground Image",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black))
            }
        }
    }
}
