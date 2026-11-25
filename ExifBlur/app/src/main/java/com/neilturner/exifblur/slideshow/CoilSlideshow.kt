package com.neilturner.exifblur.slideshow

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import com.neilturner.exifblur.data.SmbImage

private const val BLUR_RADIUS = 25
/** Crossfades between two already-decoded images using the shared request. */
@Composable
fun CoilSlideshow(
    image: SmbImage?,
    imageLoader: ImageLoader,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val appContext = remember(context) { context.applicationContext }
    val targetSize = remember(appContext) { currentDisplaySize(appContext) }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(
            targetState = image,
            animationSpec =
                tween(durationMillis = SLIDESHOW_TRANSITION_MS, easing = LinearEasing),
            label = "CoilSlideshow",
        ) { item ->
            if (item != null) {
                val request =
                    remember(item, appContext, targetSize) {
                        buildSlideshowRequest(appContext, item, targetSize)
                    }
                Box(modifier = Modifier.fillMaxSize()) {
                    // Same request twice: both resolve from memory cache, no
                    // extra fetch or decode. Blur is a no-op below API 31.
                    AsyncImage(
                        model = request,
                        contentDescription = null,
                        imageLoader = imageLoader,
                        contentScale = ContentScale.Crop,
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .clipToBounds()
                                .scale(2f)
                                .blur(BLUR_RADIUS.dp)
                                .alpha(0.5f),
                    )
                    AsyncImage(
                        model = request,
                        contentDescription = null,
                        imageLoader = imageLoader,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}
