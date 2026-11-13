package com.neilturner.visualmashup.ui.screens.simplecollage

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Size
import com.neilturner.visualmashup.data.CollageMetrics
import com.neilturner.visualmashup.data.CollagePlacement
import com.neilturner.visualmashup.data.maxImageSize
import com.neilturner.visualmashup.ui.components.OverlayChip
import com.neilturner.visualmashup.ui.theme.VisualMashupTheme
import kotlin.math.roundToInt

private val CELL_GUTTER = CollageMetrics.GUTTER_DP.dp
private val CELL_BORDER_WIDTH = CollageMetrics.BORDER_WIDTH_DP.dp
private val CELL_BORDER_COLOR = Color.White

/**
 * Shows a full-bleed collage that fills the whole screen. Every photo is framed with a border and a
 * gutter. Left/right on the D-pad cycles through the sample groups.
 */
@Composable
fun SimpleCollageScreen(modifier: Modifier = Modifier) {
    val viewModel: SimpleCollageViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp) {
                    when (event.key) {
                        Key.DirectionRight -> {
                            viewModel.next()
                            true
                        }

                        Key.DirectionLeft -> {
                            viewModel.previous()
                            true
                        }

                        else -> false
                    }
                } else {
                    false
                }
            },
    ) {
        FullBleedCollage(
            placement = uiState.placement,
            modifier = Modifier.fillMaxSize(),
        )

        val cropPercent = ((1f - uiState.placement.visibleAreaFraction) * 100).roundToInt()
        OverlayChip(
            text = "${uiState.placement.template.name}  •  " +
                uiState.placement.images.joinToString(" ") { it.orientation.name.first().toString() } +
                "  •  ${uiState.groupIndex + 1}/${uiState.groupCount}" +
                "  •  crop $cropPercent%",
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
        )
    }
}

/** Renders [placement] across the whole canvas; each image is cropped to fill its cell and framed. */
@Composable
private fun FullBleedCollage(
    placement: CollagePlacement,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    BoxWithConstraints(modifier = modifier) {
        val halfGutter = CELL_GUTTER / 2

        placement.template.cells.forEachIndexed { index, cell ->
            val image = placement.images.getOrNull(index) ?: return@forEachIndexed

            // The image request must match the size the AsyncImage actually occupies, otherwise the
            // preload in the ViewModel misses the memory cache.
            val cellWidthPx = with(density) {
                (maxWidth * cell.width - CELL_GUTTER - CELL_BORDER_WIDTH * 2).roundToPx()
            }
            val cellHeightPx = with(density) {
                (maxHeight * cell.height - CELL_GUTTER - CELL_BORDER_WIDTH * 2).roundToPx()
            }

            Box(
                modifier = Modifier
                    .offset(
                        x = maxWidth * cell.left + halfGutter,
                        y = maxHeight * cell.top + halfGutter,
                    )
                    .size(
                        width = maxWidth * cell.width - CELL_GUTTER,
                        height = maxHeight * cell.height - CELL_GUTTER,
                    )
                    .border(CELL_BORDER_WIDTH, CELL_BORDER_COLOR)
                    .padding(CELL_BORDER_WIDTH),
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(image.url)
                        .size(maxImageSize(cellWidthPx, cellHeightPx))
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

@Preview(showBackground = true, widthDp = 960, heightDp = 540)
@Composable
private fun SimpleCollageScreenPreview() {
    VisualMashupTheme {
        SimpleCollageScreen()
    }
}