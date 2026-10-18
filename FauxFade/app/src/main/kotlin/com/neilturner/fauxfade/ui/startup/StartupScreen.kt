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
import com.neilturner.fauxfade.core.media.Quality
import com.neilturner.fauxfade.ui.player.PlayerViewModel
import com.neilturner.fauxfade.ui.player.StillCaptureMode

/**
 * The screen the app opens on: pick a quality, pick how the still should be taken, then let the
 * countdown run out.
 *
 * There is no backdrop and nothing to look at but the controls - the point of the screen is to
 * be gone in ten seconds, and a poster or a preview would only invite the viewer to wait for
 * something that is not coming.
 *
 * Nothing here touches the players. They do not exist until the countdown ends, which is what
 * keeps a change from having to rebuild a running decode.
 *
 * Both pickers are the same shape because they are the same decision: two values that change what
 * a segment costs, chosen before any of it is paid for. Only one panel is ever open.
 *
 * [onGo] is handed the chosen capture method rather than starting the run itself. Four of the five
 * run here, but [StillCaptureMode.PIXEL_COPY] has a screen and a view model of its own under
 * `feature/pixelcopy`, and the caller is what knows to send it there - so the button reports which
 * method was picked and lets the router decide what that means.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun StartupScreen(
	viewModel: PlayerViewModel,
	onGo: (StillCaptureMode) -> Unit,
	modifier: Modifier = Modifier,
) {
	val quality by viewModel.quality.collectAsStateWithLifecycle()
	val captureMode by viewModel.captureMode.collectAsStateWithLifecycle()
	val countdown by viewModel.countdown.collectAsStateWithLifecycle()

	var open by remember { mutableStateOf<OpenPanel?>(null) }

	// Built for every entry up front rather than on demand: a FocusRequester has to be the same
	// instance the row's modifier was built with, and a lazily created one would not be.
	// Deliberately absent: any requestFocus() on the trigger buttons. TV Material manages focus
	// for its own surfaces and hands it straight back - the request succeeds, and focus still
	// ends up elsewhere. The triggers are the first focusable in the column, so D-pad navigation
	// reaches them in one press from wherever focus lands.
	val qualityFocus = remember { Quality.entries.associateWith { FocusRequester() } }
	val captureFocus = remember { StillCaptureMode.entries.associateWith { FocusRequester() } }

	// Focus the selected row while a panel is open, and leave focus entirely alone on the way
	// out. The dismissal decides where it lands, and from there every control on the screen is at
	// most two D-pad presses away - which is the same arrangement the first composition arrives
	// with, so leaving is no different from getting there in the first place. Requesting focus on
	// a trigger instead does not take: see the note above the triggers.
	// Keyed on the selection too, so a panel that is opened on a newly picked value asks for that
	// value rather than for whatever it last requested.
	LaunchedEffect(open, quality, captureMode) {
		withFrameNanos { }
		when (open) {
			OpenPanel.QUALITY -> qualityFocus.getValue(quality).requestFocus()
			OpenPanel.CAPTURE -> captureFocus.getValue(captureMode).requestFocus()
			null -> Unit
		}
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

			Button(onClick = { open = OpenPanel.QUALITY }) {
				Text(quality.label, style = MaterialTheme.typography.titleMedium)
			}

			// The other half of the same choice, so it reads as a peer of the quality rather than as
			// a setting. The mode names are too short to be meaningful on their own - "Tap" and
			// "PixelCopy" say what they are called, not what they cost - which is why the panel rows
			// carry the explanation.
			Button(onClick = { open = OpenPanel.CAPTURE }) {
				Text("Still: ${captureMode.label}", style = MaterialTheme.typography.titleMedium)
			}

			Button(onClick = { onGo(captureMode) }) {
				Text(startLabel(countdown), style = MaterialTheme.typography.titleMedium)
			}
		}

		// Sibling of the controls rather than a child of them: a popup has to cover the whole
		// screen, and a fillMaxSize inside the centred column would be bounded by it.
		if (open != null) {
			Box(
				modifier =
					Modifier
						.fillMaxSize()
						.background(MaterialTheme.colorScheme.scrim.copy(alpha = SCRIM_ALPHA))
						.onPreviewKeyEvent { event ->
							// Not filtered to key-down. By the time the release arrives the panel
							// is already closed and setting the flag again does nothing, and
							// swallowing it keeps Back from reaching anything behind the scrim -
							// including the handler that would otherwise leave the feature for the
							// startup screen.
							if (event.key == Key.Back) {
								open = null
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
							// The horizontal gap is headroom, not styling. A focused row is scaled
							// up about 5%, which at this width measures out at roughly 12dp out
							// either side, and without the gap the row pushes out through the panel
							// edge and hangs in the scrim. Padding the panel inward keeps the scale
							// inside the box and lets the animation do its job.
							.padding(horizontal = ROW_ROOM, vertical = PANEL_PADDING)
							// Traps the arrow keys inside the panel. Without it, up from the first
							// row escapes to the button behind the scrim, which is still focusable and
							// still reacts to the click.
							.focusGroup(),
				) {
					Column {
						when (open) {
							OpenPanel.QUALITY -> {
								Quality.entries.forEach { option ->
									OptionRow(
										selected = option == quality,
										headline = option.label,
										supporting = null,
										focusRequester = qualityFocus.getValue(option),
										onClick = {
											viewModel.select(option)
											open = null
										},
									)
								}
							}

							OpenPanel.CAPTURE -> {
								StillCaptureMode.entries.forEach { option ->
									OptionRow(
										selected = option == captureMode,
										headline = option.label,
										// Carried here rather than in the button: "Tap" and "PixelCopy"
										// are names, and the difference between them is what the panel
										// is for.
										supporting = option.supporting,
										focusRequester = captureFocus.getValue(option),
										onClick = {
											viewModel.selectCapture(option)
											open = null
										},
									)
								}
							}

							null -> {}
						}
					}
				}
			}
		}
	}
}

/** Which picker, if any, is showing. One at a time - two would be two panels over one scrim. */
private enum class OpenPanel {
	QUALITY,
	CAPTURE,
}

/**
 * One selectable row in a picker panel.
 *
 * Pulled out because the two panels differ only in what they say, and writing the focus tint and
 * the row height twice is how the two drift apart and a panel ends up looking like it came from
 * somewhere else.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun OptionRow(
	selected: Boolean,
	headline: String,
	supporting: String?,
	focusRequester: FocusRequester,
	onClick: () -> Unit,
) {
	ListItem(
		selected = selected,
		onClick = onClick,
		// Focus tints, selection does not. The row under the D-pad needs somewhere to be. The
		// quality already chosen says itself with its filled radio, so tinting that row as well
		// marked the answer twice and made it look highlighted rather than answered.
		//
		// Note there is no focusedSelectedContainerColor to override: a row that is both takes this
		// tint. The panel opens with focus already on the chosen row, so it is tinted until the
		// D-pad moves off it.
		colors =
			ListItemDefaults.colors(
				containerColor = Color.Transparent,
				focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
				selectedContainerColor = Color.Transparent,
				contentColor = MaterialTheme.colorScheme.onSurface,
				focusedContentColor = MaterialTheme.colorScheme.onSurface,
				selectedContentColor = MaterialTheme.colorScheme.onSurface,
				focusedSelectedContentColor = MaterialTheme.colorScheme.onSurface,
			),
		headlineContent = {
			Text(headline, style = MaterialTheme.typography.titleMedium)
		},
		supportingContent = {
			// Absent for the quality panel, which is four short labels that say what they are.
			if (supporting != null) Text(supporting, style = MaterialTheme.typography.bodyMedium)
		},
		leadingContent = {
			// Null onClick: the row is the click target, and a second one here would put an extra
			// stop in the D-pad's way.
			RadioButton(selected = selected, onClick = null)
		},
		modifier = Modifier.focusRequester(focusRequester),
	)
}

/** "Start playback (4)" while there is time left, plain "Start playback" once there is not. */
private fun startLabel(countdown: Int): String = if (countdown > 0) "Start playback ($countdown)" else "Start playback"

/** How opaque the dim over the controls is while a panel is open. */
private const val SCRIM_ALPHA = 0.65f

/** Wide enough for the longest label on a ten-foot screen without stretching across it. */
private val PANEL_WIDTH = 520.dp

private val PANEL_PADDING = 8.dp

/**
 * Clear space inside the panel either side of the rows, so a focused row's scale stays within
 * the panel. Sized from the measured 5% focus scale at [PANEL_WIDTH], plus a little slack.
 */
private val ROW_ROOM = 18.dp

private val GAP = 24.dp

private val CORNER = 16.dp
