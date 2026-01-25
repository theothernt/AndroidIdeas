package com.neilturner.fadeloop.ui.player

import androidx.annotation.OptIn
import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.upstream.DefaultAllocator
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import com.neilturner.fadeloop.data.model.Video
import com.neilturner.fadeloop.ui.common.LocationOverlay
import com.neilturner.fadeloop.ui.common.TimeRemainingOverlay
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min

private const val CROSS_FADE_DURATION_MS = 2000
private const val PRELOAD_BUFFER_MS = 5000L // Prepare next video 5s before current ends
private const val TAG = "VideoPlayer"

// Configuration Flags
private const val PRELOAD_AT_END = false // Set to false to revert to start-of-video preload
private const val USE_MINIMAL_BUFFER = true // Set to true to use minimal buffer settings

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
        Log.d(TAG, "Initializing VideoPlayer with ${videos.size} videos. useSurfaceView=$useSurfaceView, PRELOAD_AT_END=$PRELOAD_AT_END, USE_MINIMAL_BUFFER=$USE_MINIMAL_BUFFER")
    }

    // Startup Fade State
    val startupBlackAlpha = remember { Animatable(1f) }
    var hasStartedPlayback by remember { mutableStateOf(false) }

    // Time Remaining State
    var remainingSeconds by remember { mutableStateOf(0L) }

    // Location Overlay State
    var locationText by remember { mutableStateOf("") }
    var showLocation by remember { mutableStateOf(false) }

    // Helper to create LoadControl
    fun createLoadControl(): LoadControl {
        val builder = DefaultLoadControl.Builder()
        val allocator = DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE)
        builder.setAllocator(allocator)

        if (USE_MINIMAL_BUFFER) {
            // Minimal Buffer Settings
            builder.setBufferDurationsMs(
                2000,  // minBufferMs
                10000, // maxBufferMs
                500,   // bufferForPlaybackMs
                1000   // bufferForPlaybackAfterRebufferMs
            )
        } else {
            // Default ExoPlayer Settings
            builder.setBufferDurationsMs(
                50000, // minBufferMs (Default: 50000)
                50000, // maxBufferMs (Default: 50000)
                2500,  // bufferForPlaybackMs (Default: 2500)
                5000   // bufferForPlaybackAfterRebufferMs (Default: 5000)
            )
        }
        return builder.build()
    }

    // Initialize two players for cross-fading
    val player1 = remember {
        ExoPlayer.Builder(context)
            .setVideoScalingMode(C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
            .setLoadControl(createLoadControl())
            .build()
            .apply { 
                volume = 1f 
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        Log.d(TAG, "Player 1 State: $playbackState")
                    }
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        Log.d(TAG, "Player 1 IsPlaying: $isPlaying")
                        if (isPlaying && !hasStartedPlayback) {
                            hasStartedPlayback = true
                        }
                    }
                })
            }
    }
    val player2 = remember {
        ExoPlayer.Builder(context)
            .setVideoScalingMode(C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
            .setLoadControl(createLoadControl())
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
    // Track if next player has been prepared to avoid repetitive calls
    var isNextPlayerPrepared by remember { mutableStateOf(false) }

    // Coroutine scope for delayed preparation that outlives the LaunchedEffect key change
    val scope = rememberCoroutineScope()

    // Alpha animations
    val player1Alpha = remember { Animatable(1f) }
    val player2Alpha = remember { Animatable(0f) }

    // Handle startup fade out
    LaunchedEffect(hasStartedPlayback) {
        if (hasStartedPlayback) {
            Log.d(TAG, "First playback detected. Fading out black screen.")
            startupBlackAlpha.animateTo(0f, animationSpec = tween(1000, easing = LinearEasing))
        }
    }

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

        // Initial Preload Logic
        if (!PRELOAD_AT_END) {
             // OLD LOGIC: Prepare Player 2 shortly after start
             val nextIndex = (0 + 1) % videos.size
             scope.launch {
                Log.d(TAG, "Waiting 3s before preparing next player (Legacy Mode)...")
                delay(3000)
                preparePlayer(player2, videos[nextIndex].url)
                player2.playWhenReady = false 
             }
             isNextPlayerPrepared = true // Mark as handled
        } else {
            // NEW LOGIC: Wait for polling loop
            isNextPlayerPrepared = false
        }
    }

    // Cleanup
    DisposableEffect(Unit) {
        onDispose {
            player1.release()
            player2.release()
        }
    }

    // Polling loop for playback monitoring and cross-fade trigger
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
        // Next player starts as unprepared for this new cycle
        // UNLESS we are in Legacy mode, where we might want to trigger it immediately, 
        // but for simplicity, let the loop handle subsequent preloads if desired or stick to the logic below.
        // Actually, in Legacy mode, we usually prepare immediately after the switch.
        // So we reset this flag based on our target behavior.
        isNextPlayerPrepared = !PRELOAD_AT_END 

        // Update location for the new video
        launch {
            val video = videos[currentVideoIndex]
            if (video.title.isNotEmpty()) {
                locationText = video.title
                // Wait a bit for the new video to be visible before sliding up the text
                delay(1000) 
                showLocation = true
            } else {
                showLocation = false
            }
        }

        while (true) {
            delay(100) // Poll frequency
            
            val duration = activePlayer.duration
            val position = activePlayer.currentPosition
            val currentVideo = videos[currentVideoIndex]
            
            // Determine effective end time
            val videoLimit = currentVideo.durationMs ?: Long.MAX_VALUE
            
            // Logic works only if we have a valid duration
            if (duration > 0 && duration != C.TIME_UNSET) {
                val logicalEnd = if (videoLimit < duration) videoLimit else duration
                val remaining = logicalEnd - position
                
                // Update Time Remaining State
                remainingSeconds = remaining / 1000

                // 1. Check for Preload (Only if PRELOAD_AT_END is true)
                if (PRELOAD_AT_END && !isCrossFading && !isNextPlayerPrepared && remaining <= PRELOAD_BUFFER_MS) {
                    Log.d(TAG, "Preload Triggered (End Mode). Remaining: $remaining")
                    isNextPlayerPrepared = true
                    val nextIndex = (currentVideoIndex + 1) % videos.size
                    preparePlayer(nextPlayer, videos[nextIndex].url)
                    nextPlayer.playWhenReady = false
                }

                // 2. Check for Cross-fade transition
                // Trigger transition slightly before end (e.g. 200ms)
                if (!isCrossFading && remaining <= 200) {
                    Log.d(TAG, "Transition Triggered. Remaining: $remaining")
                    isCrossFading = true

                    // Fade out location when transition starts
                    showLocation = false
                    
                    // Pause the finishing video to hold the last frame
                    activePlayer.pause()
                    
                    // Start the next player
                    nextPlayer.playWhenReady = true
                    
                    // Fade in the next player (which should be on TOP)
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
                 
                 // CRITICAL FIX: Snap old player to invisible BEFORE we flip the state.
                 if (activePlayerIs1) player1Alpha.snapTo(0f) else player2Alpha.snapTo(0f)

                 // Stop and CLEAR old player to save RAM
                 activePlayer.stop()
                 activePlayer.clearMediaItems()
                 Log.d(TAG, "Cleared media items for inactive player")
                 
                 // Legacy Preload Logic: Prepare next video shortly after switch
                 if (!PRELOAD_AT_END) {
                     val nextNextIndex = (currentVideoIndex + 2) % videos.size
                     val videoUrl = videos[nextNextIndex].url
                     val playerToPrepare = activePlayer
                     scope.launch {
                         Log.d(TAG, "Waiting 4s before preparing player for next video (Legacy Mode)...")
                         delay(4000)
                         preparePlayer(playerToPrepare, videoUrl)
                         playerToPrepare.playWhenReady = false
                     }
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

        // Startup Fade-Out Black Overlay
        // High Z-Index to cover everything initially
        if (startupBlackAlpha.value > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(100f)
                    .alpha(startupBlackAlpha.value)
                    .background(Color.Black)
            )
        }

        // Time Remaining Overlay
        TimeRemainingOverlay(
            remainingSeconds = remainingSeconds,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .zIndex(200f)
        )

        // Location Overlay
        LocationOverlay(
            locationText = locationText,
            isVisible = showLocation,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .zIndex(200f)
        )
    }
}
