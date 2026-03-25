package com.neilturner.perfview.ui.dashboard

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.neilturner.perfview.ui.theme.PerfMint
import com.neilturner.perfview.ui.theme.PerfSky

@Composable
fun PollingIndicator(
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "polling")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pollingRotation",
    )

    Box(
        modifier = modifier
            .size(10.dp)
            .rotate(rotation),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(10.dp)) {
            drawArc(
                color = PerfMint,
                startAngle = 0f,
                sweepAngle = 250f,
                useCenter = false,
                style = Stroke(width = 1.5.dp.toPx()),
            )
        }
    }
}

@Composable
fun WaitingIndicator(
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "authorization")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "authorizationRotation",
    )

    Box(
        modifier = modifier
            .size(48.dp)
            .rotate(rotation),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(48.dp)) {
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        PerfMint,
                        PerfSky,
                        PerfMint,
                    )
                ),
                startAngle = 0f,
                sweepAngle = 300f,
                useCenter = false,
                style = Stroke(width = 4.dp.toPx()),
            )
        }
    }
}
