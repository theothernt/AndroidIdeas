package com.neilturner.overlayparty.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.neilturner.overlayparty.ui.main.MainMenuScreen
import com.neilturner.overlayparty.ui.main.MainScreen
import com.neilturner.overlayparty.ui.main.ScreenTwo
import kotlinx.serialization.Serializable

@Serializable
sealed interface AppNavKey : NavKey {
    @Serializable
    data object MainMenu : AppNavKey

    @Serializable
    data object ScreenOne : AppNavKey

    @Serializable
    data object ScreenTwo : AppNavKey
}

@Composable
fun AppNavigation(modifier: Modifier = Modifier) {
    val backStack = remember { mutableStateListOf<AppNavKey>(AppNavKey.MainMenu) }

    BackHandler(enabled = backStack.size > 1) {
        backStack.removeAt(backStack.size - 1)
    }

    NavDisplay<AppNavKey>(
        backStack = backStack,
        onBack = {
            if (backStack.size > 1) {
                backStack.removeAt(backStack.size - 1)
            }
        },
        modifier = modifier,
        entryProvider =
            entryProvider {
                entry<AppNavKey.MainMenu> {
                    MainMenuScreen(
                        onNavigateToScreenOne = { backStack.add(AppNavKey.ScreenOne) },
                        onNavigateToScreenTwo = { backStack.add(AppNavKey.ScreenTwo) },
                    )
                }
                entry<AppNavKey.ScreenOne> {
                    MainScreen()
                }
                entry<AppNavKey.ScreenTwo> {
                    ScreenTwo()
                }
            },
    )
}
