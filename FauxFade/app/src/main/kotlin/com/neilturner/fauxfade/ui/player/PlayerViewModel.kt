package com.neilturner.fauxfade.ui.player

import android.app.Application
import android.graphics.Bitmap
import android.os.Debug
import android.util.Log
import androidx.annotation.OptIn
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.neilturner.fauxfade.BuildConfig
import com.neilturner.fauxfade.TAG
import com.neilturner.fauxfade.media.Playlist
import com.neilturner.fauxfade.media.VideoItem
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.coroutineContext
import kotlin.time.TimeSource

/**
 * Owns the two players and the handoff between them.
 *
 * One slot plays for [PLAY_DURATION_MS], then the other is brought up in its place. Halfway
 * through the segment the incoming video is loaded and primed so the handoff has something to
 * show immediately. The whole cycle is driven from a single coroutine, which is also what
 * publishes [remainingMs], so the countdown cannot drift out of step with the actual switch.
 */
@OptIn(UnstableApi::class)
class PlayerViewModel(
	application: Application,
) : AndroidViewModel(application) {
	private val players = createPlayerSlots(application)

	/**
	 * The clips in the order this run will play them, shuffled once so the app does not always
	 * open on the same clip.
	 *
	 * Held as a field rather than shuffled at each use: the loop walks this list for the whole
	 * run, so re-rolling it partway through would swap the clip under the player mid-handoff.
	 */
	private val order = Playlist.shuffled()

	/**
	 * One tap per slot, installed into the player's effect chain so the still can be taken from
	 * the frame the decoder just produced rather than copied back off the surface.
	 */
	private val taps = PlayerSlot.entries.associateWith { StillTapEffect() }

	private val _foreground = MutableStateFlow(PlayerSlot.PRIMARY)
	val foreground: StateFlow<PlayerSlot> = _foreground.asStateFlow()

	/**
	 * True while the handoff is in progress and both surfaces have to be up at once, so the
	 * incoming player can get a frame rendered before it is brought to the front.
	 */
	private val _swapping = MutableStateFlow(false)
	val swapping: StateFlow<Boolean> = _swapping.asStateFlow()

	private val _stillFrame = MutableStateFlow<Bitmap?>(null)
	val stillFrame: StateFlow<Bitmap?> = _stillFrame.asStateFlow()

	private val _stillPhase = MutableStateFlow(StillPhase.HIDDEN)
	val stillPhase: StateFlow<StillPhase> = _stillPhase.asStateFlow()

	private val _remainingMs = MutableStateFlow(PLAY_DURATION_MS)
	val remainingMs: StateFlow<Long> = _remainingMs.asStateFlow()

	/**
	 * True until the opening clip has a frame on screen. Both surfaces are empty before that and
	 * the window behind them is black, so there is nothing worth showing yet.
	 */
	private val _loading = MutableStateFlow(true)
	val loading: StateFlow<Boolean> = _loading.asStateFlow()

	private val _memoryMb = MutableStateFlow(0L)
	val memoryMb: StateFlow<Long> = _memoryMb.asStateFlow()

	private var loopJob: Job? = null
	private var memoryJob: Job? = null

	fun playerFor(slot: PlayerSlot): ExoPlayer = players.getValue(slot)

	fun start() {
		if (loopJob != null) return
		// setVideoEffects puts a frame processor in the render path. The effect is a
		// pass-through, so what reaches the display is unchanged, but it has to be installed
		// before playback starts rather than rebuilt underneath a running player.
		taps.forEach { (slot, tap) -> players.getValue(slot).setVideoEffects(listOf(tap)) }
		loopJob = viewModelScope.launch { runPlaylistLoop() }
		startMemoryWatch()
	}

	/**
	 * Polls the process's PSS. Worth watching here because the still is a full-resolution
	 * bitmap allocated on every handoff - if those are not being collected promptly the figure
	 * climbs a little every segment.
	 *
	 * Debug builds only. A release build has nothing to show the figure in, and a binder call
	 * every two seconds is not worth paying for.
	 */
	private fun startMemoryWatch() {
		if (!BuildConfig.DEBUG) return
		if (memoryJob != null) return
		memoryJob =
			viewModelScope.launch {
				while (isActive) {
					// Fills a struct in-process rather than going through ActivityManager, and it is
					// a native call, so it still belongs off the main dispatcher.
					val kb =
						withContext(Dispatchers.IO) {
							val info = Debug.MemoryInfo()
							runCatching { Debug.getMemoryInfo(info) }.getOrDefault(Unit)
							info.totalPss.toLong()
						}
					_memoryMb.value = kb / 1024
					delay(MEMORY_POLL_MS)
				}
			}
	}

	fun stop() {
		loopJob?.cancel()
		loopJob = null
		memoryJob?.cancel()
		memoryJob = null
		players.values.forEach { it.pause() }
	}

	private suspend fun runPlaylistLoop() {
		var onScreen = PlayerSlot.PRIMARY
		var currentIndex = 0
		_loading.value = true
		// Filenames rather than titles, so a log line can be matched back to a URL when one of
		// them stops playing.
		Log.i(TAG, "playlist: ${order.map { it.uri.substringAfterLast('/') }}")

		// Hold the loader until there is a real frame behind it. STATE_READY is too early to
		// trust: the surface is still empty at that point, so clearing on it would only reveal
		// another black rectangle.
		val opening = players.getValue(onScreen)
		val opened = opening.firstRenderSignal()
		val startedAt = TimeSource.Monotonic.markNow()
		opening.playFromStart(order[currentIndex])
		if (withTimeoutOrNull(STARTUP_FRAME_TIMEOUT_MS) { opened.await() } == null) {
			Log.w(TAG, "startup: $onScreen had no first frame in ${STARTUP_FRAME_TIMEOUT_MS}ms")
		}
		// Cleared either way rather than left spinning: if the frame never arrives there is
		// nothing underneath to reveal, but a loader that never goes away is a worse failure.
		Log.i(TAG, "startup: first frame after ${startedAt.elapsedNow().inWholeMilliseconds}ms")
		_loading.value = false

		while (coroutineContext.isActive) {
			val incoming = onScreen.opposite()
			val nextIndex = (currentIndex + 1) % order.size
			tickSegment { players.getValue(incoming).preload(order[nextIndex]) }

			val outgoing = players.getValue(onScreen)

			// Arm the tap, let it catch a frame, and only pause once the capture has landed.
			//
			// The arm has to come before the pause because the readback can only happen inside a
			// frame callback and a paused player produces no frames. But pausing a fixed lead
			// *after* the arm - as this used to - is what put the still out of step with the video:
			// the tap takes the first frame to arrive, so a 150ms lead left the still on a frame
			// from 150ms before the freeze while the player carried on and stopped several frames
			// later. On a slowly panning clip that reads as the still drifting when the video comes
			// back. Pausing after the capture instead means the player stops on the frame that was
			// actually taken.
			val t0 = TimeSource.Monotonic.markNow()
			val signal = taps.getValue(onScreen).arm()
			val still = grabStill(onScreen, signal)
			val afterCaptureMs = t0.elapsedNow().inWholeMilliseconds

			outgoing.pause()
			Log.i(
				TAG,
				"pause: outgoing=$onScreen index=$currentIndex positionMs=${outgoing.currentPosition}",
			)
			// Debug-only: the mean costs a full-resolution readback plus a downscale and a pixel walk
			// on every handoff, so the line is skipped outright in a release build.
			meanRgb(still)?.let { Log.i(TAG, "still: internal $it") }

			// Seal the still: opaque, and explicitly not fading yet. The fade does not start
			// until the swap below is known to be good, so the whole lead-in and the entire wait
			// for the incoming player happen over a static image rather than a live surface.
			//
			// With no still there is nothing to hold the screen, so the handoff degrades to a
			// plain cut: drop any leftover bitmap, stay hidden, and let the surfaces change under
			// a live video rather than flashing an empty overlay on the way past.
			if (still != null) {
				_stillFrame.value = still
				_stillPhase.value = StillPhase.SEALED

				// Give the sealed still a beat to land before anything behind it changes. It is
				// the only thing covering the swap, so it has to be composited first.
				delay(STILL_LEAD_IN_MS)
			} else {
				_stillFrame.value = null
				_stillPhase.value = StillPhase.HIDDEN
			}

			// Bring the incoming surface up without hiding the outgoing one yet. Both are visible
			// for a moment, but the sealed still is drawn in the window, and a SurfaceView
			// composites below the window, so it covers both. Register the listener before the
			// surface goes up: once the surface is attached the frame can land at any point.
			val incomingPlayer = players.getValue(incoming)
			val firstFrame = incomingPlayer.firstRenderSignal()
			incomingPlayer.play()
			_swapping.value = true

			val rendered = withTimeoutOrNull(FIRST_FRAME_TIMEOUT_MS) { firstFrame.await() }
			if (rendered == null) {
				Log.w(TAG, "swap: $incoming had no rendered frame in ${FIRST_FRAME_TIMEOUT_MS}ms")
			}
			val afterSwapMs = t0.elapsedNow().inWholeMilliseconds

			_foreground.value = incoming
			_swapping.value = false
			// Only a sealed still has something to fade. Leaving the phase alone keeps it hidden,
			// so the video underneath simply carries on into the next clip.
			if (still != null) _stillPhase.value = StillPhase.FADING
			Log.i(
				TAG,
				"swap: foreground -> $incoming incomingIndex=$nextIndex firstFrame=${rendered == true}",
			)
			// Every phase of the handoff, and what the whole thing costs. The fade is animated in
			// the composable and logs separately, so this covers everything up to the point the
			// fade starts.
			Log.i(
				TAG,
				"timing: captureMs=$afterCaptureMs " +
					"sealedMs=${afterSwapMs - afterCaptureMs} handoffMs=$afterSwapMs",
			)

			onScreen = incoming
			currentIndex = nextIndex
		}
	}

	/**
	 * Completes the first time this player renders a frame, and detaches its listener when
	 * awaited or abandoned, so a long-lived listener does not pile up on the player.
	 */
	private fun ExoPlayer.firstRenderSignal(): CompletableDeferred<Boolean> {
		val signal = CompletableDeferred<Boolean>()
		val listener =
			object : Player.Listener {
				override fun onRenderedFirstFrame() {
					signal.complete(true)
				}
			}
		addListener(listener)
		signal.invokeOnCompletion { removeListener(listener) }
		return signal
	}

	/**
	 * The still, read back out of the tap.
	 *
	 * Null when the tap did not catch a frame, which the caller treats as "cut rather than
	 * dissolve" rather than reaching for a second capture path.
	 */
	private suspend fun grabStill(
		slot: PlayerSlot,
		signal: CompletableDeferred<Unit>,
	): Bitmap? {
		val tap = taps.getValue(slot)
		val started = TimeSource.Monotonic.markNow()
		withTimeoutOrNull(TAP_CAPTURE_TIMEOUT_MS) { signal.await() }

		val tapped = tap.latest()
		val waitedMs = started.elapsedNow().inWholeMilliseconds
		if (tapped == null) {
			Log.w(TAG, "still: no frame from $slot after ${waitedMs}ms, cutting instead")
		} else {
			Log.i(TAG, "still: source=tap waitedMs=$waitedMs")
		}
		return tapped
	}

	/**
	 * Mean R/G/B of a still, downsampled first. Walking two million pixels one at a time is
	 * slow enough to show up as a hitch at exactly the wrong moment, and the mean does not need
	 * the resolution. This is the only way to check a colour claim about the still without
	 * having to trust the TV.
	 *
	 * Returns null in a release build, which is how the caller skips the allocation and the walk
	 * entirely rather than doing the work and throwing it away.
	 */
	private fun meanRgb(bitmap: Bitmap?): String? {
		if (!BuildConfig.DEBUG) return null
		if (bitmap == null) return "none"
		val w = 64
		val h = 36
		val small = Bitmap.createScaledBitmap(bitmap, w, h, true)
		var r = 0L
		var g = 0L
		var b = 0L
		for (y in 0 until h) {
			for (x in 0 until w) {
				val p = small.getPixel(x, y)
				r += (p shr 16) and 0xFF
				g += (p shr 8) and 0xFF
				b += p and 0xFF
			}
		}
		small.recycle()
		return "${r / (w * h)},${g / (w * h)},${b / (w * h)}"
	}

	/**
	 * Runs the countdown for one segment, calling [onPreloadPoint] once as the preload time
	 * comes round.
	 *
	 * The countdown is published as the time left in the whole segment rather than as a timer
	 * per phase, so it reads 10 down to 0. Running a separate countdown either side of the
	 * preload would restart it at 5.
	 */
	private suspend fun tickSegment(onPreloadPoint: () -> Unit) {
		val start = TimeSource.Monotonic.markNow()
		var preloaded = false
		while (true) {
			val elapsed = start.elapsedNow().inWholeMilliseconds
			if (elapsed >= PLAY_DURATION_MS) break
			if (!preloaded && elapsed >= PRELOAD_AFTER_MS) {
				preloaded = true
				onPreloadPoint()
			}
			_remainingMs.value = PLAY_DURATION_MS - elapsed
			delay(COUNTDOWN_TICK_MS)
		}
		_remainingMs.value = 0L
	}

	override fun onCleared() {
		loopJob?.cancel()
		memoryJob?.cancel()
		players.values.forEach { it.release() }
	}
}

@OptIn(UnstableApi::class)
private fun ExoPlayer.playFromStart(item: VideoItem) {
	playWhenReady = true
	setMediaItem(item.toMediaItem(), C.TIME_UNSET)
	prepare()
}

@OptIn(UnstableApi::class)
private fun ExoPlayer.preload(item: VideoItem) {
	playWhenReady = false
	setMediaItem(item.toMediaItem(), C.TIME_UNSET)
	prepare()
}

/** How long each video plays before the handoff to the other slot. */
private const val PLAY_DURATION_MS = 10_000L

/** How often the memory readout is refreshed. PSS is a binder call, so it is not free. */
private const val MEMORY_POLL_MS = 2_000L

/** How long before the end of a segment the next video is loaded and primed. */
private const val PRELOAD_AFTER_MS = 5_000L

/**
 * How long to wait for an armed readback to land before falling back to a surface copy. The
 * player is still playing during this, so a normal capture arrives within a frame or two.
 */
private const val TAP_CAPTURE_TIMEOUT_MS = 500L

/**
 * Pause between sealing the still and swapping the surfaces, so the still is composited at
 * full opacity before anything behind it changes. One or two frames is enough.
 */
private const val STILL_LEAD_IN_MS = 150L

/**
 * How long to wait for the incoming player to render its first frame before swapping anyway.
 *
 * The still is sealed and fully opaque for the whole wait, so nothing is visible here except
 * the pause itself. The timeout only stops a player that never renders - a stalled stream, say
 * - from freezing the handoff indefinitely.
 */
private const val FIRST_FRAME_TIMEOUT_MS = 1_000L

/**
 * How long to wait for the opening clip to render its first frame before clearing the loader
 * anyway. Generous, because a cold start is a network fetch and a decoder spin-up; it is only
 * there so a clip that never renders cannot leave the spinner up for good.
 */
private const val STARTUP_FRAME_TIMEOUT_MS = 15_000L

private const val COUNTDOWN_TICK_MS = 100L
