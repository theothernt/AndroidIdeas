package com.neilturner.overlayparty.ui.main

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.neilturner.overlayparty.ui.overlay.OverlayLayout
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel

private const val SLIDESHOW_IMAGE_PATH = "file:///android_asset/slideshow/"

@Composable
fun ScreenTwo(
    modifier: Modifier = Modifier,
    viewModel: ScreenTwoViewModel = koinViewModel(),
) {
    val context = LocalContext.current
    val topStart by viewModel.topStartOverlay.collectAsState()
    val topEnd by viewModel.topEndOverlay.collectAsState()
    val bottomStart by viewModel.bottomStartOverlay.collectAsState()
    val bottomEnd by viewModel.bottomEndOverlay.collectAsState()
    val isOverlaysVisible by viewModel.isOverlaysVisible.collectAsState()

    val groupAlpha by animateFloatAsState(
        targetValue = if (isOverlaysVisible) 1f else 0f,
        animationSpec =
            tween(
                durationMillis =
                    if (isOverlaysVisible) {
                        ScreenTwoViewModel.FADE_IN_DURATION_MS.toInt()
                    } else {
                        ScreenTwoViewModel.FADE_OUT_DURATION_MS.toInt()
                    },
            ),
        label = "GroupOverlayFade",
    )

    var showInitialBlackout by remember { mutableStateOf(true) }
    val blackoutAlpha by animateFloatAsState(
        targetValue = if (showInitialBlackout) 1f else 0f,
        animationSpec = tween(durationMillis = 500),
        label = "InitialBlackoutFade",
    )

    LaunchedEffect(Unit) {
        delay(500) // Brief pause before starting the fade
        showInitialBlackout = false
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color.Black),
    ) {
        AsyncImage(
            model =
                ImageRequest
                    .Builder(context)
                    .data(SLIDESHOW_IMAGE_PATH + "tomo_m_wegl4zgnhpe_unsplash.jpg")
                    .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        // Gradient at the top to make overlays more readable
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

        OverlayLayout(
            topStart = topStart,
            topEnd = topEnd,
            bottomStart = bottomStart,
            bottomEnd = bottomEnd,
            modifier = Modifier.alpha(groupAlpha),
        )

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
}

