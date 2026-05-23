package com.neilturner.altfade.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.PixelCopy
import android.view.Window
import androidx.annotation.OptIn
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.PlayerSurface
import kotlinx.coroutines.delay
import androidx.core.graphics.createBitmap
import androidx.media3.common.util.UnstableApi

private const val TAG = "CrossfadeVideoPlayer"

enum class CrossfadeState {
    IDLE,       // Active video playing normally
    CAPTURING,  // Triggering PixelCopy
    FROZEN,     // Bitmap ready, showing at alpha=1
    FADING,     // Animating alpha 1→0
    DONE        // Transition complete
}

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@RequiresApi(Build.VERSION_CODES.O)
fun captureVideoFrame(
    window: Window,
    rect: Rect,
    isHdr: Boolean,
    onResult: (Bitmap?) -> Unit
) {
    if (rect.width() <= 0 || rect.height() <= 0) {
        Log.e(TAG, "captureVideoFrame: Invalid rect dimensions: ${rect.width()}x${rect.height()}")
        onResult(null)
        return
    }
    
    Log.d(TAG, "captureVideoFrame: Requesting PixelCopy for rect=$rect, isHdr=$isHdr")
    val config = if (isHdr) Bitmap.Config.RGBA_F16 else Bitmap.Config.ARGB_8888
    val bitmap = createBitmap(rect.width(), rect.height(), config)

    try {
        PixelCopy.request(
            window,
            rect,
            bitmap,
            { result -> 
                if (result == PixelCopy.SUCCESS) {
                    Log.d(TAG, "captureVideoFrame: PixelCopy SUCCESS")
                    onResult(bitmap)
                } else {
                    Log.e(TAG, "captureVideoFrame: PixelCopy FAILED with result=$result")
                    onResult(null)
                }
            },
            Handler(Looper.getMainLooper())
        )
    } catch (e: Exception) {
        Log.e(TAG, "captureVideoFrame: Exception during PixelCopy", e)
        onResult(null)
    }
}

@OptIn(UnstableApi::class)
fun ExoPlayer.isPlayingHdr(): Boolean {
    val colorInfo = videoFormat?.colorInfo ?: return false
    return colorInfo.colorTransfer == C.COLOR_TRANSFER_ST2084
        || colorInfo.colorTransfer == C.COLOR_TRANSFER_HLG
}

private const val SKIP_AFTER_MS = 15000L  // 15 seconds
private const val BACKGROUND_BUFFER_DELAY_MS = 4000L // 4 seconds before buffering next video
private const val FADE_DURATION_MS = 1500 // 1.5 seconds fade duration

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
    val window = remember(context) { context.findActivity()?.window }

    val player1 = remember { 
        ExoPlayer.Builder(context).build().apply { repeatMode = Player.REPEAT_MODE_OFF } 
    }
    val player2 = remember { 
        ExoPlayer.Builder(context).build().apply { repeatMode = Player.REPEAT_MODE_OFF } 
    }

    var state by remember { mutableStateOf(CrossfadeState.IDLE) }
    var frozenBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var playerRect by remember { mutableStateOf<Rect?>(null) }
    
    var currentIndex by remember { mutableIntStateOf(0) }
    
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

    // Animate alpha only during FADING; snap to 1f instantly when FROZEN
    val overlayAlpha by animateFloatAsState(
        targetValue = if (state == CrossfadeState.FADING) 0f else 1f,
        animationSpec = if (state == CrossfadeState.FADING) tween(FADE_DURATION_MS) else snap(),
        finishedListener = {
            if (state == CrossfadeState.FADING) {
                Log.d(TAG, "Fade finished for index $currentIndex. Recycling bitmap and advancing.")
                frozenBitmap?.recycle()
                frozenBitmap = null
                // Advance to next video
                currentIndex++
                state = CrossfadeState.IDLE
            }
        },
        label = "overlay_alpha"
    )

    // Log state changes and handle FROZEN transition
    LaunchedEffect(state) {
        Log.d(TAG, "State changed: $state (Index: $currentIndex)")
        if (state == CrossfadeState.FROZEN) {
            withFrameMillis { }   // let Compose render the frozen frame first
            Log.d(TAG, "Frozen frame ready on screen, starting fade out")
            state = CrossfadeState.FADING
        }
    }

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

    // Setup players when index changes
    LaunchedEffect(currentIndex, videos) {
        val activeUri = videos[currentIndex % videos.size]
        val nextUri = videos[(currentIndex + 1) % videos.size]
        
        Log.d(TAG, "New cycle - Index: $currentIndex, Active: $activeUri, Next: $nextUri")
        
        // Only load the active player on the very first run.
        if (currentIndex == 0) {
            Log.d(TAG, "Initial setup: Starting active player")
            activePlayer.setMediaItem(MediaItem.fromUri(activeUri))
            activePlayer.prepare()
            activePlayer.play()
        }
        
        // Prepare the next video in the background after a delay
        Log.d(TAG, "Waiting ${BACKGROUND_BUFFER_DELAY_MS}ms before preparing background player")
        delay(BACKGROUND_BUFFER_DELAY_MS)
        Log.d(TAG, "Preparing background player for next video")
        backgroundPlayer.setMediaItem(MediaItem.fromUri(nextUri))
        backgroundPlayer.prepare()
    }

    // Monitor background player buffering
    DisposableEffect(backgroundPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                val stateName = when (playbackState) {
                    Player.STATE_BUFFERING -> "BUFFERING"
                    Player.STATE_READY -> "READY"
                    Player.STATE_ENDED -> "ENDED"
                    Player.STATE_IDLE -> "IDLE"
                    else -> "UNKNOWN"
                }
                Log.d(TAG, "Background Player state: $stateName")
            }
        }
        backgroundPlayer.addListener(listener)
        onDispose { backgroundPlayer.removeListener(listener) }
    }

    // Poll for transition trigger
    LaunchedEffect(activePlayer, window) {
        while (state == CrossfadeState.IDLE) {
            delay(50)
            if (activePlayer.currentPosition >= SKIP_AFTER_MS) {
                Log.d(TAG, "Transition trigger: SKIP_AFTER_MS reached (${SKIP_AFTER_MS}ms)")
                state = CrossfadeState.CAPTURING
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && window != null && playerRect != null) {
                    captureVideoFrame(window, playerRect!!, activePlayer.isPlayingHdr()) { bitmap ->
                        if (bitmap != null) {
                            Log.d(TAG, "Frame captured successfully. Starting background player.")
                            frozenBitmap = bitmap
                            backgroundPlayer.seekTo(0)
                            backgroundPlayer.play()
                            state = CrossfadeState.FROZEN
                        } else {
                            Log.e(TAG, "Frame capture failed. Transitioning without frozen frame.")
                            backgroundPlayer.seekTo(0)
                            backgroundPlayer.play()
                            state = CrossfadeState.FADING
                        }
                    }
                } else {
                    Log.w(TAG, "Conditions for capture not met (SDK < O, window null, or rect null). Skipping capture.")
                    backgroundPlayer.seekTo(0)
                    backgroundPlayer.play()
                    state = CrossfadeState.FADING // Skip frozen frame if we can't capture
                }
            }
        }
    }

    Box(modifier.fillMaxSize().background(Color.Black)) {

        // ── Bottom layer: Background Player (next video) ───────────────────
        PlayerSurface(
            player = backgroundPlayer,
            modifier = Modifier.fillMaxSize()
        )

        // ── Middle layer: Active Player (current video) ────────────────────
        if (state == CrossfadeState.IDLE || state == CrossfadeState.CAPTURING) {
            PlayerSurface(
                player = activePlayer,
                modifier = Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { coordinates ->
                        val rect = coordinates.boundsInWindow()
                        playerRect = Rect(
                            rect.left.toInt(),
                            rect.top.toInt(),
                            rect.right.toInt(),
                            rect.bottom.toInt()
                        )
                    }
            )
        }

        // ── Top layer: Frozen frame (Compose layer, above both surfaces) ───
        frozenBitmap?.let { bmp ->
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(overlayAlpha)
            )
        }
        
        // ── Startup Loading Layer ──────────────────────────────────────────
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
