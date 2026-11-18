package com.neilturner.fauxfade.core.player.ui

import android.util.Log
import android.view.SurfaceView
import android.view.View
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.neilturner.fauxfade.TAG
import com.neilturner.fauxfade.core.media.Quality
import com.neilturner.fauxfade.core.player.StillPhase
import kotlin.time.TimeSource

/**
 * A [SurfaceView] for one player, either shown or hidden.
 *
 * Showing and hiding uses visibility rather than ordering: a SurfaceView punches a hole in the
 * window for its bounds and setZ does not reliably reorder two of them, so a handoff that relied
 * on ordering left the first surface on top. Making one INVISIBLE destroys its surface, but
 * Media3 re-attaches through its own SurfaceHolder callback, so nothing has to be set by hand.
 */
@OptIn(UnstableApi::class)
@Composable
fun SlotPlayerSurface(
	player: ExoPlayer,
	visible: Boolean,
	modifier: Modifier = Modifier,
) {
	val attached = remember { mutableStateOf<SurfaceView?>(null) }
	AndroidView(
		modifier = modifier,
		factory = { context -> SurfaceView(context) },
		update = { view ->
			if (attached.value !== view) {
				player.setVideoSurfaceView(view)
				attached.value = view
			}
			view.visibility = if (visible) View.VISIBLE else View.INVISIBLE
		},
	)
}

/**
 * A captured still held over the screen during a handoff, then faded out.
 *
 * Sealing snaps to opaque because the still is a copy of the frame the outgoing video is already
 * paused on; dissolving ramps up because there the picture underneath is still playing. The fade
 * uses graphicsLayer so a frame of the animation invalidates only drawing.
 */
@Composable
fun StillOverlay(
	stillFrame: android.graphics.Bitmap?,
	stillPhase: StillPhase,
	dissolveMs: Int,
	fadeMs: Int = STILL_FADE_MS,
	modifier: Modifier = Modifier,
) {
	val fade = remember { Animatable(0f) }
	LaunchedEffect(stillPhase, stillFrame) {
		when (stillPhase) {
			StillPhase.HIDDEN -> {
				fade.snapTo(0f)
			}

			StillPhase.DISSOLVING -> {
				val started = TimeSource.Monotonic.markNow()
				fade.snapTo(0f)
				fade.animateTo(1f, tween(dissolveMs))
				Log.i(TAG, "timing: dissolveMs=${started.elapsedNow().inWholeMilliseconds}")
			}

			StillPhase.SEALED -> {
				fade.snapTo(1f)
			}

			StillPhase.FADING -> {
				val started = TimeSource.Monotonic.markNow()
				fade.snapTo(1f)
				fade.animateTo(0f, tween(fadeMs))
				Log.i(TAG, "timing: fadeMs=${started.elapsedNow().inWholeMilliseconds}")
			}
		}
	}
	val fadedOut = remember { derivedStateOf { fade.value <= FADE_DONE_AT } }
	val bitmap = stillFrame
	if (bitmap == null || fadedOut.value) return
	Image(
		bitmap = remember(bitmap) { bitmap.asImageBitmap() },
		contentDescription = null,
		modifier = modifier.graphicsLayer { alpha = fade.value },
		contentScale = ContentScale.Crop,
	)
}

/** Black cover with a spinner until the opening clip has rendered a frame. */
@Composable
fun StartupLoader(
	loading: Boolean,
	modifier: Modifier = Modifier,
) {
	AnimatedVisibility(visible = loading, exit = fadeOut(tween(LOADER_FADE_MS)), modifier = modifier) {
		Box(
			modifier = Modifier.fillMaxSize().background(Color.Black),
			contentAlignment = Alignment.Center,
		) {
			CircularProgressIndicator(
				color = MaterialTheme.colorScheme.onSurface,
				strokeWidth = LOADER_STROKE_DP.dp,
				modifier = Modifier.size(LOADER_SIZE_DP.dp),
			)
		}
	}
}

/** The small translucent chip the readouts sit in. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ReadoutChip(
	text: String,
	modifier: Modifier = Modifier,
) {
	Box(
		modifier =
			modifier
				.padding(12.dp)
				.background(MaterialTheme.colorScheme.scrim.copy(alpha = CHIP_ALPHA), RoundedCornerShape(6.dp))
				.padding(horizontal = 9.dp, vertical = 4.dp),
	) {
		Text(
			text = text,
			style = MaterialTheme.typography.titleMedium,
			color = MaterialTheme.colorScheme.onSurface,
		)
	}
}

/** The quality actually playing, bottom right. */
@Composable
fun PlayingQualityLabel(
	quality: Quality,
	modifier: Modifier = Modifier,
) = ReadoutChip(text = quality.label, modifier = modifier)

/** Seconds left in the segment, top right. */
@Composable
fun TransitionCountdown(
	seconds: Int,
	modifier: Modifier = Modifier,
) = ReadoutChip(text = seconds.toString(), modifier = modifier)

/** Process memory in the top left. Debug builds only. */
@Composable
fun MemoryReadout(
	memoryMb: Long,
	modifier: Modifier = Modifier,
) = ReadoutChip(text = "$memoryMb MB", modifier = modifier)

/** How long a captured still takes to fade out. The loop's delay and this tween must agree. */
const val STILL_FADE_MS = 1_500

/** How long the startup spinner takes to fade out once there is a frame to show. */
private const val LOADER_FADE_MS = 400

private const val LOADER_SIZE_DP = 44
private const val LOADER_STROKE_DP = 3
private const val CHIP_ALPHA = 0.45f
private const val FADE_DONE_AT = 0.001f
