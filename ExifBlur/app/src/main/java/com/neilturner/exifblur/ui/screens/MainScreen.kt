package com.neilturner.exifblur.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import com.neilturner.exifblur.ui.components.ErrorOverlay
import com.neilturner.exifblur.ui.components.ImageCountOverlay
import com.neilturner.exifblur.ui.components.LoadingContent
import com.neilturner.exifblur.ui.components.MetadataOverlay
import com.neilturner.exifblur.ui.components.RamUsageOverlay
import com.neilturner.exifblur.ui.components.Slideshow
import com.neilturner.exifblur.ui.components.SlideshowData
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        viewModel.loadImages()
        // The root Surface is the only focusable element, so it needs focus
        // for D-pad/Enter key events to be delivered on TV.
        focusRequester.requestFocus()
    }

    // Keep the screen on during the slideshow
    DisposableEffect(Unit) {
        val activity = context.findActivity()
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown &&
                    (keyEvent.key == Key.DirectionCenter || keyEvent.key == Key.Enter)) {
                    viewModel.toggleOverlays()
                    true
                } else {
                    false
                }
            },
        shape = RectangleShape,
        colors = SurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.background
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            uiState.currentDisplayImage?.let { displayImage ->
                Slideshow(
                    currentImage = SlideshowData(bitmap = displayImage.bitmap),
                    transitionDuration = uiState.transitionDuration
                )
            }

            // Black Splash Overlay that fades out once the first image is loaded
            AnimatedVisibility(
                visible = uiState.isLoading || uiState.currentDisplayImage == null,
                enter = fadeIn(),
                exit = fadeOut(animationSpec = tween(durationMillis = 1000))
            ) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black))
            }

            if (uiState.isLoading) {
                LoadingContent()
            } else {
                val imageError = uiState.imageError
                if (!imageError.isNullOrBlank()) {
                    ErrorOverlay(
                        message = imageError,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                ImageCountOverlay(
                    isVisible = uiState.areOverlaysVisible,
                    currentImageIndex = uiState.currentImageIndex,
                    imageCount = uiState.imageCount,
                    imageSource = uiState.imageSource,
                    modifier = Modifier.align(Alignment.BottomStart)
                )

                MetadataOverlay(
                    label = uiState.currentDisplayImage?.metadataLabel,
                    modifier = Modifier.align(Alignment.BottomEnd)
                )

                RamUsageOverlay(
                    isVisible = uiState.areOverlaysVisible,
                    ramInfo = uiState.ramInfo,
                    modifier = Modifier.align(Alignment.TopEnd)
                )
            }
        }
    }
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
