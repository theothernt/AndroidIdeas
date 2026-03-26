package com.neilturner.perfview.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.neilturner.perfview.ui.dashboard.PerfViewRoute

@Composable
fun PerfViewNavGraph(
    navController: NavHostController,
) {
    NavHost(
        navController = navController,
        startDestination = "dashboard",
    ) {
        composable(
            route = "dashboard",
        ) {
            PerfViewRoute()
        }
    }
}
