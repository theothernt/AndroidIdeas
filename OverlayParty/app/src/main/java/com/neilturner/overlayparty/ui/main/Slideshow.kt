package com.neilturner.overlayparty.ui.main

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import kotlinx.coroutines.delay

@Composable
fun Slideshow(
    modifier: Modifier = Modifier,
    delayMillis: Long = 12000L,
    fadeDurationMillis: Int = 500,
) {
    val context = LocalContext.current

    // Calculate screen size in pixels to limit memory usage
    val windowInfo = LocalWindowInfo.current
    val containerSize = windowInfo.containerSize

    val screenWidthPx = containerSize.width
    val screenHeightPx = containerSize.height
    // A 720p background is enough for a blurred/photo backdrop and is much cheaper to sample.
    val backgroundWidthPx = screenWidthPx * 2 / 3
    val backgroundHeightPx = screenHeightPx * 2 / 3

    // Get all images from assets/slideshow
    val imagePaths =
        remember {
            context.assets
                .list("slideshow")
                ?.filter { it.endsWith(".jpg", ignoreCase = true) || it.endsWith(".png", ignoreCase = true) }
                ?.sorted()
                ?.map { "file:///android_asset/slideshow/$it" }
                ?: emptyList()
        }

    if (imagePaths.isEmpty()) return
    var currentIndex by remember { mutableIntStateOf(0) }
    val alpha = remember { Animatable(1f) }

    // Fade through black instead of crossfading two full-screen images at once.
    LaunchedEffect(Unit) {
        while (true) {
            delay(delayMillis)
            alpha.animateTo(0f, animationSpec = tween(fadeDurationMillis))
            currentIndex = (currentIndex + 1) % imagePaths.size
            alpha.animateTo(1f, animationSpec = tween(fadeDurationMillis))
        }
    }

    AsyncImage(
        model =
            ImageRequest
                .Builder(context)
                .data(imagePaths[currentIndex])
                .size(backgroundWidthPx, backgroundHeightPx)
                .build(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        alpha = alpha.value,
        modifier = modifier.fillMaxSize(),
    )
}
