package com.neilturner.fadeloop.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.neilturner.fadeloop.ui.common.MemoryMonitor
import org.koin.androidx.compose.koinViewModel

@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel = koinViewModel()
) {
    val videos by viewModel.videoList.collectAsState()

    if (videos.isNotEmpty()) {
        Box(modifier = Modifier.fillMaxSize()) {
            VideoPlayer(
                videos = videos,
                useSurfaceView = false
            )
        }
    }
}
