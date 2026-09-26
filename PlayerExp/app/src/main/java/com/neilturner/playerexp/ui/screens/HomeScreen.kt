package com.neilturner.playerexp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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

            Button(
                onClick = { onNavigateToPlayer("progressive") },
                modifier = Modifier
                    .focusRequester(focusRequesters[0])
                    .onFocusChanged { if (it.isFocused) lastFocusedIndex = 0 }
                    .width(360.dp)
                    .height(56.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Play MP4, MKV, etc",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = { onNavigateToPlayer("hls") },
                modifier = Modifier
                    .focusRequester(focusRequesters[1])
                    .onFocusChanged { if (it.isFocused) lastFocusedIndex = 1 }
                    .width(360.dp)
                    .height(56.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Play HLS Stream",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onNavigateToPlexPlayer,
                modifier = Modifier
                    .focusRequester(focusRequesters[2])
                    .onFocusChanged { if (it.isFocused) lastFocusedIndex = 2 }
                    .width(360.dp)
                    .height(56.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.plex_player_button),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onNavigateToPlexOnDeck,
                modifier = Modifier
                    .focusRequester(focusRequesters[3])
                    .onFocusChanged { if (it.isFocused) lastFocusedIndex = 3 }
                    .width(360.dp)
                    .height(56.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.plex_on_deck_button),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onNavigateToPlexLibraries,
                modifier = Modifier
                    .focusRequester(focusRequesters[4])
                    .onFocusChanged { if (it.isFocused) lastFocusedIndex = 4 }
                    .width(360.dp)
                    .height(56.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.plex_libraries_button),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onNavigateToSettings,
                modifier = Modifier
                    .focusRequester(focusRequesters[5])
                    .onFocusChanged { if (it.isFocused) lastFocusedIndex = 5 }
                    .width(360.dp)
                    .height(56.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.settings),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
