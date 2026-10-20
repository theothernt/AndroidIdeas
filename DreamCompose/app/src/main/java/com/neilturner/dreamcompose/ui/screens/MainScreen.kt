package com.neilturner.dreamcompose.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.neilturner.dreamcompose.R
import com.neilturner.dreamcompose.ui.theme.DreamComposeTheme

/**
 * The app's only screen. It shows whether the app is currently the active screen saver and
 * offers a preview of the dream.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MainScreen(
	isScreenSaverEnabled: Boolean,
	onPreviewDream: () -> Unit,
	onOpenDreamSettings: () -> Unit,
	modifier: Modifier = Modifier,
) {
	// The screen is shown again after the help overlay was dismissed, and the focus does not
	// come back on its own then.
	val firstButtonFocusRequester = remember { FocusRequester() }
	FocusOnAppear(firstButtonFocusRequester)

	Column(
		modifier =
			modifier
				.fillMaxSize()
				.padding(48.dp),
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
	) {
		Text(
			text = stringResource(R.string.app_name),
			style = MaterialTheme.typography.displayMedium,
		)
		Text(
			text =
				stringResource(
					if (isScreenSaverEnabled) {
						R.string.screen_saver_enabled
					} else {
						R.string.screen_saver_not_enabled
					},
				),
			style = MaterialTheme.typography.bodyLarge,
			color = if (isScreenSaverEnabled) Color(0xFF7BD88F) else Color.LightGray,
			textAlign = TextAlign.Center,
		)
		Button(
			onClick = onPreviewDream,
			modifier = Modifier.focusRequester(firstButtonFocusRequester),
		) {
			Text(text = stringResource(R.string.preview_dream))
		}
		Button(onClick = onOpenDreamSettings) {
			Text(text = stringResource(R.string.set_as_screensaver))
		}
	}
}

/**
 * Shown when the device has no screen saver settings screen, which is the case on most Android
 * TV builds. It spells out the commands that enable the dream by hand.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SetupHelpOverlay(onDismiss: () -> Unit) {
	// The overlay is opened from a button, so the focus is still on that button when the
	// overlay appears. The OK button asks for the focus explicitly, otherwise the D-pad has to
	// be used to find it.
	val okButtonFocusRequester = remember { FocusRequester() }
	FocusOnAppear(okButtonFocusRequester)

	Box(
		modifier =
			Modifier
				.fillMaxSize()
				.background(Color.Black),
		contentAlignment = Alignment.Center,
	) {
		Column(
			modifier = Modifier.padding(48.dp),
			horizontalAlignment = Alignment.CenterHorizontally,
			verticalArrangement = Arrangement.spacedBy(24.dp),
		) {
			Text(
				text = stringResource(R.string.set_as_screensaver),
				style = MaterialTheme.typography.headlineMedium,
			)
			Text(
				text = stringResource(R.string.dream_setup_help),
				style = MaterialTheme.typography.bodyLarge,
				textAlign = TextAlign.Center,
			)
			Text(
				text = stringResource(R.string.dream_setup_commands),
				style = MaterialTheme.typography.bodyMedium,
				textAlign = TextAlign.Center,
			)
			Button(
				onClick = onDismiss,
				modifier = Modifier.focusRequester(okButtonFocusRequester),
			) {
				Text(text = stringResource(R.string.ok))
			}
		}
	}
}

@Preview(showBackground = true)
@Composable
private fun MainScreenPreview() {
	DreamComposeTheme {
		MainScreen(
			isScreenSaverEnabled = true,
			onPreviewDream = {},
			onOpenDreamSettings = {},
		)
	}
}
