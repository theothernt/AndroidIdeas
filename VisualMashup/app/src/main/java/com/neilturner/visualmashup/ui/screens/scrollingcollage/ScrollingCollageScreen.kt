package com.neilturner.visualmashup.ui.screens.scrollingcollage

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.neilturner.visualmashup.data.CollageMetrics
import com.neilturner.visualmashup.data.ImageSource
import com.neilturner.visualmashup.data.SourceImage
import com.neilturner.visualmashup.data.maxImageSize
import com.neilturner.visualmashup.ui.components.OverlayChip
import com.neilturner.visualmashup.ui.theme.VisualMashupTheme
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val CELL_GUTTER = CollageMetrics.GUTTER_DP.dp
private val CELL_BORDER_WIDTH = CollageMetrics.BORDER_WIDTH_DP.dp
private val CELL_BORDER_COLOR = Color.White

private const val COLUMNS = 3

/** The auto-scroll speed. */
private val SCROLL_SPEED = 28.dp

/** How far the D-pad scrolls per press, as a fraction of the viewport. */
private const val PAGE_SCROLL_FRACTION = 0.8f

/** Tiles are cropped to fill a tidy height range instead of following every extreme panorama. */
private const val MIN_TILE_ASPECT = 0.65f
private const val MAX_TILE_ASPECT = 1.5f

/**
 * An endless vertical collage: images are laid out in a staggered grid that scrolls down slowly on
 * its own. New cycles are loaded as the viewport approaches the end, so the images repeat.
 *
 * The D-pad scrolls a page at a time (which pauses the auto-scroll); the select button toggles the
 * auto-scroll back on.
 */
@Composable
fun ScrollingCollageScreen(modifier: Modifier = Modifier) {
    val viewModel: ScrollingCollageViewModel = viewModel()
    val items by viewModel.items.collectAsStateWithLifecycle()
    val gridState = rememberLazyStaggeredGridState()
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    var autoScroll by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    // Append another cycle of images before the viewport reaches the end of the list.
    LaunchedEffect(gridState, viewModel) {
        snapshotFlow { gridState.firstVisibleItemIndex }
            .collect { viewModel.ensureItems(it) }
    }

    // Scroll down a fraction of a pixel per frame so the movement stays smooth on 60/120 Hz TVs.
    LaunchedEffect(gridState, autoScroll) {
        if (!autoScroll) return@LaunchedEffect

        val speedPxPerSecond = with(density) { SCROLL_SPEED.toPx() }
        var lastFrameNanos = withFrameNanos { it }
        while (true) {
            val frameNanos = withFrameNanos { it }
            val elapsedSeconds = (frameNanos - lastFrameNanos) / 1_000_000_000f
            lastFrameNanos = frameNanos
            if (elapsedSeconds > 0f) {
                gridState.scrollBy(speedPxPerSecond * elapsedSeconds)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyUp) return@onKeyEvent false

                when (event.key) {
                    Key.DirectionDown -> {
                        autoScroll = false
                        scope.launch {
                            gridState.animateScrollBy(
                                gridState.layoutInfo.viewportSize.height * PAGE_SCROLL_FRACTION,
                            )
                        }
                        true
                    }

                    Key.DirectionUp -> {
                        autoScroll = false
                        scope.launch {
                            gridState.animateScrollBy(
                                -gridState.layoutInfo.viewportSize.height * PAGE_SCROLL_FRACTION,
                            )
                        }
                        true
                    }

                    Key.DirectionCenter, Key.Enter -> {
                        autoScroll = !autoScroll
                        true
                    }

                    else -> false
                }
            },
    ) {
        ScrollingGrid(
            images = items,
            gridState = gridState,
            modifier = Modifier.fillMaxSize(),
        )

        val position = if (items.isEmpty()) 0 else gridState.firstVisibleItemIndex % ImageSource.COUNT + 1
        OverlayChip(
            text = "Image $position / ${ImageSource.COUNT}",
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        )

        OverlayChip(
            text = if (autoScroll) "Auto-scroll on  •  OK to pause" else "Paused  •  OK to resume",
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp),
        )
    }
}

@Composable
private fun ScrollingGrid(
    images: List<SourceImage>,
    gridState: LazyStaggeredGridState,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val context = LocalContext.current

    BoxWithConstraints(modifier = modifier) {
        val columnWidth = (maxWidth - CELL_GUTTER * (COLUMNS + 1)) / COLUMNS
        val imageWidthPx = with(density) {
            (columnWidth - CELL_BORDER_WIDTH * 2).toPx().roundToInt()
        }

        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(COLUMNS),
            state = gridState,
            contentPadding = PaddingValues(CELL_GUTTER),
            horizontalArrangement = Arrangement.spacedBy(CELL_GUTTER),
            verticalItemSpacing = CELL_GUTTER,
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(
                items = images,
                key = { index, _ -> index },
            ) { _, image ->
                val aspect = (image.height.toFloat() / image.width).coerceIn(
                    MIN_TILE_ASPECT,
                    MAX_TILE_ASPECT,
                )
                val tileHeight = columnWidth * aspect
                val imageHeightPx = (imageWidthPx * aspect).roundToInt()

                Box(
                    modifier = Modifier
                        .size(width = columnWidth, height = tileHeight)
                        .border(CELL_BORDER_WIDTH, CELL_BORDER_COLOR)
                        .padding(CELL_BORDER_WIDTH),
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(image.url)
                            .size(maxImageSize(imageWidthPx, imageHeightPx))
                            .crossfade(true)
                            .build(),
                        contentDescription = "Collage image ${image.id}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 960, heightDp = 540)
@Composable
private fun ScrollingCollageScreenPreview() {
    VisualMashupTheme {
        ScrollingCollageScreen()
    }
}