package com.neilturner.perfview.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.neilturner.perfview.ui.dashboard.PerfViewRoute

/**
 * Navigation graph for PerfView app.
 * Uses Navigation 2.8+ type-safe navigation with @Serializable destinations.
 */
@Composable
fun PerfViewNavGraph(
    navController: NavHostController,
) {
    NavHost(
        navController = navController,
        startDestination = PerfViewDestinations.Dashboard,
    ) {
        composable<PerfViewDestinations.Dashboard> {
            PerfViewRoute()
        }
    }
}
