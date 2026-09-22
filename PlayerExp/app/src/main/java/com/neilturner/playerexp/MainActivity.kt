package com.neilturner.playerexp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.media3.common.util.UnstableApi
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.tv.material3.Surface
import com.neilturner.playerexp.ui.screens.HomeScreen
import com.neilturner.playerexp.ui.screens.PlayerScreen
import com.neilturner.playerexp.ui.screens.PlexSettingsScreen
import com.neilturner.playerexp.ui.theme.PlayerExpTheme

import kotlinx.serialization.Serializable

@Serializable
sealed interface PlayerExpRoute : NavKey

@Serializable
data object HomeRoute : PlayerExpRoute

@Serializable
data class PlayerRoute(val mediaId: String) : PlayerExpRoute

@Serializable
data object PlexSettingsRoute : PlayerExpRoute

class MainActivity : ComponentActivity() {
    @UnstableApi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val backStack = rememberNavBackStack(HomeRoute)
            PlayerExpTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RectangleShape
                ) {
                    NavDisplay(
                        backStack = backStack,
                        onBack = {
                            if (backStack.size > 1) backStack.removeLastOrNull() else finish()
                        },
                        entryDecorators = listOf(
                            rememberSaveableStateHolderNavEntryDecorator(),
                            rememberViewModelStoreNavEntryDecorator()
                        ),
                        entryProvider = entryProvider {
                            entry<HomeRoute> {
                                HomeScreen(
                                    onNavigateToPlayer = { mediaId ->
                                        backStack.add(PlayerRoute(mediaId))
                                    },
                                    onNavigateToSettings = { backStack.add(PlexSettingsRoute) }
                                )
                            }
                            entry<PlayerRoute> { route ->
                                PlayerScreen(
                                    mediaId = route.mediaId,
                                    onBack = { backStack.removeLastOrNull() }
                                )
                            }
                            entry<PlexSettingsRoute> {
                                PlexSettingsScreen(onBack = { backStack.removeLastOrNull() })
                            }
                        }
                    )
                }
            }
        }
    }
}
