package com.neilturner.visualmashup.navigation

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.neilturner.visualmashup.ui.screens.scrollingcollage.ScrollingCollageScreen
import com.neilturner.visualmashup.ui.screens.simplecollage.SimpleCollageScreen
import com.neilturner.visualmashup.ui.screens.effect.EffectScreen
import com.neilturner.visualmashup.ui.screens.menu.MenuScreen

private const val TRANSITION_MS = 600
private const val FADE_MS = TRANSITION_MS / 2

@Composable
fun AppNavigation(
    modifier: Modifier = Modifier,
    backStack: NavBackStack<NavKey> = rememberNavBackStack(MenuScreen),
) {
    val entryDecorators = listOf<NavEntryDecorator<NavKey>>(
        rememberSaveableStateHolderNavEntryDecorator(),
        rememberViewModelStoreNavEntryDecorator(),
    )

    // NavDisplay only registers a back handler while there is an entry to pop, so back on the root
    // entry would otherwise be swallowed and trap the user on the first screen.
    val activity = LocalActivity.current
    BackHandler(enabled = backStack.size == 1) {
        activity?.finish()
    }

    NavDisplay(
        modifier = modifier,
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
        entryProvider = entryProvider {
            entry<MenuScreen> {
                MenuScreen(
                    onNavigateToSimpleCollage = { backStack.add(SimpleCollageScreen) },
                    onNavigateToScrollingCollage = { backStack.add(ScrollingCollageScreen) },
                    onNavigateToEffects = { backStack.add(EffectScreen) },
                )
            }
            entry<SimpleCollageScreen> {
                SimpleCollageScreen()
            }
            entry<ScrollingCollageScreen> {
                ScrollingCollageScreen()
            }
            entry<EffectScreen> {
                EffectScreen()
            }
        },
        entryDecorators = entryDecorators,
    )
}