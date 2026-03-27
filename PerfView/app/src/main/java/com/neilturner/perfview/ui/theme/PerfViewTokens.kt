package com.neilturner.perfview.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp

object PerfViewTokens {
    val screenHorizontalPadding = 24.dp
    val screenVerticalPadding = 20.dp
    val sectionSpacing = 16.dp
    val statusColumnMinWidth = 140.dp
    val cardSpacing = 12.dp
    val cardPadding = 20.dp
    val buttonHorizontalPadding = 20.dp
    val buttonVerticalPadding = 14.dp
    val cardShape = RoundedCornerShape(24.dp)
    val buttonShape = RoundedCornerShape(18.dp)
    val borderWidth = 1.dp
    val panelBackgroundBrush = Brush.linearGradient(
        colors = listOf(
            PerfInk,
            PerfInkMid,
            PerfTealBright,
        )
    )
}
