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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Glow
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
    onNavigateToPlexPlayerMock: () -> Unit,
    onNavigateToPlexOnDeck: () -> Unit,
    onNavigateToPlexLibraries: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel()
) {
    val focusRequesters = remember { List(8) { FocusRequester() } }
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
                "Play ASS subtitles" to { onNavigateToPlayer("ass") },
                stringResource(R.string.plex_player_button) to { onNavigateToPlexPlayer() },
                stringResource(R.string.plex_player_mock_button) to { onNavigateToPlexPlayerMock() },
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
                    val menuIndex = rowIndex * 2 + columnIndex
                    Button(
                        onClick = item.second,
                        modifier = Modifier
                            .focusRequester(focusRequesters[menuIndex])
                            .onFocusChanged {
                                if (it.isFocused) lastFocusedIndex = menuIndex
                            }
                            .width(MENU_BUTTON_WIDTH)
                            .height(56.dp),
                        scale = ButtonDefaults.scale(
                            focusedScale = FOCUSED_MENU_BUTTON_SCALE,
                            pressedScale = PRESSED_MENU_BUTTON_SCALE
                        ),
                        // Each button glows in its own colour, so that the effect can be seen on
                        // all of them at once rather than on one at a time.
                        glow = ButtonDefaults.glow(
                            focusedGlow = Glow(
                                elevationColor = MENU_GLOW_COLOURS[menuIndex % MENU_GLOW_COLOURS.size],
                                elevation = MENU_GLOW_ELEVATION
                            )
                        )
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
                        // A row with one item in it — the last one, because there are seven of
                        // them — keeps the width of a full row. The item then stands exactly
                        // where the item above it in the first column stands, rather than in
                        // the middle of the screen.
                        if (rowItems.size == 1) {
                            Spacer(modifier = Modifier.width(MENU_BUTTON_WIDTH))
                        }
                    }
                }
            }
        }
    }
}

/**
 * The TV library grows a focused button by 1.1x, which is too much for a home menu that fits on
 * screen; the cards and chips elsewhere in the app grow less, and these match them.
 */
private const val FOCUSED_MENU_BUTTON_SCALE = 1.04f
private const val PRESSED_MENU_BUTTON_SCALE = 0.97f

/**
 * The colour each button on the main menu glows in, in the order the buttons are read: down the
 * left column and then down the right one. A glow is only drawn around the focused button, so
 * the colours are seen one at a time as the focus moves.
 */
private val MENU_GLOW_COLOURS = listOf(
    Color(0xFFFF5252), // red
    Color(0xFFFF9800), // orange
    Color(0xFFFFCA28), // amber
    Color(0xFF9CCC65), // light green
    Color(0xFF26A69A), // teal
    Color(0xFF29B6F6), // light blue
    Color(0xFF5C6BC0), // indigo
    Color(0xFFAB47BC)  // purple
)

/**
 * How far the glow reaches out from the focused button.
 *
 * This is the radius of the blur the glow is drawn with, so a bigger one is a wider, softer halo
 * that reaches past the button and laps over the one beside it. The buttons are 20dp apart, so
 * anything much under that stops dead at the edge.
 */
private val MENU_GLOW_ELEVATION = 22.dp

/** The width of a menu button, and of the space left for the one that has no partner. */
private val MENU_BUTTON_WIDTH = 300.dp
