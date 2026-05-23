package com.neilturner.altfade.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import android.widget.ImageView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.PlayerSurface
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

private const val TAG = "CrossfadeVideoPlayer"

enum class CrossfadeState {
    IDLE,       // Active video playing normally
    FROZEN,     // Transition image visible at alpha=1
    FADING      // Animating alpha 1→0
}

@Composable
private fun FrozenFrameOverlay(
    bitmap: Bitmap,
    alpha: Float,
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier.alpha(alpha),
        factory = { context ->
            ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
            }
        },
        update = { imageView ->
            imageView.setImageBitmap(bitmap)
        },
        onRelease = { imageView ->
            imageView.setImageDrawable(null)
        }
    )
}

private suspend fun loadTransitionBitmap(uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
    runCatching {
        URL(uri.toString()).openStream().use { stream ->
            BitmapFactory.decodeStream(stream)
        }
    }.onFailure { error ->
        Log.e(TAG, "Failed to load transition image: $uri", error)
    }.getOrNull()
}

private const val MAX_PLAYBACK_DURATION_MS = 15000L  // Force transition at 15 seconds
private const val TRANSITION_BEFORE_END_MS = 2000L
private const val BACKGROUND_BUFFER_DELAY_MS = 4000L // 4 seconds before buffering next video
private const val FADE_DURATION_MS = 2500 // seconds fade duration

private fun Player.shouldStartTransition(): Boolean {
    // 1. Check if we've hit the hard limit of 15 seconds
    if (currentPosition >= MAX_PLAYBACK_DURATION_MS) {
        return true
    }

    // 2. Otherwise, check if we're near the natural end of the clip
    val durationMs = duration
    return if (durationMs != C.TIME_UNSET && durationMs > 0) {
        currentPosition >= durationMs - TRANSITION_BEFORE_END_MS
    } else {
        false
    }
}

@Composable
fun CrossfadeVideoPlayer(
    videos: List<Uri>,
    transitionImage: Uri,
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

    var state by remember { mutableStateOf(CrossfadeState.IDLE) }
    var transitionBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var incomingFrameRendered by remember { mutableStateOf(false) }
    
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

    LaunchedEffect(transitionImage) {
        transitionBitmap?.recycle()
        transitionBitmap = loadTransitionBitmap(transitionImage)
    }

    // Animate alpha only during FADING; snap to 1f instantly when FROZEN
    val overlayAlpha by animateFloatAsState(
        targetValue = if (state == CrossfadeState.FADING) 0f else 1f,
        animationSpec = if (state == CrossfadeState.FADING) tween(FADE_DURATION_MS) else snap(),
        finishedListener = {
            if (state == CrossfadeState.FADING) {
                Log.d(TAG, "Fade finished for index $currentIndex. Advancing.")
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
    }

    LaunchedEffect(state, incomingFrameRendered) {
        if (state == CrossfadeState.FROZEN && incomingFrameRendered) {
            withFrameMillis { }   // let Compose render the frozen frame first
            Log.d(TAG, "Incoming video rendered first frame, starting fade out")
            state = CrossfadeState.FADING
        }
    }

    LaunchedEffect(state) {
        if (state == CrossfadeState.FROZEN) {
            delay(500)
            if (state == CrossfadeState.FROZEN && !incomingFrameRendered) {
                Log.w(TAG, "Incoming first-frame callback not received; starting fade after fallback delay")
                incomingFrameRendered = true
            }
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

            override fun onRenderedFirstFrame() {
                Log.d(TAG, "Background Player rendered first frame")
                incomingFrameRendered = true
            }
        }
        backgroundPlayer.addListener(listener)
        onDispose { backgroundPlayer.removeListener(listener) }
    }

    // Poll for transition trigger
    LaunchedEffect(activePlayer) {
        while (state == CrossfadeState.IDLE) {
            delay(50)
            if (activePlayer.shouldStartTransition()) {
                Log.d(TAG, "Transition trigger reached at ${activePlayer.currentPosition}ms")
                state = CrossfadeState.FROZEN
                activePlayer.pause()
                activePlayer.stop()
                activePlayer.clearMediaItems()
                incomingFrameRendered = false
                backgroundPlayer.seekTo(0)
                backgroundPlayer.play()
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
        if (state == CrossfadeState.IDLE) {
            PlayerSurface(
                player = activePlayer,
                modifier = Modifier.fillMaxSize()
            )
        }

        // ── Top layer: transition image ────────────────────────────────────
        if (state == CrossfadeState.FROZEN || state == CrossfadeState.FADING) {
            transitionBitmap?.let { bmp ->
                FrozenFrameOverlay(
                    bitmap = bmp,
                    alpha = overlayAlpha,
                    modifier = Modifier.fillMaxSize()
                )
            }
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
            transitionBitmap?.recycle()
            player1.release()
            player2.release()
        }
    }
}
