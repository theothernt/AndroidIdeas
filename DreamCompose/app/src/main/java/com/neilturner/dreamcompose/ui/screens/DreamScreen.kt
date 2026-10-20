package com.neilturner.dreamcompose.ui.screens

import android.util.Log
import androidx.compose.animation.core.Animatable
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import com.neilturner.dreamcompose.dream.DREAM_VIDEOS
import com.neilturner.dreamcompose.ui.components.ClockOverlay
import com.neilturner.dreamcompose.ui.components.CountdownOverlay
import com.neilturner.dreamcompose.ui.components.SLIDE_DURATION_MS
import com.neilturner.dreamcompose.ui.components.ScreenCorner
import com.neilturner.dreamcompose.ui.components.opposite
import kotlinx.coroutines.delay

/**
 * The tag of the logs about the playlist and the length of the clips, so that they can be
 * followed on their own with `adb logcat -s player`.
 */
private const val TAG = "player"

/**
 * How long a clip is played before the next one starts.
 */
private const val CLIP_LENGTH_MS = 15_000L

/**
 * A duration in milliseconds as a number of seconds with one decimal, for the log.
 */
private fun Long.asSeconds() = "${this / 1_000}.${this % 1_000 / 100}s"

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
				setMediaItems(
					DREAM_VIDEOS.map {
						MediaItem
							.fromUri(it.url)
							.buildUpon()
							.setClippingConfiguration(
								MediaItem.ClippingConfiguration
									.Builder()
									.setEndPositionMs(CLIP_LENGTH_MS)
									.build(),
							).build()
					},
				)
				prepare()
				Log.i(TAG, "Clip 0 (${DREAM_VIDEOS.first().title}) started")
			}
		}
	DisposableEffect(player) {
		onDispose {
			player.release()
		}
	}

	// The clock is brought in again whenever the video changes.
	var videoIndex by remember { mutableIntStateOf(0) }

	// The corner the clock is in. It changes with every video, and the countdown is in the
	// corner opposite to it.
	var clockCorner by remember { mutableStateOf(ScreenCorner.entries.random()) }

	// The video has started when its first frame is on the screen.
	var videoStarted by remember { mutableStateOf(false) }

	// The seconds left of the current clip.
	var remainingSeconds by remember { mutableIntStateOf(0) }

	// The length of the current clip, learned from the timeline of the player.
	var clipDuration by remember { mutableStateOf(C.TIME_UNSET) }

	// The clock and the countdown slide out again just before the clip ends.
	var exiting by remember { mutableStateOf(false) }

	LaunchedEffect(player) {
		while (true) {
			val length = clipDuration
			val position = player.currentPosition
			if (length != C.TIME_UNSET) {
				val remaining = (length - position).coerceAtLeast(0)
				val seconds = Math.ceil(remaining / 1000.0).toInt()
				if (seconds != remainingSeconds) {
					remainingSeconds = seconds
				}
				// The slide out takes as long as the slide in, so it starts that long
				// before the clip ends.
				if (!exiting && position >= length - SLIDE_DURATION_MS) {
					exiting = true
				}
			}
			delay(100)
		}
	}

	// The duration of a clip is only known once the source of the clip has been loaded. It is
	// logged the first time it becomes available, for every clip.
	var durationLoggedFor by remember { mutableIntStateOf(-1) }

	fun logDuration(player: Player) {
		val index = player.currentMediaItemIndex
		if (index == durationLoggedFor) {
			return
		}
		val duration = player.duration
		if (duration == C.TIME_UNSET) {
			return
		}
		durationLoggedFor = index
		clipDuration = duration
		val title = DREAM_VIDEOS.getOrNull(index)?.title ?: "?"
		val end = duration - 3_000
		Log.i(
			TAG,
			"Clip $index ($title) duration ${duration}ms (${duration.asSeconds()}), " +
				"three seconds before the end is ${end}ms (${end.asSeconds()})",
		)
	}

	DisposableEffect(player) {
		val listener =
			object : Player.Listener {
				override fun onMediaItemTransition(
					mediaItem: MediaItem?,
					reason: Int,
				) {
					videoIndex = player.currentMediaItemIndex
					clockCorner = ScreenCorner.entries.random()
					exiting = false
					durationLoggedFor = -1
					Log.i(TAG, "Clip ${player.currentMediaItemIndex} started")
					// The duration of the next clip can already be known, because the player
					// loads the metadata of the following clips ahead of time.
					logDuration(player)
				}

				override fun onTimelineChanged(
					timeline: Timeline,
					reason: Int,
				) {
					Log.i(TAG, "Timeline changed, reason $reason")
					logDuration(player)
				}

				override fun onPlaybackStateChanged(state: Int) {
					if (state == Player.STATE_READY) {
						Log.i(TAG, "Playback state is ready")
						logDuration(player)
					}
				}

				override fun onRenderedFirstFrame() {
					Log.i(TAG, "The first frame of the video is on the screen")
					videoStarted = true
				}
			}
		player.addListener(listener)
		onDispose {
			player.removeListener(listener)
		}
	}

	// The screen is black until the first frame of the video is on the screen. The black fades
	// out over 300ms, and the clock slides in after that.
	val blackScreen = remember { Animatable(1f) }
	var showClock by remember { mutableStateOf(false) }
	LaunchedEffect(videoStarted) {
		if (videoStarted) {
			blackScreen.animateTo(0f, tween(durationMillis = 300))
			delay(300)
			showClock = true
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
		Box(
			modifier =
				Modifier
					.fillMaxSize()
					.background(Color.Black.copy(alpha = blackScreen.value)),
		)
		ClockOverlay(
			videoIndex = videoIndex,
			corner = clockCorner,
			visible = showClock,
			exiting = exiting,
		)
		CountdownOverlay(
			videoIndex = videoIndex,
			corner = clockCorner.opposite,
			seconds = remainingSeconds,
			visible = showClock,
			exiting = exiting,
		)
	}
}
