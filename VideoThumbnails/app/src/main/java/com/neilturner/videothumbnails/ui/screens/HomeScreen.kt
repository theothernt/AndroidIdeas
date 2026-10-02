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
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
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
import com.neilturner.videothumbnails.ui.components.ShowHideAllButton
import com.neilturner.videothumbnails.ui.components.THUMBNAIL_FOCUSED_SCALE
import com.neilturner.videothumbnails.ui.components.VIDEO_LABEL_HEIGHT
import com.neilturner.videothumbnails.ui.components.VideoItem
import com.neilturner.videothumbnails.ui.theme.RailSelectedLabel
import com.neilturner.videothumbnails.ui.theme.RailUnselectedLabel
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

private const val RAIL_WIDTH_DP = 200
private const val GRID_COLUMNS = 3
private const val GRID_SPACING_DP = 25
private const val GRID_TOP_GAP_DP = 16
private const val GRID_EDGE_PADDING_DP = 16
private const val THUMBNAIL_REVEAL_STEP_MILLIS = 40
private const val THUMBNAIL_REVEAL_MAX_MILLIS = 120

private typealias FocusCallback = () -> Unit

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedVideo by viewModel.selectedVideo.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val committedCategory by viewModel.committedCategory.collectAsState()
    val hiddenVideoIds by viewModel.hiddenVideoIds.collectAsState()
    val filteredVideos by viewModel.filteredVideos.collectAsState()
    val selectionCounts by viewModel.selectionCounts.collectAsState()
    val selectedCategoryCounts by viewModel.selectedCategoryCounts.collectAsState()
    val freeSpaceBytes by viewModel.freeSpaceBytes.collectAsState()

    // Stable references: a fresh bound reference on each recomposition would be a new
    // instance, forcing the grid to remeasure and redraw every time the rail focus moves.
    val onSelectCategory = remember(viewModel) { viewModel::selectCategory }
    val onVideoClick = remember(viewModel) { viewModel::toggleVideoHidden }
    val onVideoLongClick = remember(viewModel) { viewModel::selectVideo }
    val onToggleCategoryVisibility = remember(viewModel) { viewModel::toggleCategoryVisibility }

    val focusSelectedRailItemState = remember { mutableStateOf<FocusCallback?>(null) }
    val focusGridState = remember { mutableStateOf<FocusCallback?>(null) }
    val showHideAllFocusRequester = remember { FocusRequester() }

    val density = LocalDensity.current
    var gridContentTopOffsetDp by remember { mutableStateOf(GRID_TOP_GAP_DP.dp) }

    // Identity-stable callbacks for VideoGrid so rail focus moves do not invalidate it.
    val onNavigateToCategoryRail: () -> Unit = remember {
        { focusSelectedRailItemState.value?.invoke() }
    }
    val onNavigateToGrid: () -> Unit = remember {
        { focusGridState.value?.invoke() }
    }
    val onNavigateToShowHideAllButton: () -> Unit = remember {
        { showHideAllFocusRequester.requestFocus() }
    }
    val onGridFocusReady: (FocusCallback) -> Unit = remember {
        { callback -> focusGridState.value = callback }
    }
    val onGridContentTopOffset: (Dp) -> Unit = remember {
        { value: Dp -> gridContentTopOffsetDp = value }
    }

    val isAllHiddenInCategory =
        selectedCategoryCounts.total > 0 && selectedCategoryCounts.selected == 0
    val showHideAllLabel = if (isAllHiddenInCategory) "Show All" else "Hide All"

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
                            onSelectCategory = onSelectCategory,
                            onSelectedFocusReady = { focusSelectedRailItemState.value = it },
                            onNavigateToGrid = onNavigateToGrid,
                            contentTopPaddingDp = gridContentTopOffsetDp,
                            modifier = Modifier.weight(1f),
                        )

                        SelectionCounter(
                            counts = selectionCounts,
                            freeSpaceBytes = freeSpaceBytes,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    Column(
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                    ) {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        start = GRID_SPACING_DP.dp,
                                        end = GRID_SPACING_DP.dp,
                                        top = GRID_TOP_GAP_DP.dp,
                                    ),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            ShowHideAllButton(
                                label = showHideAllLabel,
                                onClick = onToggleCategoryVisibility,
                                onNavigateDown = onNavigateToGrid,
                                onNavigateLeft = onNavigateToCategoryRail,
                                modifier = Modifier.focusRequester(showHideAllFocusRequester),
                            )
                        }

                        VideoGrid(
                            videos = filteredVideos,
                            hiddenVideoIds = hiddenVideoIds,
                            resetKey = committedCategory.id,
                            onVideoClick = onVideoClick,
                            onVideoLongClick = onVideoLongClick,
                            onNavigateToCategoryRail = onNavigateToCategoryRail,
                            onNavigateToShowHideAllButton = onNavigateToShowHideAllButton,
                            onGridFocusReady = onGridFocusReady,
                            onContentTopOffsetDp = onGridContentTopOffset,
                            modifier = Modifier.weight(1f),
                        )
                    }
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
    freeSpaceBytes: Long,
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
        if (freeSpaceBytes > 0L) {
            Text(
                text = "Free space",
                style = MaterialTheme.typography.labelSmall,
                color = RailUnselectedLabel,
                maxLines = 1,
                modifier = Modifier.padding(top = 12.dp),
            )
            Text(
                text = formatFreeSpace(freeSpaceBytes),
                style = MaterialTheme.typography.titleMedium,
                color = RailSelectedLabel,
                maxLines = 1,
            )
        }
    }
}

private fun formatFreeSpace(bytes: Long): String {
    val gibibytes = bytes / (1024.0 * 1024.0 * 1024.0)
    return "%.1f GB".format(gibibytes)
}

@Composable
fun VideoGrid(
    videos: List<Video>,
    hiddenVideoIds: Set<String>,
    resetKey: Any?,
    onVideoClick: (Video) -> Unit,
    onVideoLongClick: (Video) -> Unit,
    onNavigateToCategoryRail: () -> Unit,
    onNavigateToShowHideAllButton: () -> Unit,
    onGridFocusReady: ((() -> Unit) -> Unit) = {},
    onContentTopOffsetDp: (Dp) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val gridState = rememberLazyGridState()
    val gridFocusRequester = remember { FocusRequester() }
    val focusedCardRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val restoreCallback by rememberUpdatedState(onGridFocusReady)
    val contentTopOffsetCallback by rememberUpdatedState(onContentTopOffsetDp)
    val revealedVideoIds = remember(resetKey) { mutableSetOf<String>() }
    var focusedVideoId by remember { mutableStateOf<String?>(null) }
    var gridWidthPx by remember { mutableIntStateOf(0) }
    var gridTopInRootDp by remember { mutableStateOf(0.dp) }

    val density = LocalDensity.current
    val focusedCardOverflow =
        with(density) {
            val edgePaddingPx = GRID_EDGE_PADDING_DP.dp.toPx()
            val spacingPx = GRID_SPACING_DP.dp.toPx()
            val cardWidthPx =
                (
                    (gridWidthPx - 2 * edgePaddingPx - (GRID_COLUMNS - 1) * spacingPx) / GRID_COLUMNS
                ).coerceAtLeast(0f)
            val cardHeightPx = cardWidthPx / 16f * 9f + VIDEO_LABEL_HEIGHT.toPx()
            (cardHeightPx * (THUMBNAIL_FOCUSED_SCALE - 1f) / 2f).toDp()
        }
    val gridTopPadding = (GRID_TOP_GAP_DP.dp - focusedCardOverflow).coerceAtLeast(0.dp)
    val focusedCardOverflowPx = with(density) { focusedCardOverflow.toPx() }

    LaunchedEffect(gridTopInRootDp, gridTopPadding, focusedCardOverflow) {
        contentTopOffsetCallback(gridTopInRootDp + gridTopPadding + focusedCardOverflow)
    }

    LaunchedEffect(resetKey) {
        focusedVideoId = null
        // Without this the grid keeps its scroll offset across a category change, so a
        // new category opens part-way down instead of at its first item.
        gridState.scrollToItem(0)
    }

    LaunchedEffect(Unit) {
        restoreCallback {
            scope.launch {
                val targetId = focusedVideoId
                val targetVisible =
                    targetId != null && gridState.layoutInfo.visibleItemsInfo.any { it.key == targetId }
                if (targetVisible) {
                    focusedCardRequester.requestFocus()
                } else {
                    gridFocusRequester.requestFocus()
                }
            }
        }
    }

    val focusedCardInFirstColumn by remember(gridState) {
        derivedStateOf {
            val focusedId = focusedVideoId
            val visibleItems = gridState.layoutInfo.visibleItemsInfo
            val focusedItem = visibleItems.firstOrNull { it.key == focusedId }
            focusedItem != null && visibleItems.none { it.offset.x < focusedItem.offset.x }
        }
    }

    val focusedCardInFirstRow by remember(gridState, focusedCardOverflowPx) {
        derivedStateOf {
            val focusedId = focusedVideoId
            val visibleItems = gridState.layoutInfo.visibleItemsInfo
            val focusedItem = visibleItems.firstOrNull { it.key == focusedId }
            focusedItem != null &&
                visibleItems.none { it.offset.y < focusedItem.offset.y - focusedCardOverflowPx }
        }
    }

    val nudgeFocusedCardClearOfTopEdge: suspend () -> Unit = nudge@{
        val focusedId = focusedVideoId ?: return@nudge
        val focusedItem = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == focusedId }
        val slack = focusedItem?.offset?.y?.toFloat() ?: return@nudge
        val atListStart =
            gridState.firstVisibleItemIndex == 0 && gridState.firstVisibleItemScrollOffset == 0
        val delta = focusedCardOverflowPx - slack
        if (!atListStart && delta >= 1f) {
            gridState.scrollBy(-delta)
        }
    }

    val keepFocusedCardClearOfTopEdge: (String) -> Unit = { videoId ->
        scope.launch {
            if (gridState.layoutInfo.visibleItemsInfo.any { it.key == videoId }) {
                nudgeFocusedCardClearOfTopEdge()
            }
        }
    }

    LaunchedEffect(gridState, focusedCardOverflowPx) {
        snapshotFlow { gridState.isScrollInProgress }.collect { isScrolling ->
            if (!isScrolling) {
                nudgeFocusedCardClearOfTopEdge()
            }
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(GRID_COLUMNS),
        state = gridState,
        modifier =
            modifier
                .fillMaxWidth()
                .onSizeChanged { gridWidthPx = it.width }
                .onGloballyPositioned { gridTopInRootDp = with(density) { it.positionInRoot().y.toDp() } }
                .padding(top = gridTopPadding)
                .focusRequester(gridFocusRequester)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) {
                        false
                    } else if (event.key == Key.DirectionLeft && focusedCardInFirstColumn) {
                        onNavigateToCategoryRail()
                        true
                    } else if (event.key == Key.DirectionUp && focusedCardInFirstRow) {
                        onNavigateToShowHideAllButton()
                        true
                    } else {
                        false
                    }
                },
        contentPadding =
            PaddingValues(
                start = GRID_EDGE_PADDING_DP.dp,
                end = GRID_EDGE_PADDING_DP.dp,
                top = focusedCardOverflow,
                bottom = GRID_EDGE_PADDING_DP.dp,
            ),
        horizontalArrangement = Arrangement.spacedBy(GRID_SPACING_DP.dp),
        verticalArrangement = Arrangement.spacedBy(GRID_SPACING_DP.dp),
    ) {
        itemsIndexed(
            items = videos,
            key = { _, video -> video.id },
            contentType = { _, _ -> "video_item" },
        ) { index, video ->
            VideoItem(
                video = video,
                isHidden = video.id in hiddenVideoIds,
                onClick = onVideoClick,
                onLongClick = onVideoLongClick,
                revealDelayMillis =
                    if (video.id in revealedVideoIds) {
                        0
                    } else {
                        (index * THUMBNAIL_REVEAL_STEP_MILLIS).coerceAtMost(THUMBNAIL_REVEAL_MAX_MILLIS)
                    },
                onReveal = { revealedVideoIds += video.id },
                modifier =
                    Modifier
                        .then(
                            if (video.id == focusedVideoId) {
                                Modifier.focusRequester(focusedCardRequester)
                            } else {
                                Modifier
                            },
                        ).onFocusChanged {
                            if (it.isFocused) {
                                focusedVideoId = video.id
                                keepFocusedCardClearOfTopEdge(video.id)
                            }
                        },
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
