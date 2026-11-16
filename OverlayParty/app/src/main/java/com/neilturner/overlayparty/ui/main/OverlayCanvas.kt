package com.neilturner.overlayparty.ui.main

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.neilturner.overlayparty.ui.overlay.OverlayContent
import com.neilturner.overlayparty.ui.overlay.OverlayLayout
import kotlinx.coroutines.delay

/**
 * Shared scaffolding for the two overlay screens: background, top readability gradient,
 * the four-corner overlay layout and the initial blackout fade.
 */
@Composable
fun OverlayCanvas(
    background: @Composable BoxScope.() -> Unit,
    topStart: OverlayContent?,
    topEnd: OverlayContent?,
    bottomStart: OverlayContent?,
    bottomEnd: OverlayContent?,
    modifier: Modifier = Modifier,
    overlaysAlpha: Float = 1f,
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color.Black),
    ) {
        background()
        TopGradient()
        OverlayLayout(
            topStart = topStart,
            topEnd = topEnd,
            bottomStart = bottomStart,
            bottomEnd = bottomEnd,
            modifier = Modifier.alpha(overlaysAlpha),
        )
        InitialBlackout()
    }
}

@Composable
private fun TopGradient() {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(300.dp)
                .background(
                    Brush.verticalGradient(
                        0.00f to Color.Black.copy(alpha = 0.80f),
                        0.25f to Color.Black.copy(alpha = 0.55f),
                        0.50f to Color.Black.copy(alpha = 0.30f),
                        0.70f to Color.Black.copy(alpha = 0.15f),
                        0.85f to Color.Black.copy(alpha = 0.05f),
                        1.00f to Color.Transparent,
                    ),
                ),
    )
}

@Composable
private fun InitialBlackout() {
    var showInitialBlackout by remember { mutableStateOf(true) }
    val blackoutAlpha by animateFloatAsState(
        targetValue = if (showInitialBlackout) 1f else 0f,
        animationSpec = tween(durationMillis = 500),
        label = "InitialBlackoutFade",
    )

    LaunchedEffect(Unit) {
        delay(500)
        showInitialBlackout = false
    }

    if (blackoutAlpha > 0f) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .alpha(blackoutAlpha)
                    .background(Color.Black),
        )
    }
}
