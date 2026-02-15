package com.neilturner.twopane

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.IntOffset
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.neilturner.twopane.ui.mainmenu.MainMenuScreen
import com.neilturner.twopane.ui.media.MediaScreen
import com.neilturner.twopane.ui.theme.TwoPaneTheme


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TwoPaneTheme {
                val backStack = remember { mutableStateListOf<AppNavKey>(AppNavKey.MainMenu) }

                BackHandler(enabled = backStack.size > 1) {
                    backStack.removeAt(backStack.size - 1)
                }

                val animationSpec = tween<IntOffset>(durationMillis = 300)

                NavDisplay<AppNavKey>(
                    backStack = backStack,
                    onBack = {
                        if (backStack.size > 1) {
                            backStack.removeAt(backStack.size - 1)
                        }
                    },
                    transitionSpec = {
                        ContentTransform(
                            targetContentEnter = slideInHorizontally(animationSpec = animationSpec, initialOffsetX = { it }),
                            initialContentExit = slideOutHorizontally(animationSpec = animationSpec, targetOffsetX = { -it })
                        )
                    },
                    popTransitionSpec = {
                        ContentTransform(
                            targetContentEnter = slideInHorizontally(animationSpec = animationSpec, initialOffsetX = { -it }),
                            initialContentExit = slideOutHorizontally(animationSpec = animationSpec, targetOffsetX = { it })
                        )
                    },
                    entryProvider = entryProvider {
                        entry<AppNavKey.MainMenu> {
                            MainMenuScreen(
                                onNavigateToMedia = { backStack.add(AppNavKey.Media) }
                            )
                        }
                        entry<AppNavKey.Media> {
                            MediaScreen(onBack = {
                                if (backStack.size > 1) {
                                    backStack.removeAt(backStack.size - 1)
                                }
                            })
                        }
                    }
                )
            }
        }
    }
}