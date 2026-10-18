package com.neilturner.fauxfade.feature.pixelcopy

import android.util.Log
import android.view.SurfaceView
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.RadioButton
import androidx.tv.material3.Text
import com.neilturner.fauxfade.BuildConfig
import com.neilturner.fauxfade.core.media.Quality
import kotlin.time.TimeSource

/**
 * The whole PixelCopy feature: pick a quality, start, and watch one player cut between clips with a
 * copied still hiding each change.
 *
 * Owns its own picker rather than sharing one, which is the point of the split. A feature that
 * borrowed the app's quality control would be borrowing a decision the rest of the app also makes,
 * and the two would have to agree about what a quality means before either could be read on its
 * own. This one is small enough to hold in a single file: the picker is the same panel the startup
 * screen had, and the player half is one surface rather than two.
 */
@OptIn(ExperimentalTvMaterial3Api::class, UnstableApi::class)
@Composable
fun PixelCopyScreen(
	modifier: Modifier = Modifier,
	viewModel: PixelCopyViewModel = viewModel(),
) {
	val phase by viewModel.phase.collectAsStateWithLifecycle()
	if (phase == Phase.PLAYING) {
		PixelCopyPlayer(viewModel = viewModel, modifier = modifier)
	} else {
		PixelCopyPicker(viewModel = viewModel, modifier = modifier)
	}
}

/**
 * The startup half: a quality picker and a start button.
 *
 * No countdown of its own in the default configuration - see [AUTO_START] in the view model. The
 * screen is deliberately bare, because the point of it is to be gone.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PixelCopyPicker(
	viewModel: PixelCopyViewModel,
	modifier: Modifier = Modifier,
) {
	val quality by viewModel.quality.collectAsStateWithLifecycle()
	val countdown by viewModel.countdown.collectAsStateWithLifecycle()

	var open by remember { mutableStateOf(false) }

	// Built for every entry up front rather than on demand: a FocusRequester has to be the same
	// instance the row's modifier was built with, and a lazily created one would not be.
	// [triggerFocus] is the one for the controls themselves, for the reason in the effect below.
	val qualityFocus = remember { Quality.entries.associateWith { FocusRequester() } }
	val triggerFocus = remember { FocusRequester() }

	// Put focus on the first control whenever this screen is composed, which is the only thing that
	// reliably does it - see the same effect on `StartupScreen` for why natural focus is not enough
	// once the app has been up. Coming back from playback is the case that matters here: the player
	// screen un-composes, this one composes in its place, and without this the D-pad does nothing
	// at all until a key is pressed to hand focus back to the framework.
	LaunchedEffect(Unit) {
		withFrameNanos { }
		triggerFocus.requestFocus()
	}

	// Focus the selected row while the panel is open, and leave focus entirely alone on the way
	// out. The dismissal decides where focus lands, and from there every control is at most one
	// D-pad press away.
	LaunchedEffect(open, quality) {
		withFrameNanos { }
		if (open) qualityFocus.getValue(quality).requestFocus()
	}

	Box(
		modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
		contentAlignment = Alignment.Center,
	) {
		Column(
			horizontalAlignment = Alignment.CenterHorizontally,
			verticalArrangement = Arrangement.spacedBy(GAP),
			modifier = Modifier.widthIn(max = PANEL_WIDTH),
		) {
			Text(
				text = "PixelCopy",
				style = MaterialTheme.typography.headlineMedium,
				color = MaterialTheme.colorScheme.onBackground,
			)

			Button(onClick = { open = true }, modifier = Modifier.focusRequester(triggerFocus)) {
				Text(quality.label, style = MaterialTheme.typography.titleMedium)
			}

			Button(onClick = viewModel::skipCountdown) {
				Text(startLabel(countdown), style = MaterialTheme.typography.titleMedium)
			}
		}

		// Sibling of the controls rather than a child of them: a popup has to cover the whole
		// screen, and a fillMaxSize inside the centred column would be bounded by it.
		if (open) {
			QualityPanel(
				selected = quality,
				focusRequesters = qualityFocus,
				onPick = {
					viewModel.select(it)
					open = false
				},
				onDismiss = { open = false },
			)
		}
	}
}

/**
 * The quality list, over a scrim.
 *
 * Pulled out of the picker above so the scrim and the key handling live in one place, and so the
 * panel reads as a thing of its own rather than as a branch inside the screen.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun QualityPanel(
	selected: Quality,
	focusRequesters: Map<Quality, FocusRequester>,
	onPick: (Quality) -> Unit,
	onDismiss: () -> Unit,
) {
	Box(
		modifier =
			Modifier
				.fillMaxSize()
				.background(MaterialTheme.colorScheme.scrim.copy(alpha = SCRIM_ALPHA))
				.onPreviewKeyEvent { event ->
					// Not filtered to key-down. By the time the release arrives the panel is already
					// closed and setting the flag again does nothing, and swallowing it keeps Back
					// from reaching anything behind the scrim.
					if (event.key == Key.Back) {
						onDismiss()
						true
					} else {
						false
					}
				},
		contentAlignment = Alignment.Center,
	) {
		Box(
			modifier =
				Modifier
					.widthIn(max = PANEL_WIDTH)
					.background(MaterialTheme.colorScheme.surface, RoundedCornerShape(CORNER))
					// The horizontal gap is headroom, not styling. A focused row is scaled up
					// about 5%, which at this width measures out at roughly 12dp out either side,
					// and without the gap the row pushes out through the panel edge and hangs in
					// the scrim.
					.padding(horizontal = ROW_ROOM, vertical = PANEL_PADDING)
					// Traps the arrow keys inside the panel. Without it, up from the first row
					// escapes to the button behind the scrim, which is still focusable and still
					// reacts to the click.
					.focusGroup(),
		) {
			Column {
				Quality.entries.forEach { option ->
					QualityRow(
						selected = option == selected,
						headline = option.label,
						focusRequester = focusRequesters.getValue(option),
						onClick = { onPick(option) },
					)
				}
			}
		}
	}
}

/**
 * One selectable quality.
 *
 * Focus tints, selection does not. The row under the D-pad needs somewhere to be. The quality
 * already chosen says itself with its filled radio, so tinting that row as well marked the answer
 * twice and made it look highlighted rather than answered.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun QualityRow(
	selected: Boolean,
	headline: String,
	focusRequester: FocusRequester,
	onClick: () -> Unit,
) {
	ListItem(
		selected = selected,
		onClick = onClick,
		colors =
			ListItemDefaults.colors(
				containerColor = Color.Transparent,
				focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
				selectedContainerColor = Color.Transparent,
				contentColor = MaterialTheme.colorScheme.onSurface,
				focusedContentColor = MaterialTheme.colorScheme.onSurface,
				selectedContentColor = MaterialTheme.colorScheme.onSurface,
				focusedSelectedContentColor = MaterialTheme.colorScheme.onSurface,
			),
		headlineContent = { Text(headline, style = MaterialTheme.typography.titleMedium) },
		leadingContent = {
			// Null onClick: the row is the click target, and a second one here would put an extra
			// stop in the D-pad's way.
			RadioButton(selected = selected, onClick = null)
		},
		modifier = Modifier.focusRequester(focusRequester),
	)
}

/**
 * The playback half: one surface, the still that covers its re-open, and the readouts.
 *
 * One `SurfaceView`, not two. There is nothing to swap between - the same player stops and starts
 * again on the next clip - so the visibility juggling the two-player screen does during a handoff
 * has no work to do here and is left out entirely.
 *
 * The surface is handed up rather than held onto: it lands in a `MutableState` the grabber reads at
 * the moment it is asked for a frame, so nothing in this screen outlives the composition that made
 * it, and the view model that asks for the copy never sees a `View` at all.
 */
@OptIn(UnstableApi::class, ExperimentalTvMaterial3Api::class)
@Composable
private fun PixelCopyPlayer(
	viewModel: PixelCopyViewModel,
	modifier: Modifier = Modifier,
) {
	val loading by viewModel.loading.collectAsStateWithLifecycle()

	// The surface, which this screen owns and the loop cannot. Lensed to the view model as a
	// function - see [SurfaceGrabber] - so no View is ever held by the view model.
	val surface = remember { mutableStateOf<SurfaceView?>(null) }
	val grabber =
		remember {
			val capture = PixelCopyCapture()
			SurfaceGrabber { target ->
				val view = surface.value ?: return@SurfaceGrabber null
				// Not succeeded, not a black frame: a still of nothing is worse than a cut.
				val result = capture.capture(view, target)
				Log.i(
					TAG,
					"copy: target=$target status=${result.status} ok=${result.succeeded} " +
						"size=${result.bitmap?.width}x${result.bitmap?.height} " +
						"scaled=${result.scaled} copyMs=${result.elapsedMs}",
				)
				if (result.succeeded) result.bitmap else null
			}
		}

	DisposableEffect(grabber) {
		// Attached before start: the loop may ask for the surface on its first handoff.
		viewModel.attachGrabber(grabber)
		viewModel.start()
		onDispose { viewModel.stop() }
	}

	Box(
		modifier =
			modifier
				.fillMaxSize()
				// Not a theme colour on purpose. This is the letterbox around the picture, which
				// has to be true black or it reads as a glowing edge on a dark television; the
				// app's own background is a shade off black for the screens that sit against it.
				.background(Color.Black),
	) {
		SinglePlayerSurface(
			viewModel = viewModel,
			onSurface = { surface.value = it },
			modifier = Modifier.fillMaxSize(),
		)

		StillOverlay(
			viewModel = viewModel,
			modifier = Modifier.fillMaxSize(),
		)

		// Debug-only. Nothing is published into memoryMb in a release build, so there is no point
		// composing a readout that would sit at zero.
		if (BuildConfig.DEBUG) {
			MemoryReadout(viewModel = viewModel, modifier = Modifier.align(Alignment.TopStart))
		}

		TransitionCountdown(viewModel = viewModel, modifier = Modifier.align(Alignment.TopEnd))

		PlayingQualityLabel(viewModel = viewModel, modifier = Modifier.align(Alignment.BottomEnd))

		// Last, so it covers the surface and the readouts. Like the still it is drawn in the window,
		// which composites above a SurfaceView.
		StartupLoader(loading = loading, modifier = Modifier.fillMaxSize())
	}
}

/**
 * A [SurfaceView] for the one player this feature has.
 *
 * This is the one place that uses `AndroidView`. `androidx.media3.ui.compose.PlayerSurface` is the
 * Compose-native surface, but it renders into a TextureView, which forces video through the app's
 * own view pipeline and colour space and so rules out HDR passthrough.
 *
 * The view is handed back so the screen can copy from it - it has to live in composition for a
 * PixelCopy to reach it, so no View is ever held by the view model.
 */
@OptIn(UnstableApi::class)
@Composable
private fun SinglePlayerSurface(
	viewModel: PixelCopyViewModel,
	onSurface: (SurfaceView) -> Unit,
	modifier: Modifier = Modifier,
) {
	val player = viewModel.player()

	// The view the player's surface was last handed to. AndroidView builds it once and reuses it,
	// so this is really just a "has the surface been set yet" flag - and it stops the update block
	// re-setting the same view on every recomposition, which churns the surface generation and
	// makes the decoder discard buffered frames.
	val attached = remember { mutableStateOf<SurfaceView?>(null) }

	AndroidView(
		modifier = modifier,
		factory = { context -> SurfaceView(context).also(onSurface) },
		update = { view ->
			if (attached.value !== view) {
				player.setVideoSurfaceView(view)
				attached.value = view
			}
		},
	)
}

/**
 * A frame captured from the outgoing video, held over the screen while the player re-opens on the
 * next clip underneath it, then faded out to reveal the new one.
 *
 * A plain Compose image rather than another surface, so its alpha is a normal draw-time value - no
 * compositor or SurfaceControl involvement - and it costs no decoder.
 */
@Composable
private fun StillOverlay(
	viewModel: PixelCopyViewModel,
	modifier: Modifier = Modifier,
) {
	val stillFrame by viewModel.stillFrame.collectAsStateWithLifecycle()
	val stillPhase by viewModel.stillPhase.collectAsStateWithLifecycle()
	val fade = remember { Animatable(0f) }

	// Keyed on the phase rather than the bitmap: the outgoing clip alternates, so the same still
	// can come round again and has to be sealed and faded from scratch each time.
	LaunchedEffect(stillPhase, stillFrame) {
		when (stillPhase) {
			StillPhase.HIDDEN -> {
				fade.snapTo(0f)
			}

			// Snapped, not ramped. The still goes up over the outgoing video already paused on the
			// same frame, so it has to be fully opaque the instant it appears - there is nothing
			// behind it to dissolve against until the player re-opens.
			StillPhase.SEALED -> {
				fade.snapTo(1f)
			}

			StillPhase.FADING -> {
				val started = TimeSource.Monotonic.markNow()
				fade.snapTo(1f)
				fade.animateTo(0f, tween(STILL_FADE_MS))
				// Logged next to the view model's timing line, which covers everything up to the
				// point this fade starts.
				Log.i(TAG, "timing: fadeMs=${started.elapsedNow().inWholeMilliseconds}")
			}
		}
	}

	// Derived so the fade value is not read in the composition scope. Read directly every one of
	// the ~90 frames of a 1.5s fade would recompose this composable and rebuild its modifier chain.
	// A derived state only invalidates its readers when the answer flips, so this costs one
	// recomposition when the still arrives and one when it has gone, and nothing in between.
	val fadedOut = remember { derivedStateOf { fade.value <= FADE_DONE_AT } }

	val bitmap = stillFrame
	if (bitmap == null || fadedOut.value) return

	Image(
		// Wrapped once per captured frame, not once per animation frame. Image keys its painter on
		// the ImageBitmap instance, so a fresh wrapper every frame is a fresh painter every frame,
		// and a fresh painter is a fresh draw node to configure.
		bitmap = remember(bitmap) { bitmap.asImageBitmap() },
		contentDescription = null,
		// graphicsLayer rather than Modifier.alpha: reading the value inside the lambda defers it
		// to the draw pass, so a frame of the fade invalidates drawing and nothing else.
		modifier = modifier.graphicsLayer { alpha = fade.value },
		contentScale = ContentScale.Crop,
	)
}

/**
 * Black cover with a spinner, up until the opening clip has a frame on screen.
 *
 * There is nothing behind it to hide - the surface is empty and the window is already black - so
 * this is not covering a flash so much as making the wait deliberate rather than looking like an
 * app that failed to start. The spinner is oversized for a ten-foot screen.
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
				color = MaterialTheme.colorScheme.onSurface,
				strokeWidth = LOADER_STROKE_DP.dp,
				modifier = Modifier.size(LOADER_SIZE_DP.dp),
			)
		}
	}
}

/**
 * The small translucent chip the debug readouts sit in.
 *
 * One composable for all three: they are the same shape, the same tint and the same padding, and
 * having them written out three times is how they drift apart.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ReadoutChip(
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

/**
 * Process memory in the top left. The still is a full-resolution bitmap allocated on every
 * handoff, so this is how you would notice them accumulating rather than being collected.
 *
 * Only ever composed in a debug build - see the call site in [PixelCopyPlayer].
 */
@Composable
private fun MemoryReadout(
	viewModel: PixelCopyViewModel,
	modifier: Modifier = Modifier,
) {
	val memoryMb by viewModel.memoryMb.collectAsStateWithLifecycle()
	ReadoutChip(text = "$memoryMb MB", modifier = modifier)
}

/**
 * The quality actually playing, bottom right.
 *
 * Reads [PixelCopyViewModel.playingQuality] rather than the picker selection, so it shows what the
 * stream really is - a run is locked to one quality, and this is the record of which.
 */
@Composable
private fun PlayingQualityLabel(
	viewModel: PixelCopyViewModel,
	modifier: Modifier = Modifier,
) {
	val quality by viewModel.playingQuality.collectAsStateWithLifecycle()
	ReadoutChip(text = quality.label, modifier = modifier)
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TransitionCountdown(
	viewModel: PixelCopyViewModel,
	modifier: Modifier = Modifier,
) {
	// Collected here rather than in the parent so the countdown only recomposes this composable.
	// Reading it in the parent would recompose the video surface as the timer ran, and each
	// recomposition re-applies the video surface.
	//
	// The view model publishes whole seconds rather than milliseconds. The underlying tick is ten a
	// second, and the label only changes once a second, so collecting milliseconds here would
	// recompose ten times for every digit that moves.
	val seconds by viewModel.remainingSeconds.collectAsStateWithLifecycle()
	ReadoutChip(text = seconds.toString(), modifier = modifier)
}

/** "Start playback (4)" while there is time left, plain "Start playback" once there is not. */
private fun startLabel(countdown: Int): String = if (countdown > 0) "Start playback ($countdown)" else "Start playback"

/** How long the captured still takes to fade out, revealing the video underneath it. */
private const val STILL_FADE_MS = 1_500

/** How long the startup spinner takes to fade out once there is a frame to show. */
private const val LOADER_FADE_MS = 400

/**
 * Spinner size and stroke.
 *
 * Sized for a ten-foot screen, but no bigger: a spinner this large and this alone on a black
 * screen reads as a splash screen the app has stalled on rather than a wait that is about to end.
 */
private const val LOADER_SIZE_DP = 44
private const val LOADER_STROKE_DP = 3

/** How opaque the readout chips are over the video. */
private const val CHIP_ALPHA = 0.45f

/**
 * Below this the still counts as gone. Used as a derived condition rather than read directly, so
 * the fade crossing it is what takes the image out of the tree and the frames in between are not.
 */
private const val FADE_DONE_AT = 0.001f

/** How opaque the dim over the controls is while a panel is open. */
private const val SCRIM_ALPHA = 0.65f

/** Wide enough for the longest label on a ten-foot screen without stretching across it. */
private val PANEL_WIDTH = 520.dp

private val PANEL_PADDING = 8.dp

/**
 * Clear space inside the panel either side of the rows, so a focused row's scale stays within the
 * panel. Sized from the measured 5% focus scale at [PANEL_WIDTH], plus a little slack.
 */
private val ROW_ROOM = 18.dp

private val GAP = 24.dp

private val CORNER = 16.dp

/** Logcat tag for this feature. Per-feature so a logcat filter can isolate one run of one approach. */
private const val TAG = "FauxFade.PixelCopy"
