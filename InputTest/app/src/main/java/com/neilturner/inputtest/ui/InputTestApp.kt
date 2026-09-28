package com.neilturner.inputtest.ui

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.neilturner.inputtest.ui.examples.TextFieldExamplesScreen
import com.neilturner.inputtest.ui.home.HomeScreen
import com.neilturner.inputtest.ui.input.InputScreen
import com.neilturner.inputtest.ui.theme.InputTestTheme
import kotlinx.serialization.Serializable

@Serializable
data object Home : NavKey

@Serializable
data object Input : NavKey

@Serializable
data object TextFieldExamples : NavKey

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun InputTestApp() {
	InputTestTheme {
		val backStack = rememberNavBackStack(Home)
		NavDisplay(
			backStack = backStack,
			onBack = { backStack.removeLastOrNull() },
			entryProvider = { key ->
				when (key) {
					Home -> NavEntry(Home) {
						HomeScreen(
							onOpenInputScreen = { backStack.add(Input) },
							onOpenTextFieldExamples = { backStack.add(TextFieldExamples) }
						)
					}

					Input -> NavEntry(Input) {
						InputScreen()
					}

					TextFieldExamples -> NavEntry(TextFieldExamples) {
						TextFieldExamplesScreen()
					}

					else -> NavEntry(key) {
						HomeScreen(
							onOpenInputScreen = { backStack.add(Input) },
							onOpenTextFieldExamples = { backStack.add(TextFieldExamples) }
						)
					}
				}
			}
		)
	}
}
