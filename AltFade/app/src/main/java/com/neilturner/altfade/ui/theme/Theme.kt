package com.neilturner.altfade.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AltFadeTheme(
	content: @Composable () -> Unit,
) {
	val colorScheme = darkColorScheme(
		primary = Purple80,
		secondary = PurpleGrey80,
		tertiary = Pink80,
		background = Color.Black,
		surface = Color.Black
	)
	MaterialTheme(
		colorScheme = colorScheme,
		typography = Typography,
		content = content
	)
}