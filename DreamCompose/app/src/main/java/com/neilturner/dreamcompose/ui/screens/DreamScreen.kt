package com.neilturner.dreamcompose.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import com.neilturner.dreamcompose.dream.DREAM_VIDEOS
import com.neilturner.dreamcompose.ui.components.ClockOverlay

/**
 * The content of the screen saver. Shown by [com.neilturner.dreamcompose.dream.DreamService] and
 * previewed by [com.neilturner.dreamcompose.dream.DreamActivity].
 *
 * The videos are played by ExoPlayer and drawn on a SurfaceView, which is cheaper than a
 * TextureView and is what a TV wants. The SurfaceView is in its own layer below the window, so
 * the Compose content, the clock in particular, is drawn on top of the video.
 */
@UnstableApi
@Composable
fun DreamScreen(modifier: Modifier = Modifier) {
	val context = LocalContext.current
	val player =
		remember {
			ExoPlayer.Builder(context).build().apply {
				repeatMode = Player.REPEAT_MODE_ALL
				playWhenReady = true
				setMediaItems(DREAM_VIDEOS.map { MediaItem.fromUri(it.url) })
				prepare()
			}
		}
	DisposableEffect(player) {
		onDispose {
			player.release()
		}
	}

	// The clock is brought in again whenever the video changes.
	var videoIndex by remember { mutableIntStateOf(0) }
	DisposableEffect(player) {
		val listener =
			object : Player.Listener {
				override fun onMediaItemTransition(
					mediaItem: MediaItem?,
					reason: Int,
				) {
					videoIndex = player.currentMediaItemIndex
				}
			}
		player.addListener(listener)
		onDispose {
			player.removeListener(listener)
		}
	}

	Box(
		modifier =
			modifier
				.fillMaxSize()
				.background(Color.Black),
	) {
		PlayerSurface(
			player = player,
			surfaceType = SURFACE_TYPE_SURFACE_VIEW,
			modifier = Modifier.fillMaxSize(),
		)
		ClockOverlay(videoIndex = videoIndex)
	}
}
