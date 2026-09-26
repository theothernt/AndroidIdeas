package com.neilturner.playerexp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.neilturner.playerexp.R
import com.neilturner.playerexp.data.plex.OnDeckItem
import com.neilturner.playerexp.data.plex.displayTitle
import com.neilturner.playerexp.ui.viewmodels.PlexOnDeckUiState
import com.neilturner.playerexp.ui.viewmodels.PlexOnDeckViewModel

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PlexOnDeckScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlexOnDeckViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 48.dp, vertical = 24.dp)
    ) {
        Text(
            text = stringResource(R.string.plex_on_deck_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(24.dp))

        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center
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
                    OnDeckRow(current.items)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        BackButton(onBack)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun OnDeckRow(items: List<OnDeckItem>) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(items) { item ->
            OnDeckCard(item)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun OnDeckCard(item: OnDeckItem) {
    Card(
        // Cards are focusable for d-pad navigation only; there is no click action yet.
        onClick = {},
        modifier = Modifier.width(200.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(POSTER_ASPECT_RATIO)
                .clip(MaterialTheme.shapes.medium)
        ) {
            if (item.thumb.isNotBlank()) {
                AsyncImage(
                    model = item.thumb,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            ProgressOverlay(
                fraction = item.progressFraction,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = item.displayTitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ProgressOverlay(fraction: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(MaterialTheme.shapes.extraSmall)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
        ) {
            if (fraction > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .height(4.dp)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun StatusMessage(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun BackButton(onBack: () -> Unit) {
    val backFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        backFocusRequester.requestFocus()
    }
    Button(
        onClick = onBack,
        modifier = Modifier
            .focusRequester(backFocusRequester)
            .width(360.dp)
            .height(56.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.back),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
        }
    }
}

private const val POSTER_ASPECT_RATIO = 2f / 3f
