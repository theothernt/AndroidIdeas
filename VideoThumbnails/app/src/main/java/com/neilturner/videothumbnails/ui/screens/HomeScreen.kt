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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Size
import com.neilturner.videothumbnails.data.Video
import com.neilturner.videothumbnails.ui.components.CategoryRail
import com.neilturner.videothumbnails.ui.components.VideoItem
import com.neilturner.videothumbnails.ui.theme.RailSelectedLabel
import com.neilturner.videothumbnails.ui.theme.RailUnselectedLabel
import org.koin.androidx.compose.koinViewModel

private const val RAIL_WIDTH_DP = 200
private const val GRID_COLUMNS = 3

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedVideo by viewModel.selectedVideo.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val hiddenVideoIds by viewModel.hiddenVideoIds.collectAsState()
    val filteredVideos by viewModel.filteredVideos.collectAsState()
    val selectionCounts by viewModel.selectionCounts.collectAsState()

    var focusSelectedRailItem by remember { mutableStateOf<(() -> Unit)?>(null) }

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
                Row(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier =
                            Modifier
                                .width(RAIL_WIDTH_DP.dp)
                                .fillMaxHeight(),
                    ) {
                        CategoryRail(
                            categories = viewModel.categories,
                            selectedCategory = selectedCategory,
                            onSelectCategory = viewModel::selectCategory,
                            onSelectedFocusReady = { focusSelectedRailItem = it },
                            modifier = Modifier.weight(1f),
                        )

                        SelectionCounter(
                            counts = selectionCounts,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    VideoGrid(
                        videos = filteredVideos,
                        hiddenVideoIds = hiddenVideoIds,
                        onVideoClick = viewModel::toggleVideoHidden,
                        onVideoLongClick = viewModel::selectVideo,
                        onNavigateToCategoryRail = { focusSelectedRailItem?.invoke() },
                    )
                }
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
private fun SelectionCounter(
    counts: SelectionCounts,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .padding(start = 20.dp, end = 12.dp, bottom = 24.dp),
    ) {
        Text(
            text = "Videos selected:",
            style = MaterialTheme.typography.labelSmall,
            color = RailUnselectedLabel,
            maxLines = 1,
        )
        Text(
            text = "${counts.selected} of ${counts.total}",
            style = MaterialTheme.typography.titleMedium,
            color = RailSelectedLabel,
            maxLines = 1,
        )
    }
}

@Composable
fun VideoGrid(
    videos: List<Video>,
    hiddenVideoIds: Set<String>,
    onVideoClick: (Video) -> Unit,
    onVideoLongClick: (Video) -> Unit,
    onNavigateToCategoryRail: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val gridState = rememberLazyGridState()
    var focusedVideoId by remember { mutableStateOf<String?>(null) }

    val focusedCardInFirstColumn by remember(gridState) {
        derivedStateOf {
            val focusedId = focusedVideoId
            val visibleItems = gridState.layoutInfo.visibleItemsInfo
            val focusedItem = visibleItems.firstOrNull { it.key == focusedId }
            focusedItem != null && visibleItems.none { it.offset.x < focusedItem.offset.x }
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(GRID_COLUMNS),
        state = gridState,
        modifier =
            modifier
                .fillMaxSize()
                .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    event.key == Key.DirectionLeft &&
                    focusedCardInFirstColumn
                ) {
                    onNavigateToCategoryRail()
                    true
                } else {
                    false
                }
            },
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(
            items = videos,
            key = { video -> video.id },
            contentType = { "video_item" },
        ) { video ->
            VideoItem(
                video = video,
                isHidden = video.id in hiddenVideoIds,
                onClick = onVideoClick,
                onLongClick = onVideoLongClick,
                modifier = Modifier.onFocusChanged { if (it.isFocused) focusedVideoId = video.id },
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
    val safeVideo = video ?: return
    val context = LocalContext.current

    val exoPlayer =
        remember(context) {
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

    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(exoPlayer) {
        val snapshot = exoPlayer.playbackState
        exoPlayer.addListener(
            object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY && exoPlayer.playWhenReady) {
                        isLoading = false
                    }
                }
            },
        )
    }

    AnimatedVisibility(
        visible = true,
        enter =
            fadeIn(tween(300)) +
                scaleIn(
                    initialScale = 0.9f,
                    animationSpec = tween(300),
                ),
        exit =
            fadeOut(tween(200)) +
                scaleOut(
                    targetScale = 0.9f,
                    animationSpec = tween(200),
                ),
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
                onClick = {},
                modifier =
                    Modifier
                        .fillMaxWidth(0.6f)
                        .aspectRatio(16f / 9f),
                shape = CardDefaults.shape(RoundedCornerShape(16.dp)),
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    val assetPath = safeVideo.thumbnailAssetPath
                    if (assetPath != null) {
                        AsyncImage(
                            model =
                                ImageRequest
                                    .Builder(context)
                                    .data("file:///android_asset/$assetPath")
                                    .size(Size.ORIGINAL)
                                    .crossfade(false)
                                    .build(),
                            contentDescription = safeVideo.getDisplayTitle(),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

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

                    if (isLoading) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(48.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
