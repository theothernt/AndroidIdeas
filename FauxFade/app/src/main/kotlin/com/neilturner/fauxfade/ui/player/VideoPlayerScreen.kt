package com.neilturner.fauxfade.ui.player

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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.neilturner.fauxfade.BuildConfig
import com.neilturner.fauxfade.TAG
import kotlin.math.ceil
import kotlin.time.TimeSource

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
	modifier: Modifier = Modifier,
	viewModel: PlayerViewModel = viewModel(),
) {
	val foreground by viewModel.foreground.collectAsStateWithLifecycle()
	val swapping by viewModel.swapping.collectAsStateWithLifecycle()
	val loading by viewModel.loading.collectAsStateWithLifecycle()

	DisposableEffect(Unit) {
		viewModel.start()
		onDispose { viewModel.stop() }
	}

	Box(
		modifier =
			modifier
				.fillMaxSize()
				.background(Color.Black),
	) {
		PlayerSlot.entries.forEach { slot ->
			SlotPlayerSurface(
				viewModel = viewModel,
				slot = slot,
				// During a handoff both surfaces have to be up: the incoming player needs a
				// surface attached to render into before it can be brought to the front. The
				// sealed still is drawn in the window and covers both while that happens.
				visible = swapping || foreground == slot,
				modifier = Modifier.fillMaxSize(),
			)
		}

		StillOverlay(
			viewModel = viewModel,
			modifier = Modifier.fillMaxSize(),
		)

		// Debug-only. Nothing is published into memoryMb in a release build, so there is no point
		// composing a readout that would sit at zero.
		if (BuildConfig.DEBUG) {
			MemoryReadout(
				viewModel = viewModel,
				modifier = Modifier.align(Alignment.TopStart),
			)
		}

		TransitionCountdown(
			viewModel = viewModel,
			modifier = Modifier.align(Alignment.TopEnd),
		)

		// Last, so it covers both surfaces and the readouts. Like the still it is drawn in the
		// window, which composites above a SurfaceView.
		StartupLoader(
			loading = loading,
			modifier = Modifier.fillMaxSize(),
		)
	}
}

/**
 * Black cover with a spinner, up until the opening clip has a frame on screen.
 *
 * There is nothing behind it to hide - the surfaces are empty and the window is already black -
 * so this is not covering a flash so much as making the wait deliberate rather than looking
 * like an app that failed to start. The spinner is oversized for a ten-foot screen.
 *
 * The spinner comes from the phone material library; [androidx.tv.material] has no equivalent.
 */
@Composable
private fun StartupLoader(
	loading: Boolean,
	modifier: Modifier = Modifier,
) {
	AnimatedVisibility(
		visible = loading,
		// Faded rather than cut, so the video arrives from black instead of popping in.
		exit = fadeOut(tween(LOADER_FADE_MS)),
		modifier = modifier,
	) {
		Box(
			modifier = Modifier.fillMaxSize().background(Color.Black),
			contentAlignment = Alignment.Center,
		) {
			CircularProgressIndicator(
				color = Color.White,
				strokeWidth = LOADER_STROKE_DP.dp,
				modifier = Modifier.size(LOADER_SIZE_DP.dp),
			)
		}
	}
}

/**
 * Process memory in the top left. The still is a full-resolution bitmap allocated on every
 * handoff, so this is how you would notice them accumulating rather than being collected.
 *
 * Only ever composed in a debug build - see the call site in [VideoPlayerScreen].
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun MemoryReadout(
	viewModel: PlayerViewModel,
	modifier: Modifier = Modifier,
) {
	val memoryMb by viewModel.memoryMb.collectAsStateWithLifecycle()

	Box(
		modifier =
			modifier
				.padding(12.dp)
				.background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
				.padding(horizontal = 9.dp, vertical = 4.dp),
	) {
		Text(
			text = "$memoryMb MB",
			style = MaterialTheme.typography.titleMedium,
			color = Color.White,
		)
	}
}

/**
 * A frame captured from the outgoing video, held over the screen while the two players swap
 * underneath it, then faded out to reveal the incoming one.
 *
 * It is a plain Compose image rather than another surface, so its alpha is a normal draw-time
 * value - no compositor or SurfaceControl involvement - and it costs no decoder.
 */
@Composable
private fun StillOverlay(
	viewModel: PlayerViewModel,
	modifier: Modifier = Modifier,
) {
	val stillFrame by viewModel.stillFrame.collectAsStateWithLifecycle()
	val stillPhase by viewModel.stillPhase.collectAsStateWithLifecycle()
	val alpha = remember { Animatable(0f) }

	// Keyed on the phase rather than the bitmap: the outgoing video alternates, so the same
	// still can come round again and has to be sealed and faded from scratch each time.
	LaunchedEffect(stillPhase, stillFrame) {
		when (stillPhase) {
			StillPhase.HIDDEN -> {
				alpha.snapTo(0f)
			}

			// Snapped, not ramped. The still goes up over the outgoing video still paused on the
			// same frame, so it has to be fully opaque the instant it appears - there is nothing
			// behind it to dissolve against until the surfaces swap.
			StillPhase.SEALED -> {
				alpha.snapTo(1f)
			}

			StillPhase.FADING -> {
				val started = TimeSource.Monotonic.markNow()
				alpha.snapTo(1f)
				alpha.animateTo(0f, tween(STILL_FADE_MS))
				// Logged next to the view model's timing line, which covers everything up to
				// the point this fade starts.
				Log.i(TAG, "timing: fadeMs=${started.elapsedNow().inWholeMilliseconds}")
			}
		}
	}

	val bitmap = stillFrame
	if (bitmap == null || alpha.value <= 0.001f) return

	Image(
		bitmap = bitmap.asImageBitmap(),
		contentDescription = null,
		modifier = modifier.alpha(alpha.value),
		contentScale = ContentScale.Crop,
	)
}

/**
 * A [SurfaceView] for one player, either shown or hidden.
 *
 * Showing and hiding is done with [SurfaceView.setVisibility] rather than by ordering the two
 * surfaces. A SurfaceView punches a hole in the window for its own bounds, and [SurfaceView.setZ]
 * does not reliably reorder two of them - the first one composited stays on top, so the handoff
 * never became visible. Hiding one outright avoids relying on surface ordering at all.
 *
 * Making a SurfaceView INVISIBLE destroys its surface, but the player keeps the view and
 * Media3 re-attaches to the new surface through its own [android.view.SurfaceHolder.Callback],
 * so the surface does not have to be set again by hand.
 *
 * This is the one place that uses `AndroidView`. `androidx.media3.ui.compose.PlayerSurface` is
 * the Compose-native surface, but it renders into a TextureView, which forces video through the
 * app's own view pipeline and colour space and so rules out HDR passthrough.
 */
@OptIn(UnstableApi::class)
@Composable
private fun SlotPlayerSurface(
	viewModel: PlayerViewModel,
	slot: PlayerSlot,
	visible: Boolean,
	modifier: Modifier = Modifier,
) {
	val player = viewModel.playerFor(slot)

	// The view this slot's surface was last handed to the player. AndroidView builds it once
	// and reuses it, so this is really just a "has the surface been set yet" flag - and it stops
	// the update block re-setting the same view on every recomposition, which churns the surface
	// generation and makes the decoder discard buffered frames.
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

/** How long the captured still takes to fade out, revealing the video underneath it. */
private const val STILL_FADE_MS = 1_500

/** How long the startup spinner takes to fade out once there is a frame to show. */
private const val LOADER_FADE_MS = 400

/** Spinner size and stroke, sized up for a ten-foot screen. */
private const val LOADER_SIZE_DP = 88
private const val LOADER_STROKE_DP = 6

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TransitionCountdown(
	viewModel: PlayerViewModel,
	modifier: Modifier = Modifier,
) {
	// Collected here rather than in VideoPlayerScreen so the 10Hz countdown only recomposes
	// this composable. Reading it in the parent would recompose the player surfaces just as
	// often, and each recomposition re-applies the video surface.
	val remainingMs by viewModel.remainingMs.collectAsStateWithLifecycle()
	val seconds = ceil(remainingMs / 1000.0).toInt().coerceAtLeast(0)

	Box(
		modifier =
			modifier
				.padding(12.dp)
				.background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
				.padding(horizontal = 9.dp, vertical = 4.dp),
	) {
		Text(
			text = seconds.toString(),
			style = MaterialTheme.typography.titleMedium,
			color = Color.White,
		)
	}
}
