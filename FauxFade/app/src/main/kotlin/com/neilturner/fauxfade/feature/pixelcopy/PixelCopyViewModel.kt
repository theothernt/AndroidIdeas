package com.neilturner.fauxfade.feature.pixelcopy

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.os.Debug
import android.util.Log
import androidx.annotation.OptIn
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import com.neilturner.fauxfade.BuildConfig
import com.neilturner.fauxfade.core.media.AerialAsset
import com.neilturner.fauxfade.core.media.Quality
import com.neilturner.fauxfade.core.media.VideoCatalog
import com.neilturner.fauxfade.core.media.VideoItem
import com.neilturner.fauxfade.core.stats.FrameStats
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
 * One player, and the still hiding the re-open rather than a surface swap.
 *
 * The reason this feature exists rather than the two-player crossfade: at 4K the device will not
 * give a second decoder, so the two-player version is simply unavailable there. But a still does
 * not strictly need two surfaces to hide something - it needs to cover the moment the picture
 * stops being continuous, and with one player that moment is the clip changing rather than the
 * surfaces swapping.
 *
 * So the sequence is the same shape with one surface instead of two. Clip N plays, is paused on its
 * last frame, and that frame is copied out of the surface. The next clip is then started on the
 * same player, and once it has a frame the still is faded off it. The viewer sees the last frame of
 * one clip dissolve into the first of the next - which is the same thing the two-player version
 * produces, with one decoder instead of two.
 *
 * This is the only approach that can do it, and the reason is the ordering. Every other still in
 * the app reads frames out of the player, and a paused player produces none, so they all have to
 * capture *before* they pause. The player here is paused and has to re-open on a new item before it
 * will produce anything at all - a decoder cannot be read in that state, but a surface can. That is
 * the whole of [PixelCopyCapture]'s claim, and it is why this view model has one player where the
 * others have two.
 */
@OptIn(UnstableApi::class)
class PixelCopyViewModel(
	application: Application,
) : AndroidViewModel(application) {
	/**
	 * Built when playback begins rather than here.
	 *
	 * Lateinit on purpose: the buffer cap depends on the chosen quality, so it cannot exist before
	 * the picker has been answered. Touching this before then throws
	 * UninitializedPropertyAccessException, which is a much easier failure to trace than a silently
	 * empty player.
	 */
	private lateinit var player: ExoPlayer

	private val _phase = MutableStateFlow(Phase.PICKING)
	val phase: StateFlow<Phase> = _phase.asStateFlow()

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
	 * Held behind a deferred rather than a plain field because playback can begin before the parse
	 * has finished - the button skips the whole countdown, and nothing stops a skip landing in the
	 * first few milliseconds.
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
	 */
	private val _playingQuality = MutableStateFlow(Quality.FHD_SDR)
	val playingQuality: StateFlow<Quality> = _playingQuality.asStateFlow()

	/**
	 * The shape a still is captured in, which follows the quality rather than being configured.
	 *
	 * Reading high dynamic range into an 8-bit bitmap does not tone-map it - it reads the values as
	 * sRGB - so the still comes out with a magenta cast and underexposed, which under a crossfade is
	 * a flash of the wrong colour every segment. Keeping the range in a half-float and letting the
	 * framework convert at draw time is the fix. It follows the quality because the SDR qualities
	 * gain nothing from it and would pay for the float destination.
	 */
	private val stillTarget: PixelCopyCapture.CopyTarget
		get() = if (_playingQuality.value.isHdr) PixelCopyCapture.CopyTarget.HDR else PixelCopyCapture.CopyTarget.SDR

	/**
	 * How the loop reaches the surface. Lent by the player screen before [start].
	 *
	 * Null means no view has attached, in which case the handoff cuts. Held as a function rather
	 * than as the view itself - a view model that keeps a `View` keeps it alive past its own view.
	 */
	private var grabber: SurfaceGrabber? = null

	/** Lends the loop a way to reach the surface. Called by the player screen on composition. */
	internal fun attachGrabber(grabber: SurfaceGrabber) {
		this.grabber = grabber
	}

	/**
	 * What the video renderer had to drop over the segment that has just finished.
	 *
	 * Measurement only - nothing here changes behaviour.
	 */
	private val frameStats = FrameStats("PRIMARY")

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
	 * second keeps that finer tick from turning into ten recompositions a second to move one digit.
	 */
	val remainingSeconds: StateFlow<Int> = _remainingSeconds.asStateFlow()

	/**
	 * True until the opening clip has a frame on screen. The surface is empty before that and the
	 * window behind it is black, so there is nothing worth showing yet.
	 */
	private val _loading = MutableStateFlow(true)
	val loading: StateFlow<Boolean> = _loading.asStateFlow()

	private val _memoryMb = MutableStateFlow(0L)
	val memoryMb: StateFlow<Long> = _memoryMb.asStateFlow()

	private var loopJob: Job? = null
	private var memoryJob: Job? = null
	private var countdownJob: Job? = null

	/** True while [beginPlayback] is building the player, so a second caller cannot join in. */
	private var starting = false

	/**
	 * True between [beginPlayback] building the player and [onCleared] releasing it.
	 *
	 * Both [stop] and [onCleared] need the same answer before touching anything: is there a player
	 * right now? The shared path's view model asks it with a `::players.isInitialized` check, which
	 * this class cannot - it also exposes `player()` for the screen to attach its surface to, and a
	 * callable reference to `player` stops being able to say which of the two it meant.
	 *
	 * It is also the better question. Leaving a feature clears its view model store, and whether that
	 * lands before or after the screen's own disposal is a detail of how Compose tears a subtree down
	 * rather than something this class should assume. Whichever way round it goes, a view model that
	 * has already released its player must not be asked to pause it.
	 */
	private var live = false

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

	fun player(): ExoPlayer = player

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
	 * Locks the choice in: draws the ten clips, builds the player sized for them, and moves to the
	 * playing phase.
	 *
	 * The phase flips last and is the only thing the composables key off, so the player and the
	 * order are always in place before anything can ask for them.
	 */
	private suspend fun beginPlayback() {
		// Reachable from two places at once - the button, and the countdown running out - which
		// collide exactly when a tap lands on the same second the timer expires. Both callers run
		// on the main dispatcher, so a plain flag is enough to keep them apart.
		if (starting || _phase.value == Phase.PLAYING) return
		starting = true
		try {
			val quality = _quality.value
			order = VideoCatalog.pick(assetsLoaded.await(), quality)
			if (order.isEmpty()) {
				// Nothing to play. Staying on the picker is the least bad outcome - the alternative
				// is a player screen that sits on a spinner forever.
				Log.e(TAG, "startup: no clips available for $quality, staying on the picker")
				return
			}
			player = createPlayer(getApplication(), quality)
			player.addAnalyticsListener(frameStats)
			// Only once there is something to act on: [stop] and [onCleared] read this rather than
			// asking the field, and a release is never reachable before it.
			live = true
			_playingQuality.value = quality
			Log.i(TAG, "startup: $quality, ${order.size} clips")
			_phase.value = Phase.PLAYING
		} finally {
			starting = false
			// Cancelled here rather than on entry: this is usually called *from* the countdown job,
			// and cancelling that job before the work is done would abort this function along with
			// it.
			countdownJob?.cancel()
			countdownJob = null
		}
	}

	fun start() {
		if (loopJob != null) return
		// No effect is ever installed. This is the whole argument for the approach: the render path
		// is left completely alone for the whole segment, where the tap puts a frame processing
		// pipeline in front of every frame of every clip in order to catch one.
		Log.i(TAG, "single: one player, crossfading via PixelCopy")
		loopJob = viewModelScope.launch { runPixelCopyLoop() }
		startMemoryWatch()
	}

	/**
	 * Polls the process's PSS. Worth watching here because the still is a full-resolution bitmap
	 * allocated on every handoff - if those are not being collected promptly the figure climbs a
	 * little every segment.
	 *
	 * Debug builds only. A release build has nothing to show the figure in, and a binder call every
	 * two seconds is not worth paying for.
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

	/**
	 * Stops the loop and leaves the player holding its frame, without releasing it.
	 *
	 * Cancelling a job that is already cancelled or null is harmless, so only the pause needs
	 * asking about - and it does, because a released player must not be told to do anything.
	 */
	fun stop() {
		loopJob?.cancel()
		loopJob = null
		memoryJob?.cancel()
		memoryJob = null
		if (live) player.pause()
	}

	/**
	 * The loop: play a segment, pause on the last frame, copy it, re-open on the next clip under
	 * the copy, then fade the copy off the new clip's first frame.
	 *
	 * No preload step. Priming the next clip is the other player's job in the two-player version,
	 * and there isn't one - the same player has to stop before it can prepare the next item, so
	 * there is nothing to prime early. What that costs is headroom: the handoff waits on the codec
	 * re-initialising, where the two-player version has had a whole segment to get the second codec
	 * up. What it buys is that the two codecs never exist at once, which is the entire reason this
	 * works at 4K.
	 */
	private suspend fun runPixelCopyLoop() {
		var currentIndex = 0
		_loading.value = true
		Log.i(TAG, "playlist: ${order.map { it.uri.substringAfterLast('/') }}")

		val opened = player.firstRenderSignal()
		val startedAt = TimeSource.Monotonic.markNow()
		player.playFromStart(order[currentIndex])
		if (withTimeoutOrNull(STARTUP_FRAME_TIMEOUT_MS) { opened.await() } == null) {
			Log.w(TAG, "startup: $currentIndex had no first frame in ${STARTUP_FRAME_TIMEOUT_MS}ms")
		}
		Log.i(TAG, "startup: first frame after ${startedAt.elapsedNow().inWholeMilliseconds}ms")
		_loading.value = false

		while (coroutineContext.isActive) {
			val nextIndex = (currentIndex + 1) % order.size
			tickSegment()

			val t0 = TimeSource.Monotonic.markNow()
			// Pause first, then copy, which is only possible because the still comes off the surface
			// rather than off the player. The surface keeps this frame and stays visible throughout,
			// so nothing on screen changes during the copy.
			player.pause()
			val still = withTimeoutOrNull(PIXEL_COPY_TIMEOUT_MS) { grabber?.grab(stillTarget) }
			val afterCaptureMs = t0.elapsedNow().inWholeMilliseconds

			if (still != null) {
				_stillFrame.value = still
				_stillPhase.value = StillPhase.SEALED
				// The still has to be composited at full opacity before the player drops the frame
				// it is standing in for.
				delay(STILL_LEAD_IN_MS)
			} else {
				_stillFrame.value = null
				_stillPhase.value = StillPhase.HIDDEN
			}

			// Now re-open on the next clip. This is the moment the still is covering: the codec
			// re-initialises and the surface has nothing to show until the new clip's first frame.
			val firstFrame = player.firstRenderSignal()
			player.playFromStart(order[nextIndex])
			val rendered = withTimeoutOrNull(_playingQuality.value.swapTimeoutMs) { firstFrame.await() }
			if (rendered == null) {
				Log.w(TAG, "single: ${order[nextIndex].uri.substringAfterLast('/')} had no first frame")
			}

			if (still != null) _stillPhase.value = StillPhase.FADING

			// No tap is installed, so there is no pipeline to count frames through and the carried
			// figure is legitimately zero rather than a measurement of something broken.
			Log.i(TAG, "frames: ${frameStats.segment(player, PLAY_DURATION_MS) { 0L }}")
			Log.i(
				TAG,
				"timing: captureMs=$afterCaptureMs " +
					"handoffMs=${t0.elapsedNow().inWholeMilliseconds} firstFrame=${rendered == true}",
			)

			currentIndex = nextIndex
		}
	}

	/**
	 * Completes the first time this player renders a frame, and detaches its listener when awaited
	 * or abandoned, so a long-lived listener does not pile up on the player.
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
	 * Runs the countdown for one segment.
	 *
	 * The countdown is published as the time left in the whole segment rather than as a timer per
	 * phase, so it reads 15 down to 0. There is no preload point here - see [runPixelCopyLoop] -
	 * which is the one thing that distinguishes this from the same function in the two-player loop.
	 */
	private suspend fun tickSegment() {
		val start = TimeSource.Monotonic.markNow()
		while (true) {
			val elapsed = start.elapsedNow().inWholeMilliseconds
			if (elapsed >= PLAY_DURATION_MS) break
			_remainingSeconds.value = secondsLeft(PLAY_DURATION_MS - elapsed)
			delay(COUNTDOWN_TICK_MS)
		}
		_remainingSeconds.value = 0
	}

	override fun onCleared() {
		loopJob?.cancel()
		memoryJob?.cancel()
		countdownJob?.cancel()
		// Down before the release, so anything running after this sees the player as gone. The
		// screen's own disposal may land on either side of this, and that is not something worth
		// pinning down: see [live]. False from the picker too, where no player was ever built.
		if (live) {
			live = false
			player.release()
		}
	}
}

/**
 * How the still overlay is being presented.
 *
 * Three states, and deliberately not the four the two-player version carries: [DISSOLVING] is
 * reserved for an approach that ramps a still *up* over video that is still playing underneath it,
 * and nothing here does that. The still in this feature always arrives by snapping - the player is
 * already paused on the frame it is a copy of, so there is nothing behind it to dissolve against.
 *
 * Sealing and fading are separate states because the still has to be fully opaque *before* the
 * player re-opens on the next clip, and stay opaque until that clip has something to show. Fading
 * it early means most of the fade runs over a surface that is still empty.
 */
enum class StillPhase {
	/** Not on screen. */
	HIDDEN,

	/** On screen at full opacity, held. */
	SEALED,

	/** Fading out over the video now underneath. */
	FADING,
}

/** Which half of this feature is up: its own picker, or its loop. */
enum class Phase {
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

/**
 * Builds the one player this feature needs.
 *
 * Forked from the shared `ui/player` builder, and deliberately diverged: that one takes a list of
 * slots because it builds one player per slot, and this feature has exactly one by construction.
 *
 * The buffer control is the same shape for the same reason the shared one is - see [bufferLimit].
 */
@OptIn(UnstableApi::class)
private fun createPlayer(
	context: Context,
	quality: Quality,
): ExoPlayer = ExoPlayer.Builder(context).setLoadControl(bufferLimit(quality)).build()

/**
 * Caps how much compressed media the loader holds.
 *
 * The default buffers by duration alone - up to 50s of a 2K clip. [DefaultLoadControl
 * .Builder.setTargetBufferBytes] is ignored unless [DefaultLoadControl.Builder
 * .setPrioritizeTimeOverSizeThresholds] is turned off, which is the part that actually makes it
 * bite.
 *
 * The byte cap comes from the quality because a 4K stream blows through a 1080p-sized budget in a
 * couple of seconds. There is no preload to protect here the way there is in the two-player
 * version, but a starved buffer still shows up as a stall, and the durations stay generous enough
 * that the next clip starts the moment the still comes down.
 */
@OptIn(UnstableApi::class)
private fun bufferLimit(quality: Quality) =
	DefaultLoadControl
		.Builder()
		.setBufferDurationsMs(
			MIN_BUFFER_MS,
			MAX_BUFFER_MS,
			BUFFER_FOR_PLAYBACK_MS,
			BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
		).setTargetBufferBytes(quality.bufferBytes)
		.setPrioritizeTimeOverSizeThresholds(false)
		.build()

/** Rounds a countdown up, so the readout holds each number for its whole second. */
private fun secondsLeft(remainingMs: Long): Int = ceil(remainingMs / 1000.0).toInt().coerceAtLeast(0)

/** How long each video plays before the handoff to the next clip. */
private const val PLAY_DURATION_MS = 15_000L

/** Seconds in a segment: what the countdown reads from, and the resolution it is published at. */
private const val SECONDS_PER_SEGMENT = (PLAY_DURATION_MS / 1000).toInt()

/**
 * How long a surface copy is given before the handoff gives up on it.
 *
 * Much longer than it looks like it should be, because the cost is not the copy. A 1080p copy on
 * this device measures ~100ms inside a two-player handoff and ~400ms with one, but the same copy at
 * 4K measures ~1.1s - while the destination is 1920x1080 in every one of those cases. So what is
 * being waited on is the display pipeline finding a moment to snapshot a layer that is being
 * written at 3840x2160, not the transfer.
 *
 * The first budget tried here was 500ms, which silently turned every 4K handoff into a cut: the
 * copy was still in flight when the budget ran out, so the still was null and nothing said so.
 */
private const val PIXEL_COPY_TIMEOUT_MS = 2_000L

/**
 * Pause between sealing the still and re-opening the player, so the still is composited at full
 * opacity before anything behind it changes. One or two frames is enough.
 */
private const val STILL_LEAD_IN_MS = 150L

/**
 * How long to wait for the opening clip to render its first frame before clearing the loader
 * anyway. Generous, because a cold start is a network fetch and a decoder spin-up; it is only there
 * so a clip that never renders cannot leave the spinner up for good.
 */
private const val STARTUP_FRAME_TIMEOUT_MS = 15_000L

/** One tick of the segment countdown. */
private const val COUNTDOWN_TICK_MS = 100L

/**
 * Whether the app starts playback by itself.
 *
 * Off so the quality picker can be worked on without playback cutting in mid-tweak. With it off the
 * countdown never starts, the button reads plain "Start playback", and the only way into playback is
 * pressing it. Flip it back to true to get the countdown; nothing else changes.
 */
private const val AUTO_START = false

/** How long the startup countdown runs before playback begins on its own. */
private const val START_COUNTDOWN_SECONDS = 10

/** One tick of the startup countdown. A whole second, because it is what the button label shows. */
private const val START_COUNTDOWN_TICK_MS = 1_000L

/** How often the memory readout is refreshed. PSS is a binder call, so it is not free. */
private const val MEMORY_POLL_MS = 2_000L

/**
 * Load control limits. The defaults buffer up to 50s of a 2K clip per player and ignore the byte
 * target, which is far more than a loop that plays a handful of seconds at a time needs.
 */
private const val MIN_BUFFER_MS = 3_000
private const val MAX_BUFFER_MS = 10_000
private const val BUFFER_FOR_PLAYBACK_MS = 2_500
private const val BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 2_000

/** Logcat tag for this feature. Per-feature so a logcat filter can isolate one run of one approach. */
private const val TAG = "FauxFade.PixelCopy"
