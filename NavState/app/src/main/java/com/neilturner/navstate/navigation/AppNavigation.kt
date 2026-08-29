package com.neilturner.navstate.navigation

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.neilturner.navstate.ui.screens.ScreenOne
import com.neilturner.navstate.ui.screens.ScreenSix
import com.neilturner.navstate.ui.screens.ScreenFour
import com.neilturner.navstate.ui.screens.ScreenThree
import com.neilturner.navstate.ui.screens.ScreenTwo
import com.neilturner.navstate.ui.screens.ScreenFive

@Composable
fun AppNavigation() {
    val backStack = rememberNavBackStack(ScreenOne)

    val decorators = listOf<NavEntryDecorator<NavKey>>(
        rememberViewModelStoreNavEntryDecorator()
    )

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        transitionSpec = {
            ContentTransform(
                targetContentEnter = fadeIn(tween(300)) + slideInHorizontally(tween(300)) { it },
                initialContentExit = fadeOut(tween(300)) + slideOutHorizontally(tween(300)) { -it }
            )
        },
        popTransitionSpec = {
            ContentTransform(
                targetContentEnter = fadeIn(tween(300)) + slideInHorizontally(tween(300)) { -it },
                initialContentExit = fadeOut(tween(300)) + slideOutHorizontally(tween(300)) { it }
            )
        },
        predictivePopTransitionSpec = { swipeEdge ->
            ContentTransform(
                targetContentEnter = fadeIn(tween(300)) + slideInHorizontally(tween(300)) { -it / 2 },
                initialContentExit = fadeOut(tween(300)) + slideOutHorizontally(tween(300)) { it / 2 }
            )
        },
        entryProvider = entryProvider {
            entry<ScreenOne> {
                ScreenOne(
                    onNavigateToScreenTwo = { backStack.add(ScreenTwo) }
                )
            }
            entry<ScreenTwo> {
                ScreenTwo(
                    onNavigateToScreenOne = { backStack.removeLastOrNull() },
                    onNavigateToScreenThree = { backStack.add(ScreenThree) }
                )
            }
            entry<ScreenThree> {
                ScreenThree(
                    onNavigateToScreenTwo = { backStack.removeLastOrNull() },
                    onNavigateToScreenFour = { backStack.add(ScreenFour) }
                )
            }
            entry<ScreenFour> {
                ScreenFour(
                    onNavigateToScreenThree = { backStack.removeLastOrNull() },
                    onNavigateToScreenFive = { backStack.add(ScreenFive) }
                )
            }
            entry<ScreenFive> {
                ScreenFive(
                    onNavigateToScreenFour = { backStack.removeLastOrNull() },
                    onNavigateToScreenSix = { backStack.add(ScreenSix) }
                )
            }
            entry<ScreenSix> {
                ScreenSix(
                    onNavigateToScreenFive = { backStack.removeLastOrNull() }
                )
            }
        },
        entryDecorators = decorators
    )
}