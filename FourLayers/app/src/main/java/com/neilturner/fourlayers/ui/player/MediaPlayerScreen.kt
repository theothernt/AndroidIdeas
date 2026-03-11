package com.neilturner.fourlayers.ui.player

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.neilturner.fourlayers.data.MediaProvider
import org.koin.androidx.compose.koinViewModel

@Composable
fun MediaPlayerScreen() {
    val viewModel: MediaPlayerViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        // Only load playlist if not already loaded
        if (state.playlist.isEmpty()) {
            val playlist = MediaProvider.getSamplePlaylist()
            viewModel.loadPlaylist(playlist)
        }
    }

    MediaPlayer(
        viewModel = viewModel,
        modifier = Modifier.fillMaxSize()
    )
}
