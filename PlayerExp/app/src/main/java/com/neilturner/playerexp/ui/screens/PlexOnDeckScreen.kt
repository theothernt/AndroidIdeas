package com.neilturner.playerexp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.tween
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
    onNavigateToShow: (showRatingKey: String, showTitle: String?, episodeRatingKey: String?) -> Unit,
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
                    LatestShelves(current.shelves, posterSize, onPlay, onNavigateToShow, viewModel)
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
 *
 * Opening an episode leaves the screen for its show page, so each shelf remembers the card the focus
 * was on and which shelf it was on. Coming back puts the focus on that card again; the shelf the
 * user was on claims the initial focus and the other two are reached by moving down as before.
 */
@Composable
private fun LatestShelves(
    shelves: PlexOnDeckShelves,
    posterSize: PosterSize,
    onPlay: (ratingKey: String, title: String) -> Unit,
    onNavigateToShow: (showRatingKey: String, showTitle: String?, episodeRatingKey: String?) -> Unit,
    viewModel: PlexOnDeckViewModel
) {
    // Null on a first visit, where Continue Watching is the shelf to land on.
    val returningShelfId = viewModel.lastFocusedShelfId()

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
                OnDeckRow(
                    items = shelves.continueWatching,
                    posterSize = posterSize,
                    onPlay = onPlay,
                    rememberedKey = viewModel.focusedRatingKey(SHELF_CONTINUE_WATCHING),
                    claimsInitialFocus = returningShelfId == null || returningShelfId == SHELF_CONTINUE_WATCHING,
                    onFocused = { viewModel.rememberShelfFocus(SHELF_CONTINUE_WATCHING, it) }
                )
            }
        }
        if (shelves.latestEpisodes.isNotEmpty()) {
            Shelf(stringResource(R.string.plex_on_deck_latest_episodes)) {
                // Episodes open their show page rather than playing straight in, so the user can
                // pick from the full episode list; movies have no show page and play straight away.
                LibraryShelfRow(
                    items = shelves.latestEpisodes,
                    posterSize = posterSize,
                    rememberedKey = viewModel.focusedRatingKey(SHELF_LATEST_EPISODES),
                    claimsInitialFocus = returningShelfId == SHELF_LATEST_EPISODES,
                    onFocused = { viewModel.rememberShelfFocus(SHELF_LATEST_EPISODES, it) },
                    onItemSelected = { item ->
                        val showKey = item.showRatingKey
                        if (showKey != null) onNavigateToShow(showKey, item.showTitle, item.ratingKey)
                        else onPlay(item.ratingKey, item.title.orEmpty())
                    }
                )
            }
        }
        if (shelves.latestMovies.isNotEmpty()) {
            Shelf(stringResource(R.string.plex_on_deck_latest_movies)) {
                LibraryShelfRow(
                    items = shelves.latestMovies,
                    posterSize = posterSize,
                    rememberedKey = viewModel.focusedRatingKey(SHELF_LATEST_MOVIES),
                    claimsInitialFocus = returningShelfId == SHELF_LATEST_MOVIES,
                    onFocused = { viewModel.rememberShelfFocus(SHELF_LATEST_MOVIES, it) },
                    onItemSelected = { onPlay(it.ratingKey, it.title.orEmpty()) }
                )
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

/**
 * A row of library items, which play the same way Continue Watching items do.
 *
 * [rememberedKey] is the card this shelf held before the screen was left, so the row comes back to
 * it. [claimsInitialFocus] is true only for the shelf the user was last on, so exactly one row ever
 * asks for focus.
 */
@Composable
private fun LibraryShelfRow(
    items: List<PlexLibraryItem>,
    posterSize: PosterSize,
    rememberedKey: String?,
    claimsInitialFocus: Boolean,
    onFocused: (ratingKey: String) -> Unit,
    onItemSelected: (PlexLibraryItem) -> Unit
) {
    PlexPosterRow(
        itemCount = items.size,
        claimsInitialFocus = claimsInitialFocus,
        initialFocusIndex = items.indexOfFirst { it.ratingKey == rememberedKey }.coerceAtLeast(0),
        key = { index -> items[index].ratingKey },
        onItemFocused = { index -> onFocused(items[index].ratingKey) }
    ) { index, itemModifier ->
        val item = items[index]
        PlexPosterCard(
            imageUrl = item.thumb,
            posterSize = posterSize,
            modifier = itemModifier,
            onPressed = { onItemSelected(item) }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OnDeckRow(
    items: List<OnDeckItem>,
    posterSize: PosterSize,
    onPlay: (ratingKey: String, title: String) -> Unit,
    rememberedKey: String?,
    claimsInitialFocus: Boolean,
    onFocused: (ratingKey: String) -> Unit
) {
    PlexPosterRow(
        itemCount = items.size,
        claimsInitialFocus = claimsInitialFocus,
        initialFocusIndex = items.indexOfFirst { it.ratingKey == rememberedKey }.coerceAtLeast(0),
        // Keyed by ratingKey so focus and the remembered request follow the item, not the index.
        key = { index -> items[index].ratingKey },
        onItemFocused = { index -> onFocused(items[index].ratingKey) }
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
 *
 * The width is animated rather than set directly: a play position moves a few seconds at a time, and
 * snapping the bar to each new value reads as a stutter. Animating it also keeps the recomposition
 * cheap — only the width value moves, the surrounding layout is untouched.
 */
@Composable
fun ProgressOverlay(fraction: Float, modifier: Modifier = Modifier) {
    val target = fraction.coerceAtLeast(MIN_PROGRESS_FRACTION)
    val width by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(PROGRESS_ANIMATION_MILLIS),
        label = "progressWidth"
    )
    Box(
        modifier = modifier
            .height(PROGRESS_BAR_HEIGHT)
            .clip(PROGRESS_BAR_SHAPE)
            .background(PROGRESS_TRACK_COLOR)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(width)
                .fillMaxHeight()
                .clip(PROGRESS_BAR_SHAPE)
                .background(PlexAmber)
        )
    }
}

private val SHELF_SPACING = 12.dp

/** Shelf identities for the remembered focus. Stable, so a rename cannot strand the focus. */
private const val SHELF_CONTINUE_WATCHING = "continueWatching"
private const val SHELF_LATEST_EPISODES = "latestEpisodes"
private const val SHELF_LATEST_MOVIES = "latestMovies"

/** Room at the top and bottom of the scrolling page for a shelf and its focused card. */
private val SHELF_EDGE_PADDING = 16.dp
private val SHELF_TITLE_GAP = 8.dp
val POSTER_BAR_INSET = 10.dp
val PROGRESS_BAR_HEIGHT = 4.dp
private val PROGRESS_BAR_RADIUS = 2.dp
val PROGRESS_BAR_SHAPE = RoundedCornerShape(PROGRESS_BAR_RADIUS)

/** Unwatched part of the bar: solid black, so it reads on pale artwork. */
val PROGRESS_TRACK_COLOR = Color.Black

/** Smallest fill the bar will draw, so a barely-started item still reads as in progress. */
const val MIN_PROGRESS_FRACTION = 0.05f

/** How long the bar takes to slide to a new position, in milliseconds. */
const val PROGRESS_ANIMATION_MILLIS = 350
