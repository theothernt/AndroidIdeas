package com.neilturner.musicfun.navigation

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.neilturner.musicfun.data.listener.NotificationListenerPermission
import com.neilturner.musicfun.ui.screens.MenuScreen
import com.neilturner.musicfun.ui.screens.MusicScreen

@Composable
fun AppNavigation(
	onRequestNotificationAccess: () -> Unit = {},
) {
	// With access already granted the music screen is the root, so back exits the app. Otherwise
	// the menu is the root and is the only way to request access.
	val context = LocalContext.current
	val initialKey = remember(context) {
		if (NotificationListenerPermission.isGranted(context)) Music else Menu
	}
	val backStack = rememberNavBackStack(initialKey)

	val entryDecorators = listOf<NavEntryDecorator<NavKey>>(
		rememberSaveableStateHolderNavEntryDecorator(),
		rememberViewModelStoreNavEntryDecorator(),
	)

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
					onOpenMusic = { backStack.add(Music) },
				)
			}
			entry<Music> {
				MusicScreen(modifier = Modifier.fillMaxSize())
			}
		},
		entryDecorators = entryDecorators,
	)
}