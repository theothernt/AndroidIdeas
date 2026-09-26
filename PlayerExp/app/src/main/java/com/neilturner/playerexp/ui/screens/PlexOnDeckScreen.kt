package com.neilturner.playerexp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.neilturner.playerexp.R
import com.neilturner.playerexp.data.plex.OnDeckItem
import com.neilturner.playerexp.ui.theme.PlexAmber
import com.neilturner.playerexp.ui.viewmodels.PlexOnDeckUiState
import com.neilturner.playerexp.ui.viewmodels.PlexOnDeckViewModel

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PlexOnDeckScreen(
    modifier: Modifier = Modifier,
    viewModel: PlexOnDeckViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val posterSize = rememberPosterSize()

    // Keyed on the size so a resolution change re-asks Plex for posters at the new size.
    LaunchedEffect(posterSize) {
        viewModel.loadOnDeck(posterSize.pixels)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(vertical = 24.dp)
    ) {
        Text(
            text = stringResource(R.string.plex_on_deck_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = SCREEN_HORIZONTAL_PADDING)
        )
        Spacer(Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            // The row sits under the title rather than floating in the middle of the screen, so the
            // heading and the first poster share a top edge.
            contentAlignment = Alignment.TopCenter
        ) {
            when (val current = state) {
                is PlexOnDeckUiState.Loading -> CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary
                )

                is PlexOnDeckUiState.NotAuthorised -> StatusMessage(
                    text = stringResource(R.string.plex_on_deck_not_authorised)
                )

                is PlexOnDeckUiState.Error -> StatusMessage(text = current.message)

                is PlexOnDeckUiState.Success -> if (current.items.isEmpty()) {
                    StatusMessage(text = stringResource(R.string.plex_on_deck_empty))
                } else {
                    OnDeckRow(current.items, posterSize)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OnDeckRow(items: List<OnDeckItem>, posterSize: PosterSize) {
    val firstPosterFocusRequester = remember { FocusRequester() }
    LaunchedEffect(items) {
        if (items.isNotEmpty()) {
            firstPosterFocusRequester.requestFocus()
        }
    }

    PlexPosterRow(
        itemCount = items.size,
        initialFocusRequester = firstPosterFocusRequester,
        // Keyed by ratingKey so focus and the remembered request follow the item, not the index.
        key = { index -> items[index].ratingKey }
    ) { index, itemModifier ->
        val item = items[index]
        PlexPosterCard(
            imageUrl = item.thumb,
            posterSize = posterSize,
            modifier = itemModifier
        ) {
            ProgressOverlay(
                fraction = item.progressFraction,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(
                        start = POSTER_BAR_INSET,
                        end = POSTER_BAR_INSET,
                        bottom = POSTER_BAR_INSET
                    )
            )
        }
    }
}

/**
 * Thin bar drawn inside the poster bounds, inset from the bottom and sides. Everything in this row
 * is something worth resuming, so the bar always draws. A position of a few seconds is too small a
 * sliver to read as progress, so the fill has a floor.
 */
@Composable
private fun ProgressOverlay(fraction: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(PROGRESS_BAR_HEIGHT)
            .clip(PROGRESS_BAR_SHAPE)
            .background(PROGRESS_TRACK_COLOR)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceAtLeast(MIN_PROGRESS_FRACTION))
                .fillMaxHeight()
                .clip(PROGRESS_BAR_SHAPE)
                .background(PlexAmber)
        )
    }
}

private val POSTER_BAR_INSET = 10.dp
private val PROGRESS_BAR_HEIGHT = 4.dp
private val PROGRESS_BAR_RADIUS = 2.dp
private val PROGRESS_BAR_SHAPE = RoundedCornerShape(PROGRESS_BAR_RADIUS)

/** Unwatched part of the bar: solid black, so it reads on pale artwork. */
private val PROGRESS_TRACK_COLOR = Color.Black

/** Smallest fill the bar will draw, so a barely-started item still reads as in progress. */
private const val MIN_PROGRESS_FRACTION = 0.05f
