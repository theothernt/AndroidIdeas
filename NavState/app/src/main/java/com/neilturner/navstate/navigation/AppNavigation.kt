package com.neilturner.navstate.navigation

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.neilturner.navstate.ui.screens.ScreenFiveContent
import com.neilturner.navstate.ui.screens.ScreenFourContent
import com.neilturner.navstate.ui.screens.ScreenOneContent
import com.neilturner.navstate.ui.screens.ScreenSixContent
import com.neilturner.navstate.ui.screens.ScreenThreeContent
import com.neilturner.navstate.ui.screens.ScreenTwoContent

private const val TRANSITION_MS = 200

@Composable
fun AppNavigation(
    backStack: NavBackStack<NavKey> = rememberNavBackStack(ScreenOne),
) {
    val decorators = listOf<NavEntryDecorator<NavKey>>(
        rememberSaveableStateHolderNavEntryDecorator(),
        rememberViewModelStoreNavEntryDecorator(),
    )

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        transitionSpec = {
            ContentTransform(
                targetContentEnter = fadeIn(tween(TRANSITION_MS)) + slideInHorizontally(tween(TRANSITION_MS)) { it },
                initialContentExit = fadeOut(tween(TRANSITION_MS)) + slideOutHorizontally(tween(TRANSITION_MS)) { -it }
            )
        },
        popTransitionSpec = {
            ContentTransform(
                targetContentEnter = fadeIn(tween(TRANSITION_MS)) + slideInHorizontally(tween(TRANSITION_MS)) { -it },
                initialContentExit = fadeOut(tween(TRANSITION_MS)) + slideOutHorizontally(tween(TRANSITION_MS)) { it }
            )
        },
        predictivePopTransitionSpec = { swipeEdge ->
            ContentTransform(
                targetContentEnter = fadeIn(tween(TRANSITION_MS)) + slideInHorizontally(tween(TRANSITION_MS)) { -it / 2 },
                initialContentExit = fadeOut(tween(TRANSITION_MS)) + slideOutHorizontally(tween(TRANSITION_MS)) { it / 2 }
            )
        },
        entryProvider = entryProvider {
            entry<ScreenOne> {
                ScreenOneContent(
                    onNavigateToScreenTwo = { backStack.add(ScreenTwo) }
                )
            }
            entry<ScreenTwo> {
                ScreenTwoContent(
                    onNavigateToScreenOne = { backStack.removeLastOrNull() },
                    onNavigateToScreenThree = { backStack.add(ScreenThree) }
                )
            }
            entry<ScreenThree> {
                ScreenThreeContent(
                    onNavigateToScreenTwo = { backStack.removeLastOrNull() },
                    onNavigateToScreenFour = { backStack.add(ScreenFour) }
                )
            }
            entry<ScreenFour> {
                ScreenFourContent(
                    onNavigateToScreenThree = { backStack.removeLastOrNull() },
                    onNavigateToScreenFive = { backStack.add(ScreenFive) }
                )
            }
            entry<ScreenFive> {
                ScreenFiveContent(
                    onNavigateToScreenFour = { backStack.removeLastOrNull() },
                    onNavigateToScreenSix = { backStack.add(ScreenSix) }
                )
            }
            entry<ScreenSix> {
                ScreenSixContent(
                    onNavigateToScreenFive = { backStack.removeLastOrNull() }
                )
            }
        },
        entryDecorators = decorators
    )
}