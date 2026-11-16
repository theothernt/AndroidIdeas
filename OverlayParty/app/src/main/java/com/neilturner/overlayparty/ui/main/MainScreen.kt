package com.neilturner.overlayparty.ui.main

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

    OverlayCanvas(
        background = { Slideshow() },
        topStart = topStart,
        topEnd = topEnd,
        bottomStart = bottomStart,
        bottomEnd = bottomEnd,
        modifier = modifier,
    )
}
