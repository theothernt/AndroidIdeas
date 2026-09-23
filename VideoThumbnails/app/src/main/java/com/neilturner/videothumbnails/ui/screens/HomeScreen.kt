package com.neilturner.videothumbnails.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.neilturner.videothumbnails.data.Video
import com.neilturner.videothumbnails.ui.components.VideoItem
import org.koin.androidx.compose.koinViewModel

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedVideo by viewModel.selectedVideo.collectAsState()

    BackHandler(enabled = selectedVideo != null) {
        viewModel.clearSelectedVideo()
    }

    Box(modifier = modifier.fillMaxSize()) {
        when (val state = uiState) {
            is VideoUiState.Loading -> {
                Text(
                    text = "Loading...",
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            is VideoUiState.Success -> {
                VideoGrid(
                    videos = state.videos,
                    onVideoClick = { viewModel.selectVideo(it) },
                )
            }

            is VideoUiState.Error -> {
                Text(
                    text = "Error: ${state.message}",
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }

        VideoPreviewOverlay(
            video = selectedVideo,
            onDismiss = { viewModel.clearSelectedVideo() },
        )
    }
}

@Composable
fun VideoGrid(
    videos: List<Video>,
    onVideoClick: (Video) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val gridState = rememberLazyGridState()

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(5),
        state = gridState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        itemsIndexed(
            items = videos,
            key = { _, video -> video.id },
            contentType = { _, _ -> "video_item" },
        ) { index, video ->
            VideoItem(
                video = video,
                onClick = onVideoClick,
                modifier = if (index == 0) Modifier.focusRequester(focusRequester) else Modifier,
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun VideoPreviewOverlay(
    video: Video?,
    onDismiss: () -> Unit,
) {
    AnimatedVisibility(
        visible = video != null,
        enter = fadeIn(tween(600)),
        exit = fadeOut(tween(600)),
    ) {
        val safeVideo = video ?: return@AnimatedVisibility
        val context = LocalContext.current

        val exoPlayer = remember(context) {
            ExoPlayer.Builder(context).build()
        }

        DisposableEffect(exoPlayer) {
            onDispose { exoPlayer.release() }
        }

        val preferredUrl = safeVideo.getPreferredVideoUrl()
        LaunchedEffect(preferredUrl, exoPlayer) {
            val mediaItem = MediaItem.fromUri(preferredUrl)
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.8f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Card(
                onClick = {},
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .aspectRatio(16f / 9f),
                shape = CardDefaults.shape(RoundedCornerShape(16.dp)),
            ) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = false
                        }
                    },
                    update = { playerView ->
                        playerView.player = exoPlayer
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}