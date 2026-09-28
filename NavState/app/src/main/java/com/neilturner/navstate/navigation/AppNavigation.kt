package com.neilturner.navstate.navigation

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.NavigationEvent
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import com.neilturner.navstate.ui.screens.CounterScreen
import com.neilturner.navstate.ui.screens.ScreenTarget
import com.neilturner.navstate.ui.theme.NavStateTheme

private const val TRANSITION_MS = 1000
private const val FADE_MS = TRANSITION_MS / 2

/**
 * Predictive back only has half a screen's worth of travel to show, so the preview slides at half
 * the distance of a committed transition.
 */
private const val PREDICTIVE_TRAVEL_DIVISOR = 2

@Composable
fun AppNavigation(
    backStack: NavBackStack<NavKey> = rememberNavBackStack(ScreenOne),
) {
    val entryDecorators = listOf<NavEntryDecorator<NavKey>>(
        rememberSaveableStateHolderNavEntryDecorator(),
        rememberViewModelStoreNavEntryDecorator(),
    )

    // NavDisplay only registers a back handler while there is an entry to pop, so back press on
    // the root entry would otherwise be swallowed and trap the user on the first screen.
    val activity = LocalActivity.current
    BackHandler(enabled = backStack.size == 1) {
        activity?.finish()
    }

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        transitionSpec = {
            ContentTransform(
                targetContentEnter = fadeIn(tween(FADE_MS)) +
                    slideInHorizontally(tween(TRANSITION_MS)) { it },
                initialContentExit = fadeOut(tween(FADE_MS)) +
                    slideOutHorizontally(tween(TRANSITION_MS)) { -it },
            )
        },
        popTransitionSpec = {
            ContentTransform(
                targetContentEnter = fadeIn(tween(FADE_MS)) +
                    slideInHorizontally(tween(TRANSITION_MS)) { -it },
                initialContentExit = fadeOut(tween(FADE_MS)) +
                    slideOutHorizontally(tween(TRANSITION_MS)) { it },
            )
        },
        predictivePopTransitionSpec = { swipeEdge ->
            // Swiping in from the left edge uncovers the previous screen on the left, so it enters
            // from the left while the current screen exits to the right, and vice versa.
            val signum = if (swipeEdge == NavigationEvent.EDGE_LEFT) -1 else 1
            ContentTransform(
                targetContentEnter = fadeIn(tween(FADE_MS)) +
                    slideInHorizontally(tween(TRANSITION_MS)) {
                        signum * it / PREDICTIVE_TRAVEL_DIVISOR
                    },
                initialContentExit = fadeOut(tween(FADE_MS)) +
                    slideOutHorizontally(tween(TRANSITION_MS)) {
                        -signum * it / PREDICTIVE_TRAVEL_DIVISOR
                    },
            )
        },
        entryProvider = entryProvider {
            entry<ScreenOne> {
                CounterScreen(number = 1, next = ScreenTarget(2) { backStack.add(ScreenTwo) })
            }
            entry<ScreenTwo> {
                CounterScreen(
                    number = 2,
                    next = ScreenTarget(3) { backStack.add(ScreenThree) },
                    previous = ScreenTarget(1) { backStack.removeLastOrNull() },
                )
            }
            entry<ScreenThree> {
                CounterScreen(
                    number = 3,
                    next = ScreenTarget(4) { backStack.add(ScreenFour) },
                    previous = ScreenTarget(2) { backStack.removeLastOrNull() },
                )
            }
            entry<ScreenFour> {
                CounterScreen(
                    number = 4,
                    next = ScreenTarget(5) { backStack.add(ScreenFive) },
                    previous = ScreenTarget(3) { backStack.removeLastOrNull() },
                )
            }
            entry<ScreenFive> {
                CounterScreen(
                    number = 5,
                    next = ScreenTarget(6) { backStack.add(ScreenSix) },
                    previous = ScreenTarget(4) { backStack.removeLastOrNull() },
                )
            }
            entry<ScreenSix> {
                CounterScreen(
                    number = 6,
                    previous = ScreenTarget(5) { backStack.removeLastOrNull() },
                )
            }
        },
        entryDecorators = entryDecorators,
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Preview(showBackground = true, widthDp = 960, heightDp = 540)
@Composable
private fun AppNavigationPreview() {
    NavStateTheme {
        Surface(modifier = Modifier.fillMaxSize(), shape = RectangleShape) {
            AppNavigation()
        }
    }
}
