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
import com.neilturner.musicfun.ui.screens.MenuScreen
import com.neilturner.musicfun.ui.screens.MusicScreen

@Composable
fun AppNavigation(
	onRequestNotificationAccess: () -> Unit = {},
) {
	// The menu is always the root; it checks notification access off the main thread and replaces
	// itself with the music screen once access is granted.
	val backStack: NavBackStack<NavKey> = rememberNavBackStack(Menu)

	// NavDisplay only handles back while there is an entry to pop, so finish the activity at the root.
	val activity = LocalActivity.current
	BackHandler(enabled = backStack.size == 1) {
		activity?.finish()
	}

	NavDisplay(
		backStack = backStack,
		onBack = { backStack.removeLastOrNull() },
		entryProvider = entryProvider {
			entry<Menu> {
				MenuScreen(
					modifier = Modifier.fillMaxSize(),
					onRequestNotificationAccess = onRequestNotificationAccess,
					onOpenMusic = {
						// The menu is only a permission gate; replace it so it can't stay drawn
						// underneath (or over) the music screen.
						backStack.remove(Menu)
						backStack.add(Music)
					},
				)
			}
			entry<Music> {
				MusicScreen(modifier = Modifier.fillMaxSize())
			}
		},
	)
}