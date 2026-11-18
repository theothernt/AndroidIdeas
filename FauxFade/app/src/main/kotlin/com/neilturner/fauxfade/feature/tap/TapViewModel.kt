package com.neilturner.fauxfade.feature.tap

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
 * The always-on tap feature: one effect per player, installed for the whole run.
 *
 * Two players hand off through a still read out of the outgoing player's frame pipeline. The tap
 * is the cheapest capture at the moment of the swap and the most expensive across a run, because
 * the effect stays in front of every frame. It is what made 4K miss its handoff budget.
 */
@OptIn(UnstableApi::class)
class TapViewModel(
	application: Application,
) : AndroidViewModel(application) {
	private lateinit var players: Map<PlayerSlot, ExoPlayer>
	val slots: List<PlayerSlot> = PlayerSlot.entries.toList()
	private val taps = PlayerSlot.entries.associateWith { StillTapEffect() }
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

	/** Starts a run with the quality already chosen on the menu. */
	fun startWith(quality: Quality) {
		viewModelScope.launch { beginPlayback(quality) }
	}

	private suspend fun beginPlayback(quality: Quality) {
		if (starting || _phase.value == AppPhase.PLAYING) return
		starting = true
		try {
			order = VideoCatalog.pick(assetsLoaded.await(), quality)
			if (order.isEmpty()) {
				Log.e(TAG, "tap: no clips available for $quality, staying on the picker")
				return
			}
			players = createPlayerSlots(getApplication(), quality, PlayerSlot.entries.toList())
			live = true
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
		// The effect goes in before playback starts rather than being rebuilt under a running
		// player. This is the one cost that scales with frame count rather than handoffs.
		taps.forEach { (slot, tap) -> players[slot]?.setVideoEffects(listOf(tap)) }
		Log.i(TAG, "tap: mode=ALWAYS_ON_TAP stills=$CAPTURE_STILLS")
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
		Log.i(TAG, "stopped: tap, back to the picker")
	}

	private fun releasePlayers() {
		if (!live) return
		live = false
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
			tickSegment { players.getValue(incoming).preload(order[nextIndex]) }

			val outgoing = players.getValue(onScreen)
			val t0 = TimeSource.Monotonic.markNow()
			val still = captureStill(onScreen)
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
			Log.i(
				TAG,
				"frames: " +
					frameStats
						.getValue(onScreen)
						.segment(players.getValue(onScreen), PLAY_DURATION_MS) { taps.getValue(onScreen).framesSeen() },
			)

			onScreen = incoming
			currentIndex = nextIndex
		}
	}

	private suspend fun captureStill(slot: PlayerSlot): Bitmap? {
		if (!CAPTURE_STILLS) return null
		return grabStill(slot, taps.getValue(slot).arm())
	}

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
private const val STARTUP_FRAME_TIMEOUT_MS = 15_000L
private const val COUNTDOWN_TICK_MS = 100L
private const val MEMORY_POLL_MS = 2_000L
