package com.neilturner.perfview.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.neilturner.perfview.data.adb.AdbConnectionGate
import com.neilturner.perfview.ui.dashboard.PerfViewRoute
import com.neilturner.perfview.ui.intro.IntroRoute

/**
 * Navigation graph for PerfView app.
 * Uses Navigation 2.8+ type-safe navigation with @Serializable destinations.
 *
 * The start destination depends on whether this process already holds an authorized ADB
 * session. "Run in the background" finishes the Activity while the overlay service keeps the
 * process alive, so returning to the app creates a brand new Activity with no saved state. Keying
 * the start off the session means that return goes straight to the process list instead of
 * replaying the authorization gate, and the shared process list keeps flowing the whole time.
 */
@Composable
fun PerfViewNavGraph(adbConnectionGate: AdbConnectionGate) {
    val startDestination = remember(adbConnectionGate.isAuthorized) {
        if (adbConnectionGate.isAuthorized) {
            PerfViewDestinations.Dashboard
        } else {
            PerfViewDestinations.Intro
        }
    }

    val backStack = rememberNavBackStack(startDestination)

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