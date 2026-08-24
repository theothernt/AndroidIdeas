package com.neilturner.inputtest.ui

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.neilturner.inputtest.ui.theme.InputTestTheme
import kotlinx.serialization.Serializable

@Serializable
data object Input : NavKey

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun InputTestApp() {
	InputTestTheme {
		val backStack = rememberNavBackStack(Input)
		NavDisplay(
			backStack = backStack,
			entryProvider = { key ->
				when (key) {
					Input -> NavEntry(Input) {
						InputScreen()
					}
					else -> NavEntry(key) {
						InputScreen()
					}
				}
			}
		)
	}
}
