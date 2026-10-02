package com.neilturner.videothumbnails.ui.theme

import androidx.compose.runtime.Composable
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun VideoThumbnailsTheme(content: @Composable () -> Unit) {
    // Always dark: the app surface is a fixed near-black, so a light scheme would
    // flash near-white card surfaces while thumbnails load.
    val colorScheme =
        darkColorScheme(
            primary = Purple80,
            secondary = PurpleGrey80,
            tertiary = Pink80,
        )
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
