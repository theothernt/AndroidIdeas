package com.neilturner.visualmashup.ui.screens.collage

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Size
import com.neilturner.visualmashup.data.ImageSource
import com.neilturner.visualmashup.data.SourceImage
import com.neilturner.visualmashup.ui.theme.VisualMashupTheme

private const val FADE_DURATION_MS = 500

/**
 * Shows the test images one at a time. Each image is held for 5 seconds by [CollageViewModel], then
 * the next image fades in over [FADE_DURATION_MS]. After the last image the sequence loops.
 */
@Composable
fun CollageScreen(modifier: Modifier = Modifier) {
    val viewModel: CollageViewModel = viewModel()
    val currentIndex by viewModel.currentIndex.collectAsStateWithLifecycle()

    CollageSlideshow(
        images = viewModel.images,
        currentIndex = currentIndex,
        modifier = modifier,
    )
}

@Composable
private fun CollageSlideshow(
    images: List<SourceImage>,
    currentIndex: Int,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    BoxWithConstraints(modifier = modifier) {
        val widthPx = with(density) { maxWidth.roundToPx() }
        val heightPx = with(density) { maxHeight.roundToPx() }

        Crossfade(
            targetState = currentIndex,
            animationSpec = tween(FADE_DURATION_MS),
            modifier = Modifier.fillMaxSize(),
        ) { index ->
            val image = images[index]
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(image.url)
                    .size(Size(widthPx, heightPx))
                    .crossfade(false) // Crossfade owns the transition between images
                    .build(),
                contentDescription = "Collage image ${image.id}",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 960, heightDp = 540)
@Composable
private fun CollageSlideshowPreview() {
    VisualMashupTheme {
        CollageSlideshow(
            images = ImageSource.images,
            currentIndex = 0,
        )
    }
}