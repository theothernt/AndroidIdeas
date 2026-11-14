package com.neilturner.musicfun.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.neilturner.musicfun.R
import com.neilturner.musicfun.ui.theme.MusicFunTheme
import com.neilturner.musicfun.ui.viewmodel.MenuUiState
import com.neilturner.musicfun.ui.viewmodel.MenuViewModel

@Composable
fun MenuScreen(
	modifier: Modifier = Modifier,
	onRequestNotificationAccess: () -> Unit = {},
	onOpenMusic: () -> Unit = {},
) {
	val viewModel: MenuViewModel = viewModel()
	val state by viewModel.uiState.collectAsStateWithLifecycle()

	// The permission can only be changed from system settings, so re-check when we come back.
	LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
		viewModel.refreshPermission()
	}

	// Open the music screen the first time access is granted, but don't bounce the user back into
	// it when they navigate back to this menu.
	LaunchedEffect(state.hasNotificationListenerAccess) {
		if (viewModel.onPermissionChanged(state.hasNotificationListenerAccess)) {
			onOpenMusic()
		}
	}

	MenuScreenContent(
		state = state,
		onRequestNotificationAccess = onRequestNotificationAccess,
		onOpenMusic = onOpenMusic,
		modifier = modifier,
	)
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun MenuScreenContent(
	state: MenuUiState,
	onRequestNotificationAccess: () -> Unit,
	onOpenMusic: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val focusRequester = remember { FocusRequester() }
	LaunchedEffect(state.hasNotificationListenerAccess) {
		android.util.Log.d("AppState", "menu granted=${state.hasNotificationListenerAccess}")
		focusRequester.requestFocus()
	}

	Column(
		modifier = modifier
			.fillMaxSize()
			.padding(48.dp),
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
	) {
		Text(
			text = stringResource(R.string.menu_title),
			style = MaterialTheme.typography.displayMedium,
			textAlign = TextAlign.Center,
		)

		if (!state.hasNotificationListenerAccess) {
			Text(
				text = stringResource(R.string.notification_access_required),
				textAlign = TextAlign.Center,
			)
			Button(
				onClick = onRequestNotificationAccess,
				modifier = Modifier.focusRequester(focusRequester),
			) {
				Text(text = stringResource(R.string.request_notification_permission))
			}
		} else {
			Text(
				text = stringResource(R.string.notification_access_granted),
				textAlign = TextAlign.Center,
			)
			Button(
				onClick = onOpenMusic,
				modifier = Modifier.focusRequester(focusRequester),
			) {
				Text(text = stringResource(R.string.open_music))
			}
		}
	}
}

@Preview(showBackground = true)
@Composable
private fun MenuScreenPreview() {
	MusicFunTheme {
		MenuScreenContent(
			state = MenuUiState(),
			onRequestNotificationAccess = {},
			onOpenMusic = {},
			modifier = Modifier.fillMaxSize(),
		)
	}
}

@Preview(showBackground = true)
@Composable
private fun MenuScreenGrantedPreview() {
	MusicFunTheme {
		MenuScreenContent(
			state = MenuUiState(hasNotificationListenerAccess = true),
			onRequestNotificationAccess = {},
			onOpenMusic = {},
			modifier = Modifier.fillMaxSize(),
		)
	}
}