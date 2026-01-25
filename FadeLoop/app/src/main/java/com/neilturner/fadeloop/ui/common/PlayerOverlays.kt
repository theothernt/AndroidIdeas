package com.neilturner.fadeloop.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

// Reusable modifier for the technical background style
fun Modifier.technicalBackground(): Modifier = this
    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
    .padding(horizontal = 8.dp, vertical = 4.dp)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TimeRemainingOverlay(
    remainingSeconds: Long,
    modifier: Modifier = Modifier
) {
    val alpha by animateFloatAsState(
        targetValue = if (remainingSeconds > 0) 1f else 0f,
        animationSpec = tween(durationMillis = 500),
        label = "countdownAlpha"
    )

    Text(
        text = "${remainingSeconds}s",
        style = MaterialTheme.typography.labelSmall,
        color = Color.Green,
        modifier = modifier
            .alpha(alpha)
            .technicalBackground()
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LocationOverlay(
    locationText: String,
    isVisible: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Text(
            text = locationText,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            modifier = Modifier.technicalBackground()
        )
    }
}
