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
import android.view.PixelCopy
import android.view.Window
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.PlayerSurface
import kotlinx.coroutines.delay

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
        onResult(null)
        return
    }
    
    val config = if (isHdr) Bitmap.Config.RGBA_F16 else Bitmap.Config.ARGB_8888
    val bitmap = Bitmap.createBitmap(rect.width(), rect.height(), config)

    try {
        PixelCopy.request(
            window,
            rect,
            bitmap,
            { result -> 
                if (result == PixelCopy.SUCCESS) {
                    onResult(bitmap)
                } else {
                    onResult(null)
                }
            },
            Handler(Looper.getMainLooper())
        )
    } catch (e: Exception) {
        e.printStackTrace()
        onResult(null)
    }
}

fun ExoPlayer.isPlayingHdr(): Boolean {
    val colorInfo = videoFormat?.colorInfo ?: return false
    return colorInfo.colorTransfer == C.COLOR_TRANSFER_ST2084
        || colorInfo.colorTransfer == C.COLOR_TRANSFER_HLG
}

private const val SKIP_AFTER_MS = 15000L  // 15 seconds

@Composable
fun CrossfadeVideoPlayer(
    videos: List<Uri>,
    modifier: Modifier = Modifier
) {
    if (videos.isEmpty()) return

    val context = LocalContext.current
    val view = LocalView.current
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
    
    val isPlayer1Active = currentIndex % 2 == 0
    val activePlayer = if (isPlayer1Active) player1 else player2
    val backgroundPlayer = if (isPlayer1Active) player2 else player1

    // Animate alpha only during FADING; snap to 1f instantly when FROZEN
    val overlayAlpha by animateFloatAsState(
        targetValue = if (state == CrossfadeState.FADING) 0f else 1f,
        animationSpec = if (state == CrossfadeState.FADING) tween(700) else snap(),
        finishedListener = {
            if (state == CrossfadeState.FADING) {
                frozenBitmap?.recycle()
                frozenBitmap = null
                // Advance to next video
                currentIndex++
                state = CrossfadeState.IDLE
            }
        },
        label = "overlay_alpha"
    )

    // Once FROZEN, wait one frame for the Image to render, then start fading
    LaunchedEffect(state) {
        if (state == CrossfadeState.FROZEN) {
            withFrameMillis { }   // let Compose render the frozen frame first
            state = CrossfadeState.FADING
        }
    }

    // Setup players when index changes
    LaunchedEffect(currentIndex, videos) {
        val activeUri = videos[currentIndex % videos.size]
        val nextUri = videos[(currentIndex + 1) % videos.size]
        
        // Only load the active player on the very first run.
        if (currentIndex == 0) {
            activePlayer.setMediaItem(MediaItem.fromUri(activeUri))
            activePlayer.prepare()
            activePlayer.play()
        }
        
        // Prepare the next video in the background
        backgroundPlayer.setMediaItem(MediaItem.fromUri(nextUri))
        backgroundPlayer.prepare()
    }

    // Poll for transition trigger
    LaunchedEffect(activePlayer, window) {
        while (state == CrossfadeState.IDLE) {
            delay(50)
            if (activePlayer.currentPosition >= SKIP_AFTER_MS) {
                state = CrossfadeState.CAPTURING
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && window != null && playerRect != null) {
                    captureVideoFrame(window, playerRect!!, activePlayer.isPlayingHdr()) { bitmap ->
                        if (bitmap != null) {
                            frozenBitmap = bitmap
                            backgroundPlayer.seekTo(0)
                            backgroundPlayer.play()
                            state = CrossfadeState.FROZEN
                        } else {
                            backgroundPlayer.seekTo(0)
                            backgroundPlayer.play()
                            state = CrossfadeState.FADING
                        }
                    }
                } else {
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
    }

    DisposableEffect(Unit) {
        onDispose {
            player1.release()
            player2.release()
        }
    }
}
