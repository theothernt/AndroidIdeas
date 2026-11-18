package com.neilturner.fauxfade.feature.timedeffect

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
import com.neilturner.fauxfade.core.media.AerialAsset
import com.neilturner.fauxfade.core.media.Quality
import com.neilturner.fauxfade.core.media.VideoCatalog
import com.neilturner.fauxfade.core.media.VideoItem
import com.neilturner.fauxfade.core.player.AppPhase
import com.neilturner.fauxfade.core.player.PlayerSlot
import com.neilturner.fauxfade.core.player.StillPhase
import com.neilturner.fauxfade.core.player.createPlayerSlots
import com.neilturner.fauxfade.core.stats.FrameStats
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
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
 * The timed-effect feature: the capture pipeline is built a fixed lead time before each frame.
 *
 * Two players hand off through a still read out of the outgoing player. Unlike Tap, no effect is
 * installed for the whole run - the effect goes in only for the run-up to a capture and is removed
 * immediately after. The mechanism works but on this device installing the effect stops the
 * player, so every handoff degrades to a cut.
 */
@OptIn(UnstableApi::class)
class TimedEffectViewModel(
	application: Application,
) : AndroidViewModel(application) {
	private lateinit var players: Map<PlayerSlot, ExoPlayer>
	val slots: List<PlayerSlot> = PlayerSlot.entries.toList()
	private lateinit var scheduled: Map<PlayerSlot, ScheduledFrameCapture>
	private val frameStats = PlayerSlot.entries.associateWith { FrameStats(it.name) }

	private val _phase = MutableStateFlow(AppPhase.PICKING)
	val phase: StateFlow<AppPhase> = _phase.asStateFlow()

	private val _playingQuality = MutableStateFlow(Quality.FHD_SDR)
	val playingQuality: StateFlow<Quality> = _playingQuality.asStateFlow()

	private val assetsLoaded = CompletableDeferred<List<AerialAsset>>()
	private lateinit var order: List<VideoItem>

	private val _foreground = MutableStateFlow(PlayerSlot.PRIMARY)
	val foreground: StateFlow<PlayerSlot> = _foreground.asStateFlow()

	private val _swapping = MutableStateFlow(false)
	val swapping: StateFlow<Boolean> = _swapping.asStateFlow()

	private val _stillFrame = MutableStateFlow<Bitmap?>(null)
	val stillFrame: StateFlow<Bitmap?> = _stillFrame.asStateFlow()

	private val _stillPhase = MutableStateFlow(StillPhase.HIDDEN)
	val stillPhase: StateFlow<StillPhase> = _stillPhase.asStateFlow()

	private val _remainingSeconds = MutableStateFlow(SECONDS_PER_SEGMENT)
	val remainingSeconds: StateFlow<Int> = _remainingSeconds.asStateFlow()

	private val _loading = MutableStateFlow(true)
	val loading: StateFlow<Boolean> = _loading.asStateFlow()

	private val _memoryMb = MutableStateFlow(0L)
	val memoryMb: StateFlow<Long> = _memoryMb.asStateFlow()

	private var loopJob: Job? = null
	private var memoryJob: Job? = null
	private var starting = false
	private var live = false

	init {
		viewModelScope.launch { assetsLoaded.complete(VideoCatalog.load(application)) }
	}

	fun playerFor(slot: PlayerSlot): ExoPlayer = players.getValue(slot)

	fun startWith(quality: Quality) {
		viewModelScope.launch { beginPlayback(quality) }
	}

	private suspend fun beginPlayback(quality: Quality) {
		if (starting || _phase.value == AppPhase.PLAYING) return
		starting = true
		try {
			order = VideoCatalog.pick(assetsLoaded.await(), quality)
			if (order.isEmpty()) {
				Log.e(TAG, "timed: no clips available for $quality, staying on the picker")
				return
			}
			players = createPlayerSlots(getApplication(), quality, slots)
			live = true
			scheduled = players.keys.associateWith { ScheduledFrameCapture(players.getValue(it)) }
			players.forEach { (slot, player) -> player.addAnalyticsListener(frameStats.getValue(slot)) }
			_foreground.value = PlayerSlot.PRIMARY
			_swapping.value = false
			_stillFrame.value = null
			_stillPhase.value = StillPhase.HIDDEN
			_playingQuality.value = quality
			Log.i(TAG, "startup: $quality, ${order.size} clips")
			_phase.value = AppPhase.PLAYING
		} finally {
			starting = false
		}
	}

	fun start() {
		if (loopJob != null || !live) return
		Log.i(TAG, "timed: mode=SCHEDULED_EFFECT stills=$CAPTURE_STILLS")
		loopJob = viewModelScope.launch { runPlaylistLoop() }
		startMemoryWatch()
	}

	private fun startMemoryWatch() {
		if (!BuildConfig.DEBUG || memoryJob != null) return
		memoryJob =
			viewModelScope.launch {
				while (isActive) {
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
		if (!live) return
		loopJob?.cancel()
		loopJob = null
		memoryJob?.cancel()
		memoryJob = null
		players.values.forEach { it.pause() }
	}

	fun stopRun() {
		loopJob?.cancel()
		loopJob = null
		memoryJob?.cancel()
		memoryJob = null
		releasePlayers()
		_phase.value = AppPhase.PICKING
		Log.i(TAG, "stopped: timed, back to the picker")
	}

	private fun releasePlayers() {
		if (!live) return
		live = false
		if (::scheduled.isInitialized) scheduled.values.forEach { it.release() }
		if (::players.isInitialized) players.values.forEach { it.release() }
	}

	private suspend fun CoroutineScope.runPlaylistLoop() {
		var onScreen = PlayerSlot.PRIMARY
		var currentIndex = 0
		_loading.value = true
		Log.i(TAG, "playlist: ${order.map { it.uri.substringAfterLast('/') }}")

		val opening = players.getValue(onScreen)
		val opened = opening.firstRenderSignal()
		val startedAt = TimeSource.Monotonic.markNow()
		opening.playFromStart(order[currentIndex])
		if (withTimeoutOrNull(STARTUP_FRAME_TIMEOUT_MS) { opened.await() } == null) {
			Log.w(TAG, "startup: $onScreen had no first frame in ${STARTUP_FRAME_TIMEOUT_MS}ms")
		}
		Log.i(TAG, "startup: first frame after ${startedAt.elapsedNow().inWholeMilliseconds}ms")
		_loading.value = false

		while (coroutineContext.isActive) {
			val incoming = onScreen.opposite()
			val nextIndex = (currentIndex + 1) % order.size

			// The effect has to be installed while this segment is still playing, a fixed lead time
			// before the target - asking at the handoff would be asking for a position already passed.
			val armedStill = armScheduledStill(onScreen)
			tickSegment { players.getValue(incoming).preload(order[nextIndex]) }

			val outgoing = players.getValue(onScreen)
			val t0 = TimeSource.Monotonic.markNow()
			val still = captureStill(onScreen, armedStill)
			val afterCaptureMs = t0.elapsedNow().inWholeMilliseconds
			outgoing.pause()
			Log.i(TAG, "pause: outgoing=$onScreen index=$currentIndex positionMs=${outgoing.currentPosition}")
			meanRgb(still)?.let { Log.i(TAG, "still: internal $it") }

			if (still != null) {
				_stillFrame.value = still
				_stillPhase.value = StillPhase.SEALED
				delay(STILL_LEAD_IN_MS)
			} else {
				_stillFrame.value = null
				_stillPhase.value = StillPhase.HIDDEN
			}

			val incomingPlayer = players.getValue(incoming)
			val firstFrame = incomingPlayer.firstRenderSignal()
			incomingPlayer.play()
			_swapping.value = true
			val swapBudget = _playingQuality.value.swapTimeoutMs
			val rendered = withTimeoutOrNull(swapBudget) { firstFrame.await() }
			if (rendered == null) Log.w(TAG, "swap: $incoming had no rendered frame in ${swapBudget}ms")
			val afterSwapMs = t0.elapsedNow().inWholeMilliseconds

			_foreground.value = incoming
			_swapping.value = false
			if (still != null) _stillPhase.value = StillPhase.FADING
			Log.i(TAG, "swap: foreground -> $incoming incomingIndex=$nextIndex firstFrame=${rendered == true}")
			Log.i(TAG, "timing: captureMs=$afterCaptureMs sealedMs=${afterSwapMs - afterCaptureMs} handoffMs=$afterSwapMs")
			Log.i(TAG, "frames: " + frameStats.getValue(onScreen).segment(outgoing, PLAY_DURATION_MS) { 0 })

			onScreen = incoming
			currentIndex = nextIndex
		}
	}

	private fun armScheduledStill(slot: PlayerSlot): CompletableDeferred<Bitmap?>? {
		if (!CAPTURE_STILLS) return null
		return scheduled.getValue(slot).captureAt(STILL_TARGET_MS)
	}

	private suspend fun captureStill(
		slot: PlayerSlot,
		armed: CompletableDeferred<Bitmap?>?,
	): Bitmap? {
		if (!CAPTURE_STILLS) return null
		val capture = scheduled.getValue(slot)
		return try {
			withTimeoutOrNull(_playingQuality.value.tapTimeoutMs) { armed?.await() }
		} finally {
			// Drop a half-built pipeline even when the wait times out.
			capture.cancelPending()
		}
	}

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
		releasePlayers()
	}
}

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

private const val PLAY_DURATION_MS = 15_000L
private const val SECONDS_PER_SEGMENT = (PLAY_DURATION_MS / 1000).toInt()

private fun secondsLeft(remainingMs: Long): Int = ceil(remainingMs / 1000.0).toInt().coerceAtLeast(0)

private const val CAPTURE_STILLS = true
private const val PRELOAD_AFTER_MS = 10_000L
private const val STILL_LEAD_IN_MS = 150L
private const val STILL_TARGET_SLACK_MS = 250L
private const val STILL_TARGET_MS = PLAY_DURATION_MS - STILL_TARGET_SLACK_MS
private const val STARTUP_FRAME_TIMEOUT_MS = 15_000L
private const val COUNTDOWN_TICK_MS = 100L
private const val MEMORY_POLL_MS = 2_000L
