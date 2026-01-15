package com.neilturner.exifblur.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.neilturner.exifblur.ui.screens.MainScreen

sealed class Screen(val route: String) {
    object Main : Screen("main")
}

@Composable
fun ExifBlurNavGraph() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Screen.Main.route
    ) {
        composable(Screen.Main.route) {
            MainScreen()
        }
    }
}
