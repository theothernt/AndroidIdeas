package com.neilturner.overlayparty.ui.main

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import org.koin.androidx.compose.koinViewModel

private const val SLIDESHOW_IMAGE_PATH = "file:///android_asset/slideshow/"

@Composable
fun ScreenTwo(
    modifier: Modifier = Modifier,
    viewModel: ScreenTwoViewModel = koinViewModel(),
) {
    val context = LocalContext.current
    val topStart by viewModel.topStartOverlay.collectAsState()
    val topEnd by viewModel.topEndOverlay.collectAsState()
    val bottomStart by viewModel.bottomStartOverlay.collectAsState()
    val bottomEnd by viewModel.bottomEndOverlay.collectAsState()
    val isOverlaysVisible by viewModel.isOverlaysVisible.collectAsState()

    val groupAlpha by animateFloatAsState(
        targetValue = if (isOverlaysVisible) 1f else 0f,
        animationSpec =
            tween(
                durationMillis =
                    if (isOverlaysVisible) {
                        ScreenTwoViewModel.FADE_IN_DURATION_MS.toInt()
                    } else {
                        ScreenTwoViewModel.FADE_OUT_DURATION_MS.toInt()
                    },
            ),
        label = "GroupOverlayFade",
    )

    OverlayCanvas(
        background = {
            AsyncImage(
                model =
                    ImageRequest
                        .Builder(context)
                        .data(SLIDESHOW_IMAGE_PATH + "tomo_m_wegl4zgnhpe_unsplash.jpg")
                        .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        },
        topStart = topStart,
        topEnd = topEnd,
        bottomStart = bottomStart,
        bottomEnd = bottomEnd,
        modifier = modifier,
        overlaysAlpha = groupAlpha,
    )
}
