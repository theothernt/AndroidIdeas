package com.neilturner.overlayparty.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.serialization.NavBackStackSerializer
import androidx.navigation3.ui.NavDisplay
import com.neilturner.overlayparty.ui.main.MainMenuScreen
import com.neilturner.overlayparty.ui.main.MainScreen
import com.neilturner.overlayparty.ui.main.ScreenTwo
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer

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
    val backStack =
        rememberSerializable(
            serializer = NavBackStackSerializer(serializer<AppNavKey>()),
        ) {
            NavBackStack(AppNavKey.MainMenu)
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
