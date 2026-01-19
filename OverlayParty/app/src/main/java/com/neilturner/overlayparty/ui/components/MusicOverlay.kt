package com.neilturner.overlayparty.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun MusicOverlay(text: String, modifier: Modifier = Modifier) {
    TextBlock(
        text = text,
        icon = Icons.Default.MusicNote,
        modifier = modifier
    )
}
