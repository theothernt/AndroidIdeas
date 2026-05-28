package com.neilturner.altfade.ui.components

import android.net.Uri
import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first

private const val TAG = "CrossfadeVideoPlayer"
private const val MAX_PLAYBACK_DURATION_MS = 15000L  // Force transition at 15 seconds
private const val BACKGROUND_BUFFER_DELAY_MS = 4000L // 4 seconds before buffering next video

@Composable
fun CrossfadeVideoPlayer(
    videos: List<Uri>,
    modifier: Modifier = Modifier
) {
    if (videos.isEmpty()) {
        Log.w(TAG, "CrossfadeVideoPlayer: videos list is empty")
        return
    }

    val context = LocalContext.current

    val playerA = remember { 
        ExoPlayer.Builder(context).build().apply { repeatMode = Player.REPEAT_MODE_OFF } 
    }
    val playerB = remember { 
        ExoPlayer.Builder(context).build().apply { repeatMode = Player.REPEAT_MODE_OFF } 
    }

    var currentIndex by remember { mutableIntStateOf(0) }
    var isPlayerAFront by remember { mutableStateOf(true) }
    
    val alphaA = remember { Animatable(1f) }
    val alphaB = remember { Animatable(1f) }

    // First frame rendering detection
    var playerARenderedFirstFrame by remember { mutableStateOf(false) }
    var playerBRenderedFirstFrame by remember { mutableStateOf(false) }

    // Startup loading state
    var isAppLoading by remember { mutableStateOf(true) }
    val loadingAlpha by animateFloatAsState(
        targetValue = if (isAppLoading) 1f else 0f,
        animationSpec = tween(1000),
        label = "loading_alpha"
    )

    // Listeners for first frame detection
    DisposableEffect(playerA) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                playerARenderedFirstFrame = true
                if (isAppLoading && isPlayerAFront) {
                    Log.d(TAG, "Initial video (A) ready")
                    isAppLoading = false
                }
            }
        }
        playerA.addListener(listener)
        onDispose { playerA.removeListener(listener) }
    }

    DisposableEffect(playerB) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                playerBRenderedFirstFrame = true
                if (isAppLoading && !isPlayerAFront) {
                    Log.d(TAG, "Initial video (B) ready")
                    isAppLoading = false
                }
            }
        }
        playerB.addListener(listener)
        onDispose { playerB.removeListener(listener) }
    }

    // Initial setup
    LaunchedEffect(Unit) {
        playerA.setMediaItem(MediaItem.fromUri(videos[0]))
        playerA.prepare()
        playerA.play()
    }

    // Background pre-buffering
    LaunchedEffect(currentIndex, videos) {
        val nextIndex = (currentIndex + 1) % videos.size
        val nextUri = videos[nextIndex]
        
        delay(BACKGROUND_BUFFER_DELAY_MS)
        
        val backPlayer = if (isPlayerAFront) playerB else playerA
        Log.d(TAG, "Pre-buffering background player with: $nextUri")
        
        // Reset frame flag before preparation
        if (isPlayerAFront) playerBRenderedFirstFrame = false else playerARenderedFirstFrame = false
        
        backPlayer.setMediaItem(MediaItem.fromUri(nextUri))
        backPlayer.prepare()
        backPlayer.playWhenReady = false
    }

    // Main playback and transition logic
    LaunchedEffect(currentIndex) {
        val frontPlayer = if (isPlayerAFront) playerA else playerB
        val backPlayer = if (isPlayerAFront) playerB else playerA
        val frontAlpha = if (isPlayerAFront) alphaA else alphaB
        
        while (true) {
            delay(500)
            val duration = frontPlayer.duration
            val position = frontPlayer.currentPosition
            
            val shouldTransition = (duration != C.TIME_UNSET && duration > 0 && position >= duration - 1000) || 
                                   (position >= MAX_PLAYBACK_DURATION_MS)

            if (shouldTransition) {
                Log.d(TAG, "Starting transition: Pausing front player. Back player status: renderedFirstFrame=${if (isPlayerAFront) playerBRenderedFirstFrame else playerARenderedFirstFrame}")
                frontPlayer.pause()
                
                Log.d(TAG, "Starting back player")
                backPlayer.play()
                
                // Wait for the first frame to actually render (if it hasn't already during preparation)
                snapshotFlow { if (isPlayerAFront) playerBRenderedFirstFrame else playerARenderedFirstFrame }
                    .filter { it }
                    .first()
                
                Log.d(TAG, "Back player is active and rendered. Fading out front player.")
                
                // Execute fade
                frontAlpha.animateTo(0f, animationSpec = tween(1500))
                
                // Cleanup front player
                frontPlayer.stop()
                frontPlayer.clearMediaItems()
                
                // Reset for next lifecycle
                frontAlpha.snapTo(1f)
                isPlayerAFront = !isPlayerAFront
                currentIndex++
                break
            }
        }
    }

    Box(modifier.fillMaxSize().background(Color.Black)) {
        // Player B Surface
        PlayerSurface(
            player = playerB,
            surfaceType = SURFACE_TYPE_SURFACE_VIEW,
            modifier = Modifier
                .fillMaxSize()
                .alpha(alphaB.value)
                .zIndex(if (isPlayerAFront) 0f else 1f)
        )
        
        // Player A Surface
        PlayerSurface(
            player = playerA,
            surfaceType = SURFACE_TYPE_SURFACE_VIEW,
            modifier = Modifier
                .fillMaxSize()
                .alpha(alphaA.value)
                .zIndex(if (isPlayerAFront) 1f else 0f)
        )
        
        // Startup Loading Overlay
        if (loadingAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .alpha(loadingAlpha),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.size(48.dp)
                )
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            Log.d(TAG, "Releasing players")
            playerA.release()
            playerB.release()
        }
    }
}
