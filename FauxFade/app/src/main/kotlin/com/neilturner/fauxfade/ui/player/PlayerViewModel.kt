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
import com.neilturner.fauxfade.media.AerialAsset
import com.neilturner.fauxfade.media.Quality
import com.neilturner.fauxfade.media.VideoCatalog
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
import kotlin.math.ceil
import kotlin.time.TimeSource

/**
 * Owns the two players and the handoff between them.
 *
 * One slot plays for [PLAY_DURATION_MS], then the other is brought up in its place. Part way
 * through the segment - at [PRELOAD_AFTER_MS] - the incoming video is loaded and primed so the
 * handoff has something to show immediately and has the rest of the segment to get ahead. The
 * whole cycle is driven from a single coroutine, which is also what publishes
 * [remainingSeconds], so the countdown cannot drift out of step with the actual switch.
 *
 * It also owns the startup flow: the app sits in [AppPhase.Picking] until the quality has been
 * chosen and the countdown has run out (or been skipped), and only then are the players built
 * and the loop started. Nothing about playback is torn down or rebuilt to change quality - the
 * picker is gone by the time playback exists.
 */
@OptIn(UnstableApi::class)
class PlayerViewModel(
	application: Application,
) : AndroidViewModel(application) {
	/**
	 * Built when playback begins rather than here.
	 *
	 * Lateinit on purpose: the buffer cap depends on the chosen quality, so they cannot exist
	 * before the picker has been answered. Touching this before then throws
	 * UninitializedPropertyAccessException, which is a much easier failure to trace than a
	 * silently empty map.
	 */
	private lateinit var players: Map<PlayerSlot, ExoPlayer>

	private val _phase = MutableStateFlow(AppPhase.PICKING)
	val phase: StateFlow<AppPhase> = _phase.asStateFlow()

	/** The quality on offer to play at. Defaults to 1080p SDR. */
	private val _quality = MutableStateFlow(Quality.FHD_SDR)
	val quality: StateFlow<Quality> = _quality.asStateFlow()

	/**
	 * Seconds left on the startup countdown. Zero when there is no countdown running, which is
	 * what makes the button read plain "Start playback" rather than showing a number.
	 */
	private val _countdown = MutableStateFlow(if (AUTO_START) START_COUNTDOWN_SECONDS else 0)
	val countdown: StateFlow<Int> = _countdown.asStateFlow()

	/**
	 * The catalogue, parsed once.
	 *
	 * Held behind a deferred rather than a plain field because playback can begin before the
	 * parse has finished - the button skips the whole countdown, and nothing stops a skip landing
	 * in the first few milliseconds.
	 */
	private val assetsLoaded = CompletableDeferred<List<AerialAsset>>()

	/**
	 * The clips in the order this run will play them, chosen from the catalogue at the selected
	 * quality and held as a field rather than re-rolled at each use: the loop walks this list for
	 * the whole run, so re-rolling it partway through would swap the clip under the player
	 * mid-handoff.
	 */
	private lateinit var order: List<VideoItem>

	/**
	 * The quality playback actually started at.
	 *
	 * Distinct from [_quality], which the picker is still free to change right up until the
	 * countdown runs out. The handoff has to read a stable value, because a timeout that changed
	 * mid-loop would be a different timeout on either side of a swap.
	 *
	 * Seeded with the default so it has a value before playback begins, though nothing reads it
	 * that early - the player screen is only composed once the phase has flipped.
	 */
	private val _playingQuality = MutableStateFlow(Quality.FHD_SDR)
	val playingQuality: StateFlow<Quality> = _playingQuality.asStateFlow()

	/**
	 * One tap per slot, installed into the player's effect chain so the still can be taken from
	 * the frame the decoder just produced rather than copied back off the surface.
	 */
	private val taps = PlayerSlot.entries.associateWith { StillTapEffect() }

	/**
	 * One per player, for [StillCaptureMode.SCHEDULED_EFFECT].
	 *
	 * Built in [beginPlayback] rather than here because each capture needs its player's
	 * application looper to post its own hand-back on, and that only exists once the players do -
	 * the same reason [players] is not built at construction.
	 */
	private lateinit var scheduled: Map<PlayerSlot, ScheduledFrameCapture>

	/** One per player, for [StillCaptureMode.METADATA_RETRIEVER]. Stateless, so this is free. */
	private lateinit var retrievers: Map<PlayerSlot, RetrieverFrameCapture>

	/** Which path the run captures its stills with. See [StillCaptureMode]. */
	private val captureMode = CAPTURE_MODE

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

	private val _remainingSeconds = MutableStateFlow(SECONDS_PER_SEGMENT)

	/**
	 * Seconds left in the current segment, for the readout.
	 *
	 * Whole seconds rather than milliseconds because that is the only resolution the readout has.
	 * The tick that drives the loop runs ten times a second so the preload point can be hit
	 * accurately, and a state flow only emits when its value actually changes, so publishing the
	 * second keeps that finer tick from turning into ten recompositions a second to move one
	 * digit. The display rounds up, so each number is held for its whole second.
	 */
	val remainingSeconds: StateFlow<Int> = _remainingSeconds.asStateFlow()

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
	private var countdownJob: Job? = null

	/** True while [beginPlayback] is building the players, so a second caller cannot join in. */
	private var starting = false

	// Parse the catalogue, then run the countdown down to playback.
	//
	// The countdown is deliberately not waiting on the picker: the app is a screensaver, so the
	// whole point is that it starts on its own. Changing the quality while it runs is fine - the
	// ten clips are only drawn once playback begins, so a late change is picked up for free.
	init {
		viewModelScope.launch {
			assetsLoaded.complete(VideoCatalog.load(application))
			startCountdown()
		}
	}

	fun playerFor(slot: PlayerSlot): ExoPlayer = players.getValue(slot)

	/** Records the quality to play at. Takes effect when playback starts. */
	fun select(quality: Quality) {
		_quality.value = quality
		Log.i(TAG, "picked: $quality")
	}

	/**
	 * Called from the startup button. Launches rather than doing the work inline because
	 * [beginPlayback] has to wait for the catalogue, and an onClick is not a coroutine.
	 */
	fun skipCountdown() {
		viewModelScope.launch { beginPlayback() }
	}

	private fun startCountdown() {
		if (!AUTO_START) return
		if (countdownJob != null) return
		countdownJob =
			viewModelScope.launch {
				for (n in START_COUNTDOWN_SECONDS downTo 1) {
					_countdown.value = n
					delay(START_COUNTDOWN_TICK_MS)
				}
				_countdown.value = 0
				beginPlayback()
			}
	}

	/**
	 * Locks the choice in: draws the ten clips, builds the players sized for them, and moves to
	 * the playing phase.
	 *
	 * The phase flips last and is the only thing the composables key off, so the players and the
	 * order are always in place before anything can ask for them.
	 */
	private suspend fun beginPlayback() {
		// Reachable from two places at once - the button, and the countdown running out - which
		// collide exactly when a tap lands on the same second the timer expires. Both callers
		// run on the main dispatcher, so a plain flag is enough to keep them apart.
		if (starting || _phase.value == AppPhase.PLAYING) return
		starting = true
		try {
			val quality = _quality.value
			order = VideoCatalog.pick(assetsLoaded.await(), quality)
			if (order.isEmpty()) {
				// Nothing to play. Staying on the picker is the least bad outcome - the
				// alternative is a player screen that sits on a spinner forever.
				Log.e(TAG, "startup: no clips available for $quality, staying on the picker")
				return
			}
			players = createPlayerSlots(getApplication(), quality)
			// After the players, because both need one: the scheduled capture reads the player's
			// application looper when it is built.
			scheduled = PlayerSlot.entries.associateWith { ScheduledFrameCapture(players.getValue(it)) }
			retrievers = PlayerSlot.entries.associateWith { RetrieverFrameCapture() }
			_playingQuality.value = quality
			Log.i(TAG, "startup: $quality, ${order.size} clips")
			_phase.value = AppPhase.PLAYING
		} finally {
			starting = false
			// Cancelled here rather than on entry: this is usually called *from* the countdown
			// job, and cancelling that job before the work is done would abort this function
			// along with it.
			countdownJob?.cancel()
			countdownJob = null
		}
	}

	fun start() {
		if (loopJob != null) return
		// setVideoEffects puts a frame processor in the render path. The effect is a
		// pass-through, so what reaches the display is unchanged, but it has to be installed
		// before playback starts rather than rebuilt underneath a running player.
		//
		// Only the always-on mode does it here. The scheduled path installs its own effect a lead
		// time before each capture, and the retriever path never installs one at all, so for both
		// of those the render path is left completely alone for the whole segment.
		if (CAPTURE_STILLS && captureMode == StillCaptureMode.ALWAYS_ON_TAP) {
			taps.forEach { (slot, tap) -> players.getValue(slot).setVideoEffects(listOf(tap)) }
		}
		// Logged so a run's numbers can be attributed to the path that produced them. The three
		// modes are only comparable next to each other, and this is the only line that says which
		// one a given set of timings came from.
		Log.i(TAG, "capture: mode=$captureMode stills=$CAPTURE_STILLS")
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
		if (::players.isInitialized) players.values.forEach { it.pause() }
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

			// Armed here rather than at the handoff below, and only for the modes that need to run
			// ahead. The scheduled capture installs its effect a fixed lead time *before* its
			// target, so it has to be asked for while this segment is still playing: asking at the
			// handoff would be asking for a position the player has already passed, the message
			// would land at once, and the pipeline would have no time to finish building before
			// the pause. The other two do not want this - the always-on tap answers on the very
			// next frame, so arming it early would capture the start of the segment instead of the
			// end - hence the null.
			val armedStill = armScheduledStill(onScreen)

			tickSegment { players.getValue(incoming).preload(order[nextIndex]) }

			val outgoing = players.getValue(onScreen)

			// Take the still, then pause. The order matters and is not negotiable: a paused player
			// produces no frames, so anything that needs a frame has to get it first. Pausing after
			// the capture is also what keeps the still in step with the video - the frame taken is
			// the frame the player then stops on, rather than one taken a lead earlier while the
			// clip carried on underneath it.
			//
			// How the frame is obtained depends on [captureMode]: the always-on tap is already
			// installed and answers immediately, the scheduled path has been building its pipeline
			// since before the target and should already have the frame, and the retriever path
			// pauses here and then seeks the file.
			val t0 = TimeSource.Monotonic.markNow()
			val still = captureStill(onScreen, order[currentIndex], armedStill)
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

			val swapBudget = _playingQuality.value.swapTimeoutMs
			val rendered = withTimeoutOrNull(swapBudget) { firstFrame.await() }
			if (rendered == null) {
				Log.w(TAG, "swap: $incoming had no rendered frame in ${swapBudget}ms")
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
	 * The still for this handoff, or null if the configured path could not produce one.
	 *
	 * Null is the same failure for all three and is handled the same way by the caller - the
	 * handoff degrades to a cut - so no path has to reach for a second attempt on another's
	 * behalf. What differs is the budget each one is given, because they have wildly different
	 * costs: the always-on tap is already installed and answers on the next frame, the scheduled
	 * capture has had a lead time to build its pipeline, and the retriever has to open and seek a
	 * network file, which is the only one that might genuinely need seconds.
	 *
	 * [armedStill] is the deferred handed out by [armScheduledStill] earlier in this segment, and
	 * is null for the modes that do not run ahead.
	 */
	private suspend fun captureStill(
		slot: PlayerSlot,
		item: VideoItem,
		armedStill: CompletableDeferred<Bitmap?>?,
	): Bitmap? {
		if (!CAPTURE_STILLS) return null
		return when (captureMode) {
			StillCaptureMode.ALWAYS_ON_TAP -> {
				grabStill(slot, taps.getValue(slot).arm())
			}

			StillCaptureMode.SCHEDULED_EFFECT -> {
				val capture = scheduled.getValue(slot)
				try {
					// Collects what the processor took part way through this segment, so it should
					// already be done. The timeout exists only so a pipeline that never came up
					// cannot stall the loop.
					withTimeoutOrNull(_playingQuality.value.tapTimeoutMs) { armedStill?.await() }
				} finally {
					// On every way out of here the capture is finished with, including a timeout,
					// and a half-built pipeline must not be left installed on a player the loop has
					// already stopped using.
					capture.cancelPending()
				}
			}

			StillCaptureMode.METADATA_RETRIEVER -> {
				withTimeoutOrNull(RETRIEVER_TIMEOUT_MS) {
					// Aimed at the position the player has already reached, so the pause message
					// lands at once. The frame comes back from the file rather than from the player,
					// so where the player is stopped does not affect which frame is returned.
					retrievers.getValue(slot).captureAt(players.getValue(slot), item.uri, PLAY_DURATION_MS)
				}
			}
		}
	}

	/**
	 * Starts the scheduled capture for this segment, if that is the mode in use.
	 *
	 * Only the scheduled path needs this: its effect has to be installed and built before its
	 * target frame, and the only moment that can happen is while the segment is still playing.
	 * The always-on tap is the opposite - it captures the next frame it sees, so arming it early
	 * would take the still from the start of the clip instead of the end - and the retriever path
	 * does its work in one go at the handoff. Both get null and are handled there.
	 */
	private fun armScheduledStill(slot: PlayerSlot): CompletableDeferred<Bitmap?>? {
		if (!CAPTURE_STILLS) return null
		if (captureMode != StillCaptureMode.SCHEDULED_EFFECT) return null
		return scheduled.getValue(slot).captureAt(STILL_TARGET_MS)
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
		withTimeoutOrNull(_playingQuality.value.tapTimeoutMs) { signal.await() }
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
			_remainingSeconds.value = secondsLeft(PLAY_DURATION_MS - elapsed)
			delay(COUNTDOWN_TICK_MS)
		}
		_remainingSeconds.value = 0
	}

	override fun onCleared() {
		loopJob?.cancel()
		memoryJob?.cancel()
		countdownJob?.cancel()
		// Before the players: a capture in flight holds a pending PlayerMessage and possibly an
		// installed effect, and both have to be dropped while the player they belong to still
		// exists to drop them from.
		if (::scheduled.isInitialized) scheduled.values.forEach { it.release() }
		// Uninitialised when the app is cleared from the picker, where no player was ever built.
		if (::players.isInitialized) players.values.forEach { it.release() }
	}
}

/** Which of the two top-level screens is up. */
enum class AppPhase {
	/** The quality picker and the countdown. No player exists yet. */
	PICKING,

	/** The video loop. */
	PLAYING,
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

/**
 * How long each video plays before the handoff to the other slot.
 *
 * Fifteen seconds rather than ten. The opening of any of these clips is a codec spin-up and a
 * fade up from black, and at ten seconds that was a sizeable fraction of everything you actually
 * watched; fifteen gives the shot room to establish itself before it is taken away.
 */
private const val PLAY_DURATION_MS = 15_000L

/** Seconds in a segment: what the countdown reads from, and the resolution it is published at. */
private const val SECONDS_PER_SEGMENT = (PLAY_DURATION_MS / 1000).toInt()

/** Rounds a countdown up, so the readout holds each number for its whole second. */
private fun secondsLeft(remainingMs: Long): Int = ceil(remainingMs / 1000.0).toInt().coerceAtLeast(0)

// The two handoff deadlines - how long to wait for the incoming player to render a frame, and
// how long to wait for the armed readback to land - are not constants any more. They moved onto
// [Quality] as swapTimeoutMs and tapTimeoutMs, because a 4K decode on a weak device routinely
// overruns the 1080p figures and the handoff degrades to a cut. What they protect against is
// unchanged: the still is sealed and fully opaque for the whole wait, so nothing is visible
// during it, and the deadlines only stop a player that never renders - a stalled stream, say -
// from freezing the handoff indefinitely.

/**
 * Whether the still is captured from the decoder's frame pipeline.
 *
 * Off for now, to measure. The tap is installed as a video effect, which puts an extra GL pass
 * in front of every frame for the whole run in order to capture one frame per segment -
 * the one cost in the app that scales with frame count rather than with handoffs. Leaving it
 * off takes the effect pipeline out entirely and the player renders straight to the surface.
 *
 * Everything downstream copes: with no still to seal, the handoff degrades to the plain cut it
 * already falls back to whenever a tap comes up empty. The outgoing player is paused on its
 * last frame before the swap, so that frame holds the screen and the surfaces change under it
 * rather than over a frozen image. The timing line reads captureMs=0 and the fade never runs,
 * which is what a run with no stills should look like.
 *
 * Flip it back to true for the dissolve; nothing else changes.
 */
private const val CAPTURE_STILLS = true

/**
 * Which path the run takes its stills through.
 *
 * The always-on tap is still the default, because on this device the other two are not better
 * once you count what each actually costs. Measured, 1080p SDR, same build:
 *
 * - [StillCaptureMode.ALWAYS_ON_TAP] - capture 96-119ms, still correct, but the pipeline is in
 *   front of every frame for the whole run.
 * - [StillCaptureMode.SCHEDULED_EFFECT] - does not produce a still at all: installing the effect
 *   stops the player rather than hitching it, so the target is never reached. See the mode.
 * - [StillCaptureMode.METADATA_RETRIEVER] - capture 2.1-3.1s, still correct, no pipeline at all,
 *   but the outgoing clip is frozen for those 2-3s at every handoff.
 *
 * So the retriever trades per-frame overhead for a visible multi-second freeze at every handoff,
 * which is a bad deal for something meant to be looked at from across a room. Flip this to
 * [StillCaptureMode.METADATA_RETRIEVER] to try it anyway - it is the only one of the three that
 * both works and keeps the pipeline out of the render path.
 */
private val CAPTURE_MODE = StillCaptureMode.ALWAYS_ON_TAP

/**
 * How far before the segment ends the scheduled capture aims.
 *
 * Just short of the end, so the still is of the last thing the clip was showing, but short enough
 * that the frame has landed by the time the loop asks for it - the loop reaches the handoff at
 * [PLAY_DURATION_MS], and aiming the capture at exactly that point would race the frame that is
 * about to be presented.
 */
private const val STILL_TARGET_SLACK_MS = 250L

private const val STILL_TARGET_MS = PLAY_DURATION_MS - STILL_TARGET_SLACK_MS

/**
 * How long the retriever path is given before the handoff gives up on it.
 *
 * Generous, because unlike the other two it has to open the file and seek it, and the catalogue
 * is network-hosted. Only used by [StillCaptureMode.METADATA_RETRIEVER].
 */
private const val RETRIEVER_TIMEOUT_MS = 5_000L

/**
 * Whether the app starts playback by itself.
 *
 * Off for now so the quality picker can be worked on without playback cutting in mid-tweak. With
 * it off the countdown never starts, the button reads plain "Start playback", and the only way
 * into playback is pressing it - which is also the honest behaviour for a user who has not
 * chosen anything yet. Flip it back to true to get the countdown; nothing else changes.
 */
private const val AUTO_START = false

/**
 * How long the startup countdown runs before playback begins on its own.
 *
 * Ten seconds rather than five: the picker is the only interaction the app offers, and five was
 * not long enough to open the quality list and read it before the screen went away underneath.
 */
private const val START_COUNTDOWN_SECONDS = 10

/**
 * One tick of the startup countdown.
 *
 * A whole second because it is what the button label shows. Distinct from [COUNTDOWN_TICK_MS],
 * which is the in-segment tick and has to be far finer than this.
 */
private const val START_COUNTDOWN_TICK_MS = 1_000L

/** How often the memory readout is refreshed. PSS is a binder call, so it is not free. */
private const val MEMORY_POLL_MS = 2_000L

/**
 * How long after a segment starts the next clip is loaded and primed.
 *
 * What this buys is headroom: whatever is loaded here then has until the segment ends to buffer,
 * and the handoff cannot begin until the incoming player has actually rendered a frame. At ten
 * seconds into a fifteen second segment that is five seconds of headroom - the same as the ten
 * and five it replaced, which is deliberate. Once the preload has had that long there is nothing
 * for the handoff to wait for at 1080p, and headroom is what absorbs a slow network rather than
 * anything that makes the cut happen sooner.
 *
 * "Halfway through" used to be the rule of thumb, and it stops being a useful one now that the
 * two numbers are independent: as a fraction it would drift earlier with every lengthening of the
 * segment, which costs nothing but says nothing about whether there is room left to buffer in.
 */
private const val PRELOAD_AFTER_MS = 10_000L

/**
 * Pause between sealing the still and swapping the surfaces, so the still is composited at
 * full opacity before anything behind it changes. One or two frames is enough.
 */
private const val STILL_LEAD_IN_MS = 150L

/**
 * How long to wait for the opening clip to render its first frame before clearing the loader
 * anyway. Generous, because a cold start is a network fetch and a decoder spin-up; it is only
 * there so a clip that never renders cannot leave the spinner up for good.
 */
private const val STARTUP_FRAME_TIMEOUT_MS = 15_000L

private const val COUNTDOWN_TICK_MS = 100L
