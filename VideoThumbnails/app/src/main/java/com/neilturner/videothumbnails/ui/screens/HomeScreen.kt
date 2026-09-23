package com.neilturner.videothumbnails.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Size
import com.neilturner.videothumbnails.data.Video
import com.neilturner.videothumbnails.ui.components.VideoItem
import kotlinx.coroutines.launch
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
        enter = fadeIn(tween(300)) + scaleIn(tween(300), initialScale = 0.9f),
        exit = fadeOut(tween(300)) + scaleOut(tween(300), targetScale = 0.9f),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.8f))
                    .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Card(
                onClick = { },
                modifier =
                    Modifier
                        .fillMaxWidth(0.5f)
                        .aspectRatio(16f / 9f),
                shape = CardDefaults.shape(RoundedCornerShape(16.dp)),
            ) {
                val safeVideo = video ?: return@Card
                val assetPath = safeVideo.thumbnailAssetPath
                if (assetPath != null) {
                    AsyncImage(
                        model =
                            ImageRequest
                                .Builder(LocalContext.current)
                                .data("file:///android_asset/$assetPath")
                                .size(Size(960, 540))
                                .build(),
                        contentDescription = safeVideo.getDisplayTitle(),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}
