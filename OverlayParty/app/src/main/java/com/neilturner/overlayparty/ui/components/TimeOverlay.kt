package com.neilturner.overlayparty.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun TimeOverlay(text: String, modifier: Modifier = Modifier) {
    TextBlock(text = text, modifier = modifier)
}
