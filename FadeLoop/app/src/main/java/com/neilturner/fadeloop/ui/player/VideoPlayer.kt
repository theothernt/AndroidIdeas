package com.neilturner.fadeloop.ui.player

import androidx.annotation.OptIn
import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.zIndex
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import com.neilturner.fadeloop.data.model.Video
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min

private const val CROSS_FADE_DURATION_MS = 2000
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
    
    LaunchedEffect(Unit) {
        Log.d(TAG, "Initializing VideoPlayer with ${videos.size} videos. useSurfaceView=$useSurfaceView")
    }

    // Initialize two players for cross-fading
    val player1 = remember {
        ExoPlayer.Builder(context)
            .setVideoScalingMode(C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
            .build()
            .apply { 
                volume = 1f 
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        Log.d(TAG, "Player 1 State: $playbackState")
                    }
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        Log.d(TAG, "Player 1 IsPlaying: $isPlaying")
                    }
                })
            }
    }
    val player2 = remember {
        ExoPlayer.Builder(context)
            .setVideoScalingMode(C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
            .build()
            .apply { 
                volume = 0f 
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        Log.d(TAG, "Player 2 State: $playbackState")
                    }
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        Log.d(TAG, "Player 2 IsPlaying: $isPlaying")
                    }
                })
            }
    }

    // State to track the active player and video index
    var activePlayerIs1 by remember { mutableStateOf(true) }
    var currentVideoIndex by remember { mutableIntStateOf(0) }
    var isCrossFading by remember { mutableStateOf(false) }

    // Coroutine scope for delayed preparation that outlives the LaunchedEffect key change
    val scope = rememberCoroutineScope()

    // Alpha animations
    val player1Alpha = remember { Animatable(1f) }
    val player2Alpha = remember { Animatable(0f) }

    // Helper to prepare a player with a URL
    fun preparePlayer(player: ExoPlayer, url: String) {
        Log.d(TAG, "Preparing player ${player.hashCode()} with $url")
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        player.repeatMode = Player.REPEAT_MODE_OFF // We handle looping manually
    }

    // Initial setup
    LaunchedEffect(Unit) {
        Log.d(TAG, "Initial Setup Launched")
        // Prepare Player 1 with first video (Immediate)
        preparePlayer(player1, videos[0].url)
        player1.playWhenReady = true

        // Prepare Player 2 with second video (Delayed by 3s)
        val nextIndex = (0 + 1) % videos.size
        scope.launch {
            Log.d(TAG, "Waiting 3s before preparing next player...")
            delay(3000)
            preparePlayer(player2, videos[nextIndex].url)
            player2.playWhenReady = false 
        }
    }

    // Cleanup
    DisposableEffect(Unit) {
        onDispose {
            player1.release()
            player2.release()
        }
    }

    // Polling loop for cross-fade trigger
    LaunchedEffect(activePlayerIs1, currentVideoIndex, useSurfaceView) {
        Log.d(TAG, "State Changed: activePlayerIs1=$activePlayerIs1, index=$currentVideoIndex")
        
        val activePlayer = if (activePlayerIs1) player1 else player2
        val nextPlayer = if (activePlayerIs1) player2 else player1
        
        // Ensure accurate z-ordering and visibility
        if (activePlayerIs1) {
             player1Alpha.snapTo(1f)
             player2Alpha.snapTo(0f)
             player1.volume = 1f
             player2.volume = 1f
        } else {
             player1Alpha.snapTo(0f)
             player2Alpha.snapTo(1f)
             player1.volume = 1f
             player2.volume = 1f
        }
        
        isCrossFading = false

        while (true) {
            delay(100) // Poll frequency
            
            val duration = activePlayer.duration
            val position = activePlayer.currentPosition
            val currentVideo = videos[currentVideoIndex]
            
            // Determine effective end time
            val videoLimit = currentVideo.durationMs ?: Long.MAX_VALUE
            
            // Check if we should start transition
            if (!isCrossFading && duration > 0 && duration != C.TIME_UNSET) {
                val logicalEnd = if (videoLimit < duration) videoLimit else duration
                val remaining = logicalEnd - position
                
                // Trigger transition slightly before end (e.g. 200ms) to ensure we catch it before actual completion
                if (remaining <= 200) {
                    Log.d(TAG, "Transition Triggered. Remaining: $remaining")
                    isCrossFading = true
                    
                    // 1. Pause the finishing video to hold the last frame
                    activePlayer.pause()
                    
                    // 2. Start the next player
                    nextPlayer.playWhenReady = true
                    
                    // 3. Fade in the next player (which should be on TOP)
                    launch {
                        val fadeDuration = if (useSurfaceView) 0 else CROSS_FADE_DURATION_MS
                        Log.d(TAG, "Animating fade: duration=$fadeDuration")
                        if (activePlayerIs1) {
                            // Current is P1 (bottom). Next is P2 (top). 
                            // Fade in P2. P1 stays visible behind.
                            player2Alpha.animateTo(1f, animationSpec = tween(fadeDuration, easing = LinearEasing))
                        } else {
                            // Current is P2 (bottom). Next is P1 (top).
                            player1Alpha.animateTo(1f, animationSpec = tween(fadeDuration, easing = LinearEasing))
                        }
                    }
                }
            }
            
            if (isCrossFading) {
                 // Wait for fade to complete
                 val waitTime = if (useSurfaceView) 0L else CROSS_FADE_DURATION_MS.toLong()
                 delay(waitTime)
                 
                 Log.d(TAG, "Transition Complete. Switching State.")
                 
                 // NOW switch state.
                 // activePlayer (old) is now hidden behind nextPlayer (new).
                 
                 // CRITICAL FIX: Snap old player to invisible BEFORE we flip the state.
                 if (activePlayerIs1) player1Alpha.snapTo(0f) else player2Alpha.snapTo(0f)

                 // Stop old player immediately
                 activePlayer.stop()
                 // activePlayer.clearMediaItems()
                 
                 val nextNextIndex = (currentVideoIndex + 2) % videos.size
                 val videoUrl = videos[nextNextIndex].url
                 
                 // Prepare for *next next* video AFTER 3s delay
                 scope.launch {
                     Log.d(TAG, "Waiting 4s before preparing player for next video logic...")
                     delay(4000)
                     preparePlayer(activePlayer, videoUrl)
                     activePlayer.playWhenReady = false
                 }
                 
                 // Update global state to restart this effect
                 currentVideoIndex = (currentVideoIndex + 1) % videos.size
                 activePlayerIs1 = !activePlayerIs1
                 
                 break 
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // We use zIndex to control layering.
        // TextureView (Fade): The one that is "fading in" (nextPlayer) must be on TOP of the "finishing" (activePlayer).
        // SurfaceView (Cut): The one that is "playing" (activePlayer) must be on TOP to be visible (cannot fade efficiently).
        
        if (useSurfaceView) {
            // SurfaceView Mode: Render ONLY the active player to prevent occlusion.
            // Z-ordering multiple SurfaceViews is unreliable.
            if (activePlayerIs1) {
                 PlayerSurface(
                    player = player1,
                    surfaceType = SURFACE_TYPE_SURFACE_VIEW,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                 PlayerSurface(
                    player = player2,
                    surfaceType = SURFACE_TYPE_SURFACE_VIEW,
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else {
            // TextureView Mode: Render BOTH for Cross-fade.
            val p1ZIndex = if (activePlayerIs1) 0f else 1f
            val p2ZIndex = if (activePlayerIs1) 1f else 0f
            
            // Render Player 1
            PlayerSurface(
                player = player1,
                surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(p1ZIndex)
                    .alpha(player1Alpha.value)
            )

            // Render Player 2
            PlayerSurface(
                player = player2,
                surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(p2ZIndex)
                    .alpha(player2Alpha.value)
            )
        }
    }
}
