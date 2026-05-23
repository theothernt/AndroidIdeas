package com.neilturner.altfade.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.neilturner.altfade.data.repository.VideoRepository
import com.neilturner.altfade.ui.components.CrossfadeVideoPlayer

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    videoRepository: VideoRepository = remember { VideoRepository() }
) {
    val videos = remember { videoRepository.getVideos() }
    
    if (videos.isNotEmpty()) {
        CrossfadeVideoPlayer(
            videos = videos,
            modifier = modifier.fillMaxSize()
        )
    }
}
