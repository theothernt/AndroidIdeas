package com.neilturner.playerexp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Column
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
import com.neilturner.playerexp.data.plex.PlexLibraryItem
import com.neilturner.playerexp.data.plex.displayTitle
import com.neilturner.playerexp.ui.theme.PlexAmber
import com.neilturner.playerexp.ui.viewmodels.PlexOnDeckShelves
import com.neilturner.playerexp.ui.viewmodels.PlexOnDeckUiState
import com.neilturner.playerexp.ui.viewmodels.PlexOnDeckViewModel

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PlexOnDeckScreen(
    onPlay: (ratingKey: String, title: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlexOnDeckViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val posterSize = rememberPosterSize()

    // Keyed on the size so a resolution change re-asks Plex for posters at the new size.
    LaunchedEffect(posterSize) {
        viewModel.loadOnDeck(posterSize.pixels)
    }

    Column(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize(),
            // The shelves start at the top rather than floating in the middle of the screen.
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

                is PlexOnDeckUiState.Success -> if (current.shelves.isEmpty) {
                    StatusMessage(text = stringResource(R.string.plex_on_deck_empty))
                } else {
                    LatestShelves(current.shelves, posterSize, onPlay)
                }
            }
        }
    }
}

/**
 * The shelves, one under another, scrolling as a page.
 *
 * All three are drawn at the same card size, so the lower ones run past the bottom of the screen
 * rather than being shrunk to fit, and the page scrolls to bring them into view. A shelf with nothing
 * in it is left out rather than showing an empty heading.
 */
@Composable
private fun LatestShelves(
    shelves: PlexOnDeckShelves,
    posterSize: PosterSize,
    onPlay: (ratingKey: String, title: String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            // The shelves run to the edges of the screen, so the padding that keeps a row and its
            // focused card clear of the top and bottom edges lives inside the scroll rather than
            // around it. A focused card grows past the height it is laid out at and would otherwise
            // be clipped, and an inset outside the scroll draws a visible edge across the screen that
            // content is cut off at.
            .padding(
                top = SHELF_EDGE_PADDING,
                bottom = SHELF_EDGE_PADDING
            ),
        verticalArrangement = Arrangement.spacedBy(SHELF_SPACING)
    ) {
        if (shelves.continueWatching.isNotEmpty()) {
            Shelf(stringResource(R.string.plex_on_deck_continue_watching)) {
                OnDeckRow(shelves.continueWatching, posterSize, onPlay)
            }
        }
        if (shelves.latestEpisodes.isNotEmpty()) {
            Shelf(stringResource(R.string.plex_on_deck_latest_episodes)) {
                LibraryShelfRow(shelves.latestEpisodes, posterSize, onPlay)
            }
        }
        if (shelves.latestMovies.isNotEmpty()) {
            Shelf(stringResource(R.string.plex_on_deck_latest_movies)) {
                LibraryShelfRow(shelves.latestMovies, posterSize, onPlay)
            }
        }
    }
}

@Composable
private fun Shelf(heading: String, content: @Composable () -> Unit) {
    Column {
        Text(
            text = heading,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(
                start = SCREEN_HORIZONTAL_PADDING,
                end = SCREEN_HORIZONTAL_PADDING,
                bottom = SHELF_TITLE_GAP
            )
        )
        content()
    }
}

/** A row of library items, which play the same way Continue Watching items do. */
@Composable
private fun LibraryShelfRow(
    items: List<PlexLibraryItem>,
    posterSize: PosterSize,
    onPlay: (ratingKey: String, title: String) -> Unit
) {
    PlexPosterRow(
        itemCount = items.size,
        // Only the first shelf claims the initial focus; the others are reached by moving down.
        claimsInitialFocus = false,
        key = { index -> items[index].ratingKey }
    ) { index, itemModifier ->
        val item = items[index]
        PlexPosterCard(
            imageUrl = item.thumb,
            posterSize = posterSize,
            modifier = itemModifier,
            onPressed = { onPlay(item.ratingKey, item.title.orEmpty()) }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OnDeckRow(
    items: List<OnDeckItem>,
    posterSize: PosterSize,
    onPlay: (ratingKey: String, title: String) -> Unit
) {
    PlexPosterRow(
        itemCount = items.size,
        // Keyed by ratingKey so focus and the remembered request follow the item, not the index.
        key = { index -> items[index].ratingKey }
    ) { index, itemModifier ->
        val item = items[index]
        PlexPosterCard(
            imageUrl = item.thumb,
            posterSize = posterSize,
            modifier = itemModifier,
            onPressed = { onPlay(item.ratingKey, item.displayTitle) }
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

private val SHELF_SPACING = 12.dp

/** Room at the top and bottom of the scrolling page for a shelf and its focused card. */
private val SHELF_EDGE_PADDING = 16.dp
private val SHELF_TITLE_GAP = 8.dp
private val POSTER_BAR_INSET = 10.dp
private val PROGRESS_BAR_HEIGHT = 4.dp
private val PROGRESS_BAR_RADIUS = 2.dp
private val PROGRESS_BAR_SHAPE = RoundedCornerShape(PROGRESS_BAR_RADIUS)

/** Unwatched part of the bar: solid black, so it reads on pale artwork. */
private val PROGRESS_TRACK_COLOR = Color.Black

/** Smallest fill the bar will draw, so a barely-started item still reads as in progress. */
private const val MIN_PROGRESS_FRACTION = 0.05f
