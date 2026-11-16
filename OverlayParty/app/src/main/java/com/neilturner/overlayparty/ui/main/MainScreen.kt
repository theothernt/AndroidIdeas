package com.neilturner.overlayparty.ui.main

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import org.koin.androidx.compose.koinViewModel

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = koinViewModel(),
) {
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
                        MainViewModel.FADE_IN_DURATION_MS.toInt()
                    } else {
                        MainViewModel.FADE_OUT_DURATION_MS.toInt()
                    },
            ),
        label = "GroupOverlayFade",
    )

    OverlayCanvas(
        background = { Slideshow() },
        topStart = topStart,
        topEnd = topEnd,
        bottomStart = bottomStart,
        bottomEnd = bottomEnd,
        modifier = modifier,
        overlaysAlpha = groupAlpha,
    )
}
