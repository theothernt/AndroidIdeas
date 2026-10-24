package com.neilturner.arrangethings.ui.theme

import androidx.compose.runtime.Composable
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ArrangeThingsTheme(
	content: @Composable () -> Unit,
) {
	val colorScheme = darkColorScheme(
		primary = MonochromeWhite,
		onPrimary = MonochromeBlack,
		primaryContainer = MonochromeSurfaceVariant,
		onPrimaryContainer = MonochromeWhite,
		inversePrimary = MonochromeOnSurfaceVariant,
		secondary = MonochromeWhite,
		onSecondary = MonochromeBlack,
		secondaryContainer = MonochromeSurfaceVariant,
		onSecondaryContainer = MonochromeWhite,
		tertiary = MonochromeWhite,
		onTertiary = MonochromeBlack,
		tertiaryContainer = MonochromeSurfaceVariant,
		onTertiaryContainer = MonochromeWhite,
		background = MonochromeBlack,
		onBackground = MonochromeWhite,
		surface = MonochromeSurface,
		onSurface = MonochromeWhite,
		surfaceVariant = MonochromeSurfaceVariant,
		onSurfaceVariant = MonochromeOnSurfaceVariant,
		inverseSurface = MonochromeWhite,
		inverseOnSurface = MonochromeBlack,
		error = MonochromeWhite,
		onError = MonochromeBlack,
		errorContainer = MonochromeSurfaceVariant,
		onErrorContainer = MonochromeWhite,
		border = MonochromeOutline,
		borderVariant = MonochromeOutline,
		scrim = MonochromeBlack
	)
	MaterialTheme(
		colorScheme = colorScheme,
		typography = Typography,
		content = content
	)
}
