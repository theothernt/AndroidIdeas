package com.neilturner.inputtest.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * The app theme is based on TV Material 3, which does not supply a colour scheme for the
 * standard Material 3 components (TextField, SearchBar, ...). Screens that use those wrap
 * their content in this so the M3 components pick up matching dark colours.
 */
@Composable
fun ComposeMaterial3Theme(content: @Composable () -> Unit) {
	androidx.compose.material3.MaterialTheme(
		colorScheme = darkColorScheme(
			primary = Purple80,
			secondary = PurpleGrey80,
			tertiary = Pink80,
			background = Color.Black,
			surface = Color.Black,
			surfaceContainer = Color(0xFF1C1B1F)
		),
		content = content
	)
}
