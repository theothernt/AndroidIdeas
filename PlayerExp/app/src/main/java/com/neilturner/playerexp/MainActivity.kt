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
import com.neilturner.playerexp.ui.screens.PlexLibrariesScreen
import com.neilturner.playerexp.ui.screens.PlexOnDeckScreen
import com.neilturner.playerexp.ui.screens.PlexPlayerScreen
import com.neilturner.playerexp.ui.screens.PlexSettingsScreen
import com.neilturner.playerexp.ui.screens.PlexShowScreen
import com.neilturner.playerexp.ui.theme.PlayerExpTheme

import kotlinx.serialization.Serializable

@Serializable
sealed interface PlayerExpRoute : NavKey

@Serializable
data object HomeRoute : PlayerExpRoute

@Serializable
data class PlayerRoute(val mediaId: String) : PlayerExpRoute

/** [ratingKey] is the card that was pressed; without one the screen plays a random episode. */
@Serializable
data class PlexPlayerRoute(
    val ratingKey: String? = null,
    val title: String? = null
) : PlayerExpRoute

@Serializable
data object PlexSettingsRoute : PlayerExpRoute

@Serializable
data object PlexOnDeckRoute : PlayerExpRoute

@Serializable
data object PlexLibrariesRoute : PlayerExpRoute

/**
 * A TV show opened from an episode card on On Deck, showing its seasons and full episode list.
 * [initialEpisodeRatingKey] is the episode that was picked on the previous screen, so the show page
 * can land focus on it rather than always on the first episode.
 */
@Serializable
data class ShowDetailRoute(
    val showRatingKey: String,
    val showTitle: String? = null,
    val initialEpisodeRatingKey: String? = null
) : PlayerExpRoute

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
                                    onNavigateToPlexPlayer = { backStack.add(PlexPlayerRoute()) },
                                    onNavigateToPlexOnDeck = { backStack.add(PlexOnDeckRoute) },
                                    onNavigateToPlexLibraries = { backStack.add(PlexLibrariesRoute) },
                                    onNavigateToSettings = { backStack.add(PlexSettingsRoute) }
                                )
                            }
                            entry<PlayerRoute> { route ->
                                PlayerScreen(
                                    mediaId = route.mediaId,
                                    onBack = { backStack.removeLastOrNull() }
                                )
                            }
                            entry<PlexPlayerRoute> { route ->
                                PlexPlayerScreen(
                                    onBack = { backStack.removeLastOrNull() },
                                    ratingKey = route.ratingKey,
                                    title = route.title
                                )
                            }
                            entry<PlexSettingsRoute> {
                                PlexSettingsScreen(onBack = { backStack.removeLastOrNull() })
                            }
                            entry<PlexOnDeckRoute> {
                                PlexOnDeckScreen(
                                    onPlay = { ratingKey, title ->
                                        backStack.add(PlexPlayerRoute(ratingKey, title))
                                    },
                                    onNavigateToShow = { showRatingKey, showTitle, episodeRatingKey ->
                                        backStack.add(ShowDetailRoute(showRatingKey, showTitle, episodeRatingKey))
                                    }
                                )
                            }
                            entry<ShowDetailRoute> { route ->
                                PlexShowScreen(
                                    showRatingKey = route.showRatingKey,
                                    showTitle = route.showTitle,
                                    initialEpisodeRatingKey = route.initialEpisodeRatingKey,
                                    onNavigateToPlayer = { ratingKey, title ->
                                        backStack.add(PlexPlayerRoute(ratingKey, title))
                                    }
                                )
                            }
                            entry<PlexLibrariesRoute> {
                                PlexLibrariesScreen()
                            }
                        }
                    )
                }
            }
        }
    }
}
