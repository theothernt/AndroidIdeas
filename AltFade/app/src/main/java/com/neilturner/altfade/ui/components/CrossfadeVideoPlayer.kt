package com.neilturner.altfade.ui.components

import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
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
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import kotlinx.coroutines.delay

private const val TAG = "CrossfadeVideoPlayer"

/**
 * Extension to check if the current video track is HDR.
 */
@OptIn(UnstableApi::class)
fun ExoPlayer.isPlayingHdr(): Boolean {
    val colorInfo = videoFormat?.colorInfo ?: return false
    return colorInfo.colorTransfer == C.COLOR_TRANSFER_ST2084  // PQ (HDR10)
        || colorInfo.colorTransfer == C.COLOR_TRANSFER_HLG     // HLG
}

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

    val player1 = remember { 
        ExoPlayer.Builder(context).build().apply { repeatMode = Player.REPEAT_MODE_OFF } 
    }
    val player2 = remember { 
        ExoPlayer.Builder(context).build().apply { repeatMode = Player.REPEAT_MODE_OFF } 
    }

    var currentIndex by remember { mutableIntStateOf(0) }
    var isTransitioning by remember { mutableStateOf(false) }
    
    // Track required surface type for each player
    var player1SurfaceType by remember { mutableIntStateOf(SURFACE_TYPE_SURFACE_VIEW) }
    var player2SurfaceType by remember { mutableIntStateOf(SURFACE_TYPE_SURFACE_VIEW) }

    // Startup loading state
    var isAppLoading by remember { mutableStateOf(true) }
    val loadingAlpha by animateFloatAsState(
        targetValue = if (isAppLoading) 1f else 0f,
        animationSpec = tween(1000),
        label = "loading_alpha"
    )

    val isPlayer1Active = currentIndex % 2 == 0
    val activePlayer = if (isPlayer1Active) player1 else player2
    val backgroundPlayer = if (isPlayer1Active) player2 else player1
    val activeSurfaceType = if (isPlayer1Active) player1SurfaceType else player2SurfaceType

    // Monitor Active Player for initial loading
    DisposableEffect(activePlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (isAppLoading && playbackState == Player.STATE_READY) {
                    Log.d(TAG, "Initial video ready, fading out loading screen")
                    isAppLoading = false
                }
            }
        }
        activePlayer.addListener(listener)
        onDispose { activePlayer.removeListener(listener) }
    }

    // Setup players and handle background pre-buffering
    LaunchedEffect(currentIndex, videos) {
        val activeUri = videos[currentIndex % videos.size]
        val nextUri = videos[(currentIndex + 1) % videos.size]
        
        Log.d(TAG, "Cycle start - Index: $currentIndex, Active: $activeUri")
        
        if (currentIndex == 0) {
            activePlayer.setMediaItem(MediaItem.fromUri(activeUri))
            activePlayer.prepare()
            activePlayer.play()
        }
        
        // Wait then prepare the background player
        delay(BACKGROUND_BUFFER_DELAY_MS)
        Log.d(TAG, "Preparing background player for next video: $nextUri")
        backgroundPlayer.setMediaItem(MediaItem.fromUri(nextUri))
        backgroundPlayer.prepare()
    }

    // Monitor background player for HDR detection
    DisposableEffect(backgroundPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    val isHdr = backgroundPlayer.isPlayingHdr()
                    val newSurfaceType = if (isHdr) SURFACE_TYPE_SURFACE_VIEW else SURFACE_TYPE_TEXTURE_VIEW
                    Log.d(TAG, "Background Player READY. HDR=$isHdr. Surface: ${if (isHdr) "SurfaceView" else "TextureView"}")
                    
                    if (isPlayer1Active) {
                        player2SurfaceType = newSurfaceType
                    } else {
                        player1SurfaceType = newSurfaceType
                    }
                }
            }
        }
        backgroundPlayer.addListener(listener)
        onDispose { backgroundPlayer.removeListener(listener) }
    }

    // Simple transition trigger
    LaunchedEffect(activePlayer) {
        while (true) {
            delay(100)
            val duration = activePlayer.duration
            val position = activePlayer.currentPosition
            
            val shouldTransition = (duration != C.TIME_UNSET && duration > 0 && position >= duration - 100) || 
                                   (position >= MAX_PLAYBACK_DURATION_MS)

            if (shouldTransition && !isTransitioning) {
                isTransitioning = true
                Log.d(TAG, "Transitioning to next video at position ${position}ms")
                
                // Handover
                backgroundPlayer.play()
                activePlayer.pause()
                activePlayer.stop()
                activePlayer.clearMediaItems()
                
                currentIndex++
                isTransitioning = false
            }
        }
    }

    Box(modifier.fillMaxSize().background(Color.Black)) {
        // We only show the active player's surface to ensure the clean jump.
        // Because the background player is prepared and "ready", 
        // the switch in the currentIndex will swap which PlayerSurface is composed.
        
        PlayerSurface(
            player = activePlayer,
            surfaceType = activeSurfaceType,
            modifier = Modifier.fillMaxSize()
        )
        
        // Startup Loading Layer
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
            player1.release()
            player2.release()
        }
    }
}
