package com.neilturner.perfview.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.neilturner.perfview.ui.dashboard.PerfViewRoute
import com.neilturner.perfview.ui.intro.IntroRoute

/**
 * Navigation graph for PerfView app.
 * Uses Navigation 2.8+ type-safe navigation with @Serializable destinations.
 *
 * The intro gate is the start destination and replaces itself with the dashboard rather than
 * pushing on top of it: the gate has no meaning once ADB is authorized, and leaving it beneath
 * the dashboard would let a system back gesture return to a screen that no longer reflects
 * the app's state.
 */
@Composable
fun PerfViewNavGraph() {
    val backStack = rememberNavBackStack(PerfViewDestinations.Intro)

    NavDisplay(
        backStack = backStack,
        entryProvider = entryProvider {
            entry<PerfViewDestinations.Intro> {
                IntroRoute(
                    onNavigateToDashboard = { backStack.replaceWith(PerfViewDestinations.Dashboard) },
                )
            }

            entry<PerfViewDestinations.Dashboard> {
                PerfViewRoute()
            }
        },
    )
}

/**
 * Replaces the whole stack, since the dashboard is a root destination rather than something
 * the user can navigate back to.
 */
private fun <T : NavKey> NavBackStack<T>.replaceWith(destination: T) {
    if (contains(destination)) return
    clear()
    add(destination)
}