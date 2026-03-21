package com.neilturner.perfview.ui.theme

import androidx.compose.runtime.Composable
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PerfViewTheme(
    content: @Composable () -> Unit,
) {
    val colorScheme = darkColorScheme(
        primary = OceanBlue,
        secondary = Seafoam,
        tertiary = Mist,
        background = InkBlue,
        surface = DeepTeal,
        onPrimary = InkBlue,
        onSecondary = InkBlue,
        onBackground = Mist,
        onSurface = Mist,
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
