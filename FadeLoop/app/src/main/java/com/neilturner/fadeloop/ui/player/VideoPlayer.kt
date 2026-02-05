package com.neilturner.fadeloop.ui.player

import androidx.annotation.OptIn
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import com.neilturner.fadeloop.data.cache.VideoCacheManager
import com.neilturner.fadeloop.data.model.Video
import com.neilturner.fadeloop.ui.common.LocationOverlay
import com.neilturner.fadeloop.ui.common.MemoryMonitor
import com.neilturner.fadeloop.ui.common.TimeRemainingOverlay
import org.koin.compose.koinInject

private const val TAG = "VideoPlayer"

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(
    videos: List<Video>,
    useSurfaceView: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (videos.isEmpty()) return

    val context = LocalContext.current
    val cacheManager: VideoCacheManager = koinInject()
    val scope = rememberCoroutineScope()
    val state = remember {
        VideoPlayerState(
            context = context,
            cacheManager = cacheManager,
            scope = scope,
            config = VideoPlayerConfig()
        )
    }
    
    LaunchedEffect(Unit) {
        Log.d(TAG, "Initializing VideoPlayer with ${videos.size} videos. useSurfaceView=$useSurfaceView")
    }

    // Technical Overlays visibility state
    var showTechnicalOverlays by remember { mutableStateOf(false) }

    LaunchedEffect(videos) {
        state.initialSetup(videos)
    }

    // Cleanup
    DisposableEffect(Unit) {
        onDispose {
            state.release()
        }
    }

    LaunchedEffect(state.activePlayerIs1, state.currentVideoIndex, useSurfaceView, videos) {
        state.launchPlaybackLoop(videos = videos, useSurfaceView = useSurfaceView)
    }


    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusable()
            .technicalOverlayToggle { showTechnicalOverlays = !showTechnicalOverlays }
    ) {

        CrossFadeVideoRenderer(
            player1 = state.player1,
            player2 = state.player2,
            useSurfaceView = useSurfaceView,
            activePlayerIs1 = state.activePlayerIs1,
            player1Alpha = state.player1Alpha.value,
            player2Alpha = state.player2Alpha.value
        )

        StartupOverlay(startupBlackAlpha = state.startupBlackAlpha.value)

        if (showTechnicalOverlays) {
            TimeRemainingOverlay(
                remainingSeconds = state.remainingSeconds,
                modifier = Modifier
	                .align(Alignment.TopStart)
                    .padding(16.dp)
            )

            MemoryMonitor(
                modifier = Modifier
	                .align(Alignment.TopEnd)
                    .padding(16.dp)
            )
        }

        LocationOverlay(
            locationText = state.locationText,
            isVisible = state.showLocation,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        )
    }
}

private fun Modifier.technicalOverlayToggle(onToggle: () -> Unit): Modifier {
    return onKeyEvent { keyEvent ->
        if (
            keyEvent.type == KeyEventType.KeyUp &&
            (keyEvent.key == Key.DirectionCenter || keyEvent.key == Key.Enter)
        ) {
            onToggle()
            true
        } else {
            false
        }
    }
}

@Composable
private fun CrossFadeVideoRenderer(
	player1: ExoPlayer,
	player2: ExoPlayer,
	useSurfaceView: Boolean,
	activePlayerIs1: Boolean,
	player1Alpha: Float,
	player2Alpha: Float,
	modifier: Modifier = Modifier
) {
    if (useSurfaceView) {
        if (activePlayerIs1) {
            PlayerSurface(
                player = player1,
                surfaceType = SURFACE_TYPE_SURFACE_VIEW,
                modifier = modifier.fillMaxSize()
            )
        } else {
            PlayerSurface(
                player = player2,
                surfaceType = SURFACE_TYPE_SURFACE_VIEW,
                modifier = modifier.fillMaxSize()
            )
        }
    } else {
        val p1ZIndex = if (activePlayerIs1) 0f else 1f
        val p2ZIndex = if (activePlayerIs1) 1f else 0f

        PlayerSurface(
            player = player1,
            surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
            modifier = modifier
                .fillMaxSize()
                .zIndex(p1ZIndex)
                .alpha(player1Alpha)
        )

        PlayerSurface(
            player = player2,
            surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
            modifier = modifier
                .fillMaxSize()
                .zIndex(p2ZIndex)
                .alpha(player2Alpha)
        )
    }
}

@Composable
private fun StartupOverlay(
    startupBlackAlpha: Float,
    modifier: Modifier = Modifier
) {
    if (startupBlackAlpha > 0f) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .alpha(startupBlackAlpha)
                .background(Color.Black)
        )
    }
}