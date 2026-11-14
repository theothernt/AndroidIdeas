package com.neilturner.musicfun.navigation

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.neilturner.musicfun.ui.screens.MusicScreen

@Composable
fun AppNavigation(
	onRequestNotificationAccess: () -> Unit = {},
) {
	// The now-playing screen is the whole app; notification access is requested from it.
	val backStack: NavBackStack<NavKey> = rememberNavBackStack(Music)

	// NavDisplay only handles back while there is an entry to pop, so finish the activity at the root.
	val activity = LocalActivity.current
	BackHandler(enabled = backStack.size == 1) {
		activity?.finish()
	}

	NavDisplay(
		backStack = backStack,
		onBack = { backStack.removeLastOrNull() },
		entryProvider = entryProvider {
			entry<Music> {
				MusicScreen(
					modifier = Modifier.fillMaxSize(),
					onRequestNotificationAccess = onRequestNotificationAccess,
				)
			}
		},
	)
}