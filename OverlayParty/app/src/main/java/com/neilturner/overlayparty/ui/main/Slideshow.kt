package com.neilturner.overlayparty.ui.main

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import kotlinx.coroutines.delay

@Composable
fun Slideshow(
    modifier: Modifier = Modifier,
    delayMillis: Long = 12000L,
    crossfadeDurationMillis: Int = 2000
) {
    val context = LocalContext.current

    // Calculate screen size in pixels to limit memory usage
    val windowInfo = LocalWindowInfo.current
    val containerSize = windowInfo.containerSize

    val screenWidthPx = containerSize.width
    val screenHeightPx = containerSize.height

    // Get all images from assets/slideshow
    val imagePaths = remember {
        context.assets.list("slideshow")?.map { "file:///android_asset/slideshow/$it" } ?: emptyList()
    }

    if (imagePaths.isEmpty()) return
    var currentIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(delayMillis)
            currentIndex = (currentIndex + 1) % imagePaths.size
        }
    }

    Crossfade(
        targetState = imagePaths[currentIndex],
        animationSpec = tween(durationMillis = crossfadeDurationMillis, easing = LinearEasing),
        label = "Slideshow Crossfade",
        modifier = modifier.fillMaxSize()
    ) { imagePath ->
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(imagePath)
                .size(screenWidthPx, screenHeightPx)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}