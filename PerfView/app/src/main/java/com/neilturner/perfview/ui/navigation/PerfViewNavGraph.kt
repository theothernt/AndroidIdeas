package com.neilturner.perfview.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation3.ui.NavDisplay
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import com.neilturner.perfview.ui.dashboard.PerfViewRoute

/**
 * Navigation graph for PerfView app.
 * Uses Navigation 2.8+ type-safe navigation with @Serializable destinations.
 */
@Composable
fun PerfViewNavGraph() {
    val backStack = rememberNavBackStack(PerfViewDestinations.Dashboard)

    NavDisplay(
        backStack = backStack,
        entryProvider = entryProvider {
            entry<PerfViewDestinations.Dashboard> {
                PerfViewRoute()
            }
        }
    )
}
