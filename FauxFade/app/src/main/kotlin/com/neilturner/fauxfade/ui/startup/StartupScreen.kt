package com.neilturner.fauxfade.ui.startup

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.RadioButton
import androidx.tv.material3.Text
import com.neilturner.fauxfade.media.Quality
import com.neilturner.fauxfade.ui.player.PlayerViewModel

/**
 * The screen the app opens on: pick a quality, then let the countdown run out.
 *
 * There is no backdrop and nothing to look at but the controls - the point of the screen is to
 * be gone in ten seconds, and a poster or a preview would only invite the viewer to wait for
 * something that is not coming.
 *
 * Nothing here touches the players. They do not exist until the countdown ends, which is what
 * keeps a quality change from having to rebuild a running decode.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun StartupScreen(
	viewModel: PlayerViewModel,
	modifier: Modifier = Modifier,
) {
	val quality by viewModel.quality.collectAsStateWithLifecycle()
	val countdown by viewModel.countdown.collectAsStateWithLifecycle()

	var expanded by remember { mutableStateOf(false) }

	// Built for every entry up front rather than on demand: a FocusRequester has to be the same
	// instance the row's modifier was built with, and a lazily created one would not be.
	// Deliberately absent: any requestFocus() on the quality trigger. TV Material manages focus
	// for its own surfaces and hands it straight back - the request succeeds, and focus still
	// ends up on the button below. The trigger is the first focusable in the column, so D-pad
	// navigation reaches it in one press from wherever focus lands.

	val rowFocus = remember { Quality.entries.associateWith { FocusRequester() } }

	// Focus the selected row while the panel is open, and hand it back to the trigger when it
	// closes - including on the first composition, so the screen never arrives with nowhere to
	// go. Keyed on the selection as well, so choosing an option lands on the trigger rather than
	// re-requesting the row that was just clicked.
	LaunchedEffect(expanded, quality) {
		withFrameNanos { }
		if (expanded) rowFocus.getValue(quality).requestFocus()
	}

	Box(
		modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
		contentAlignment = Alignment.Center,
	) {
		Column(
			horizontalAlignment = Alignment.CenterHorizontally,
			verticalArrangement = Arrangement.spacedBy(GAP),
			modifier = Modifier.widthIn(max = PANEL_WIDTH),
		) {
			Text(
				text = "Faux Fade",
				style = MaterialTheme.typography.headlineMedium,
				color = MaterialTheme.colorScheme.onBackground,
			)

			Button(onClick = { expanded = true }) {
				Text(quality.label, style = MaterialTheme.typography.titleMedium)
			}

			Button(onClick = viewModel::skipCountdown) {
				Text(startLabel(countdown), style = MaterialTheme.typography.titleMedium)
			}
		}

		// Sibling of the controls rather than a child of them: a popup has to cover the whole
		// screen, and a fillMaxSize inside the centred column would be bounded by it.
		if (expanded) {
			Box(
				modifier =
					Modifier
						.fillMaxSize()
						.background(MaterialTheme.colorScheme.scrim.copy(alpha = SCRIM_ALPHA))
						.onPreviewKeyEvent { event ->
							// Not filtered to key-down. By the time the release arrives the panel
							// is already closed and setting the flag again does nothing, and
							// swallowing it keeps Back from reaching anything behind the scrim.
							if (event.key == Key.Back) {
								expanded = false
								true
							} else {
								false
							}
						},
				contentAlignment = Alignment.Center,
			) {
				Box(
					modifier =
						Modifier
							.widthIn(max = PANEL_WIDTH)
							.background(MaterialTheme.colorScheme.surface, RoundedCornerShape(CORNER))
							.padding(vertical = PANEL_PADDING)
							// Traps the arrow keys inside the panel. Without it, up from the first
							// row escapes to the start button behind the scrim, which is still
							// focusable and still reacts to the click.
							.focusGroup(),
				) {
					Column {
						Quality.entries.forEach { option ->
							ListItem(
								selected = option == quality,
								onClick = {
									viewModel.select(option)
									expanded = false
								},
								// Stated rather than defaulted. The theme carries no colour scheme,
								// and the inherited content colour lands too close to the panel
								// behind it to read on the three rows that are not selected.
								colors =
									ListItemDefaults.colors(
										containerColor = Color.Transparent,
										contentColor = MaterialTheme.colorScheme.onSurface,
										focusedContainerColor = MaterialTheme.colorScheme.primaryContainer,
										focusedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
										selectedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
										selectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
										focusedSelectedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
									),
								headlineContent = {
									Text(option.label, style = MaterialTheme.typography.titleMedium)
								},
								leadingContent = {
									// Null onClick: the row is the click target, and a second one here
									// would put an extra stop in the D-pad's way.
									RadioButton(selected = option == quality, onClick = null)
								},
								modifier = Modifier.focusRequester(rowFocus.getValue(option)),
							)
						}
					}
				}
			}
		}
	}
}

/** "Start playback (4)" while there is time left, plain "Start playback" once there is not. */
private fun startLabel(countdown: Int): String = if (countdown > 0) "Start playback ($countdown)" else "Start playback"

/** How opaque the dim over the controls is while the panel is open. */
private const val SCRIM_ALPHA = 0.65f

/** Wide enough for the longest label on a ten-foot screen without stretching across it. */
private val PANEL_WIDTH = 520.dp

private val PANEL_PADDING = 8.dp

private val GAP = 24.dp

private val CORNER = 16.dp
