package com.neilturner.playerexp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.neilturner.playerexp.ui.viewmodels.HomeViewModel
import com.neilturner.playerexp.R
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToPlayer: (String) -> Unit,
    onNavigateToPlexPlayer: () -> Unit,
    onNavigateToPlexOnDeck: () -> Unit,
    onNavigateToPlexLibraries: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel()
) {
    val focusRequesters = remember { List(6) { FocusRequester() } }
    var hasRequestedInitialFocus by rememberSaveable { mutableStateOf(false) }
    var lastFocusedIndex by rememberSaveable { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        if (!hasRequestedInitialFocus) {
            hasRequestedInitialFocus = true
            focusRequesters[0].requestFocus()
        } else {
            focusRequesters[lastFocusedIndex].requestFocus()
        }
    }

    // A growing list of buttons stops fitting on a TV with a small dp viewport, so the column
    // scrolls. It is still centred when everything fits: the spacer above and below the content is
    // half of whatever room is left, which is zero once the content is taller than the screen.
    val scrollState = rememberScrollState()
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    var contentHeightDp by remember { mutableIntStateOf(0) }
    val centringSpacer = ((configuration.screenHeightDp - contentHeightDp) / 2).coerceAtLeast(0).dp

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(centringSpacer))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 48.dp, vertical = 24.dp)
                .onSizeChanged { size -> contentHeightDp = with(density) { size.height.toDp() }.value.toInt() },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Player Experience",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Select a media stream to test playback",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(36.dp))

            // The menu reads better as a pair of columns: three rows of two, centred as a block,
            // with the same spacing between and within rows.
            val menuItems = listOf(
                "Play MP4, MKV, etc" to { onNavigateToPlayer("progressive") },
                "Play HLS Stream" to { onNavigateToPlayer("hls") },
                stringResource(R.string.plex_player_button) to { onNavigateToPlexPlayer() },
                stringResource(R.string.plex_on_deck_button) to { onNavigateToPlexOnDeck() },
                stringResource(R.string.plex_libraries_button) to { onNavigateToPlexLibraries() },
                stringResource(R.string.settings) to { onNavigateToSettings() }
            )
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                menuItems.chunked(2).forEachIndexed { rowIndex, rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        rowItems.forEachIndexed { columnIndex, item ->
                            Button(
                                onClick = item.second,
                                modifier = Modifier
                                    .focusRequester(focusRequesters[rowIndex * 2 + columnIndex])
                                    .onFocusChanged {
                                        if (it.isFocused) lastFocusedIndex = rowIndex * 2 + columnIndex
                                    }
                                    .width(300.dp)
                                    .height(56.dp)
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = item.first,
                                        style = MaterialTheme.typography.titleMedium,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
