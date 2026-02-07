package com.neilturner.fourlayers.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.neilturner.fourlayers.model.LayerState
import com.neilturner.fourlayers.model.MediaItem
import com.neilturner.fourlayers.model.PlaybackState
import com.neilturner.fourlayers.model.RendererType

/**
 * Main media playlist player composable.
 * Uses a Fixed Pool strategy: Renderer A and Renderer B are always present.
 * They simply fade in/out and swap Z-index.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MediaPlayer(
	viewModel: MediaPlayerViewModel,
	modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    
    // Determine target alphas based on Active Renderer and Transition State
    // If Active=A and Transitioning -> A fades out (0), B fades in (1)
    // If Active=A and Not Transitioning -> A is visible (1), B is hidden (0)
    // Vice versa for B.
    
    val isTransitioning = state.playbackState is PlaybackState.Transitioning
    val activeIsA = state.activeRenderer == RendererType.RendererA
    
    val (targetAlphaA, targetAlphaB) = if (activeIsA) {
        if (isTransitioning) 0f to 1f else 1f to 0f
    } else {
        if (isTransitioning) 1f to 0f else 0f to 1f
    }
    
    // Animate alphas
    // Use 0 duration if fade disabled, else 1000ms
    val animSpec = if (state.isFadeEnabled) tween<Float>(2000) else tween(0)
    
    val alphaA by animateFloatAsState(targetValue = targetAlphaA, animationSpec = animSpec, label = "alphaA")
    val alphaB by animateFloatAsState(targetValue = targetAlphaB, animationSpec = animSpec, label = "alphaB")
    
    // Z-Index: invalid/incoming renderer should be ON TOP to fade in over the current one.
    // If Active=A, B is incoming (or hidden). So B should be on Top (Z=1).
    // If Active=B, A is incoming (or hidden). So A should be on Top (Z=1).
    val zIndexA = if (!activeIsA) 1f else 0f
    val zIndexB = if (activeIsA) 1f else 0f

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Renderer A
        MediaLayer(
            layerState = state.rendererA,
            alpha = alphaA,
            onMediaReady = viewModel::onMediaReady,
            modifier = Modifier
                .fillMaxSize()
                .zIndex(zIndexA)
        )

        // Renderer B
        MediaLayer(
            layerState = state.rendererB,
            alpha = alphaB,
            onMediaReady = viewModel::onMediaReady,
            modifier = Modifier
                .fillMaxSize()
                .zIndex(zIndexB)
        )

        // Show RAM Usage Widget
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
                .zIndex(100f), // Always on top
            contentAlignment = Alignment.TopEnd
        ) {
            Text(
                text = "RAM: ${state.ramUsageMb} MB",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .background(
                        color = Color.Black.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(8.dp)
                    .clickable { viewModel.toggleFade() }
            )
        }
        
        // Startup Loading Overlay
        AnimatedVisibility(
            visible = state.isLoading,
            enter = fadeIn(),
            exit = fadeOut(animationSpec = tween(1000)),
            modifier = Modifier.zIndex(200f) // Top-most
        ) {
            StartupOverlay()
        }
    }
}

/**
 * Dispatches to the appropriate layer type based on state.
 */
@Composable
private fun MediaLayer(
    layerState: LayerState,
    alpha: Float,
    onMediaReady: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Optimization: if alpha is 0, we can skip rendering content? 
    // NO! TextureView needs to be attached to the window to be ready.
    // If we remove it from composition, we lose the "warm" state.
    // Keep it in composition always, just alpha 0.
    
    when (layerState) {
        is LayerState.Empty -> {
            // Render nothing or empty box
        }
        is LayerState.ShowingImage -> {
            ImageLayer(
                item = layerState.item,
                alpha = alpha,
                onMediaReady = onMediaReady,
                modifier = modifier
            )
        }
        is LayerState.ShowingVideo -> {
            VideoLayer(
                player = layerState.player,
                alpha = alpha,
                modifier = modifier
            )
        }
    }
}

/**
 * Image layer using Coil AsyncImage.
 */
@Composable
private fun ImageLayer(
    item: MediaItem.Image,
    alpha: Float,
    onMediaReady: () -> Unit,
    modifier: Modifier = Modifier
) {
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(item.url)
            .crossfade(false) // We handle our own crossfade
            .build(),
        onSuccess = { onMediaReady() },
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.alpha(alpha)
    )
}

/**
 * Video layer using ExoPlayer with TextureView for fade/alpha support.
 */
@Composable
private fun VideoLayer(
    player: ExoPlayer,
    alpha: Float,
    modifier: Modifier = Modifier
) {
    PlayerSurface(
        player = player,
        surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
        modifier = modifier
            .fillMaxSize()
            .alpha(alpha)
    )
}

/**
 * Startup loading overlay.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun StartupOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Loading...",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}
