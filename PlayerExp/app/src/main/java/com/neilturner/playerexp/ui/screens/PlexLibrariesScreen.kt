package com.neilturner.playerexp.ui.screens

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.neilturner.playerexp.R
import com.neilturner.playerexp.data.plex.PlexLibraryShelf
import com.neilturner.playerexp.ui.viewmodels.PlexLibrariesUiState
import com.neilturner.playerexp.ui.viewmodels.PlexLibrariesViewModel

/**
 * The library shelves, drawn the same way as On Deck: a heading at the top of the screen and poster
 * rows underneath, one per library.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PlexLibrariesScreen(
    modifier: Modifier = Modifier,
    viewModel: PlexLibrariesViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val posterSize = rememberPosterSize()

    LaunchedEffect(posterSize) {
        viewModel.loadLibraries(posterSize.pixels)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(vertical = 24.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            when (val current = state) {
                is PlexLibrariesUiState.Loading -> CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary
                )

                is PlexLibrariesUiState.NotAuthorised -> StatusMessage(
                    text = stringResource(R.string.plex_on_deck_not_authorised)
                )

                is PlexLibrariesUiState.Error -> StatusMessage(text = current.message)

                is PlexLibrariesUiState.Success -> LibrariesShelves(current.shelves, posterSize)
            }
        }
    }
}

/**
 * One labelled row per library, stacked down the screen. A row only takes focus while it is empty of
 * it, so arriving here lands on the first poster of the first row and moving down reaches the next
 * row rather than walking along the first one.
 */
@Composable
private fun LibrariesShelves(shelves: List<PlexLibraryShelf>, posterSize: PosterSize) {
    // Both shelves are drawn at the same card size, which means the lower one runs past the bottom
    // of the screen rather than being shrunk to fit. The column scrolls instead, so moving down to
    // the Movies shelf brings it fully into view.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(SHELF_SPACING)
    ) {
        shelves.forEachIndexed { shelfIndex, shelf ->
            Column {
                Text(
                    text = shelf.sectionTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(
                        start = SCREEN_HORIZONTAL_PADDING,
                        end = SCREEN_HORIZONTAL_PADDING,
                        bottom = SHELF_TITLE_GAP
                    )
                )
                if (shelf.items.isEmpty()) {
                    Text(
                        text = stringResource(R.string.plex_libraries_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = SCREEN_HORIZONTAL_PADDING)
                    )
                } else {
                    PlexPosterRow(
                        itemCount = shelf.items.size,
                        // Focus lands on the first shelf; moving down reaches the others.
                        claimsInitialFocus = shelfIndex == 0,
                        key = { index -> shelf.items[index].ratingKey }
                    ) { index, itemModifier ->
                        PlexPosterCard(
                            imageUrl = shelf.items[index].thumb,
                            posterSize = posterSize,
                            modifier = itemModifier
                        )
                    }
                }
            }
        }
    }
}

private val SHELF_SPACING = 12.dp
private val SHELF_TITLE_GAP = 8.dp
