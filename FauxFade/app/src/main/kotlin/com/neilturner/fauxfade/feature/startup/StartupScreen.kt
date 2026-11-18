package com.neilturner.fauxfade.feature.startup

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
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.RadioButton
import androidx.tv.material3.Text
import com.neilturner.fauxfade.core.media.Quality
import com.neilturner.fauxfade.core.player.StillCaptureMode

/**
 * The app's menu: pick a quality, pick how the still should be taken, then start.
 *
 * No backdrop and nothing to look at but the controls - the point of the screen is to be gone in
 * ten seconds. Nothing here touches a player; the chosen quality and method are handed to the
 * router, which enters the matching feature with the quality already decided.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun StartupScreen(
	onGo: (Quality, StillCaptureMode) -> Unit,
	modifier: Modifier = Modifier,
) {
	var quality by remember { mutableStateOf(Quality.FHD_SDR) }
	var captureMode by remember { mutableStateOf(StillCaptureMode.PIXEL_COPY) }
	var open by remember { mutableStateOf<OpenPanel?>(null) }

	val qualityFocus = remember { Quality.entries.associateWith { FocusRequester() } }
	val captureFocus = remember { StillCaptureMode.entries.associateWith { FocusRequester() } }
	val triggerFocus = remember { FocusRequester() }

	LaunchedEffect(Unit) {
		withFrameNanos { }
		triggerFocus.requestFocus()
	}

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
			Button(onClick = { open = OpenPanel.QUALITY }, modifier = Modifier.focusRequester(triggerFocus)) {
				Text(quality.label, style = MaterialTheme.typography.titleMedium)
			}
			Button(onClick = { open = OpenPanel.CAPTURE }) {
				Text("Still: ${captureMode.label}", style = MaterialTheme.typography.titleMedium)
			}
			Button(onClick = { onGo(quality, captureMode) }) {
				Text("Start playback", style = MaterialTheme.typography.titleMedium)
			}
		}

		if (open != null) {
			Box(
				modifier =
					Modifier
						.fillMaxSize()
						.background(MaterialTheme.colorScheme.scrim.copy(alpha = SCRIM_ALPHA))
						.onPreviewKeyEvent { event ->
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
							.padding(horizontal = ROW_ROOM, vertical = PANEL_PADDING)
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
											quality = option
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
										supporting = option.supporting,
										focusRequester = captureFocus.getValue(option),
										onClick = {
											captureMode = option
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

private enum class OpenPanel {
	QUALITY,
	CAPTURE,
}

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
		headlineContent = { Text(headline, style = MaterialTheme.typography.titleMedium) },
		supportingContent = {
			if (supporting != null) Text(supporting, style = MaterialTheme.typography.bodyMedium)
		},
		leadingContent = { RadioButton(selected = selected, onClick = null) },
		modifier = Modifier.focusRequester(focusRequester),
	)
}

private const val SCRIM_ALPHA = 0.65f
private val PANEL_WIDTH = 520.dp
private val PANEL_PADDING = 8.dp
private val ROW_ROOM = 18.dp
private val GAP = 24.dp
private val CORNER = 16.dp
