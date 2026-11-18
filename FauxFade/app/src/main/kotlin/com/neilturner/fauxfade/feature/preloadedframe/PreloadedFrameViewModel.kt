package com.neilturner.fauxfade.feature.preloadedframe

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
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
 * The preloaded-frame feature: one player, and the still is the *next* clip's first frame.
 *
 * The read starts part way through the outgoing segment, while nothing on screen changes, and the
 * incoming frame rises from zero opacity over the still-playing clip. Only once it is opaque does
 * the player stop and re-open underneath the cover - so no second decoder is ever required.
 */
@OptIn(UnstableApi::class)
class PreloadedFrameViewModel(
	application: Application,
) : AndroidViewModel(application) {
	private lateinit var player: ExoPlayer
	private val slot = PlayerSlot.PRIMARY
	val slots: List<PlayerSlot> = listOf(slot)
	private lateinit var retriever: RetrieverFrameCapture
	private val frameStats = FrameStats(slot.name)

	private val _phase = MutableStateFlow(AppPhase.PICKING)
	val phase: StateFlow<AppPhase> = _phase.asStateFlow()

	private val _playingQuality = MutableStateFlow(Quality.FHD_SDR)
	val playingQuality: StateFlow<Quality> = _playingQuality.asStateFlow()

	private val assetsLoaded = CompletableDeferred<List<AerialAsset>>()
	private lateinit var order: List<VideoItem>

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

	fun playerFor(playerSlot: PlayerSlot): ExoPlayer = player

	fun startWith(quality: Quality) {
		viewModelScope.launch { beginPlayback(quality) }
	}

	private suspend fun beginPlayback(quality: Quality) {
		if (starting || _phase.value == AppPhase.PLAYING) return
		starting = true
		try {
			order = VideoCatalog.pick(assetsLoaded.await(), quality)
			if (order.isEmpty()) {
				Log.e(TAG, "preload: no clips available for $quality, staying on the picker")
				return
			}
			player = createPlayerSlots(getApplication(), quality, slots).getValue(slot)
			live = true
			retriever = RetrieverFrameCapture()
			player.addAnalyticsListener(frameStats)
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
		Log.i(TAG, "preload: mode=PRELOADED_FRAME stills=$CAPTURE_STILLS")
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
		player.pause()
	}

	fun stopRun() {
		loopJob?.cancel()
		loopJob = null
		memoryJob?.cancel()
		memoryJob = null
		releasePlayer()
		_phase.value = AppPhase.PICKING
		Log.i(TAG, "stopped: preload, back to the picker")
	}

	private fun releasePlayer() {
		if (!live) return
		live = false
		if (::player.isInitialized) player.release()
	}

	private suspend fun runPlaylistLoop() =
		coroutineScope {
			var currentIndex = 0
			_loading.value = true
			Log.i(TAG, "playlist: ${order.map { it.uri.substringAfterLast('/') }}")
			Log.i(TAG, "preload: ${order.size} clips, reading each first frame ahead of its segment")

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
				val next = order[nextIndex]

				var pending: Deferred<Bitmap?>? = null
				tickSegment { pending = async { retriever.extractAt(next.uri, 0L) } }

				val t0 = TimeSource.Monotonic.markNow()
				val extraction = pending ?: async { retriever.extractAt(next.uri, 0L) }
				val still = withTimeoutOrNull(PRELOAD_TIMEOUT_MS) { extraction.await() }
				val extractMs = t0.elapsedNow().inWholeMilliseconds

				if (still == null) {
					val name = next.uri.substringAfterLast('/')
					Log.w(TAG, "preload: no frame for $name after ${extractMs}ms, cutting instead")
					_stillFrame.value = null
					_stillPhase.value = StillPhase.HIDDEN
					player.playFromStart(next)
					Log.i(TAG, "frames: " + frameStats.segment(player, PLAY_DURATION_MS) { 0 })
					Log.i(TAG, "timing: extractMs=$extractMs still=none handoffMs=${t0.elapsedNow().inWholeMilliseconds}")
				} else {
					_stillFrame.value = still
					_stillPhase.value = StillPhase.DISSOLVING
					delay(DISSOLVE_MS + STILL_LEAD_IN_MS)
					Log.i(
						TAG,
						"frames: " +
							frameStats.segment(player, PLAY_DURATION_MS + DISSOLVE_MS) { 0 },
					)

					val firstFrame = player.firstRenderSignal()
					player.stop()
					player.preload(next)
					val rendered = withTimeoutOrNull(_playingQuality.value.swapTimeoutMs) { firstFrame.await() } == true
					player.play()
					_stillPhase.value = StillPhase.FADING
					Log.i(
						TAG,
						"timing: extractMs=$extractMs dissolveMs=$DISSOLVE_MS " +
							"handoffMs=${t0.elapsedNow().inWholeMilliseconds} firstFrame=$rendered",
					)
				}
				currentIndex = nextIndex
			}
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
		releasePlayer()
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
private const val DISSOLVE_MS = 750L
private const val STILL_LEAD_IN_MS = 150L
private const val PRELOAD_TIMEOUT_MS = 8_000L
private const val STARTUP_FRAME_TIMEOUT_MS = 15_000L
private const val COUNTDOWN_TICK_MS = 100L
private const val MEMORY_POLL_MS = 2_000L
