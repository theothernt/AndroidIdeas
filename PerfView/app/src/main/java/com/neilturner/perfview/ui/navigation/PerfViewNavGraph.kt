package com.neilturner.perfview.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.neilturner.perfview.data.adb.AdbConnectionGate
import com.neilturner.perfview.platform.NotificationPermissionChecker
import com.neilturner.perfview.ui.dashboard.PerfViewRoute
import com.neilturner.perfview.ui.intro.IntroRoute

/**
 * Navigation graph for PerfView app.
 * Uses Navigation 2.8+ type-safe navigation with @Serializable destinations.
 *
 * The start destination is decided by whether the app is already set up: an authorized ADB
 * session and granted notification access mean the checklist has nothing left to ask, so the
 * dashboard is shown directly. Both checks are cheap local reads, so this stays a synchronous
 * decision rather than a frame of the intro appearing on every return.
 *
 * "Run in the background" finishes the Activity while the overlay service keeps the process
 * alive, so returning to the app builds a brand new Activity with no saved state. That is why
 * readiness is remembered in the process rather than in the back stack.
 */
@Composable
fun PerfViewNavGraph(
    adbConnectionGate: AdbConnectionGate,
    notificationPermissionChecker: NotificationPermissionChecker,
) {
    val isFullySetUp = adbConnectionGate.isAuthorized && notificationPermissionChecker.isGranted()

    val startDestination = remember(isFullySetUp) {
        if (isFullySetUp) {
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