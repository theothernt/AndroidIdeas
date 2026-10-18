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
import com.neilturner.fauxfade.core.media.AerialAsset
import com.neilturner.fauxfade.core.media.Quality
import com.neilturner.fauxfade.core.media.VideoCatalog
import com.neilturner.fauxfade.core.media.VideoItem
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
 * Owns the players and the handoff between them, for every approach still living in the shared
 * path.
 *
 * Four of them, in two shapes. Three capture a frame of the clip that is *leaving* - the always-on
 * tap, the scheduled effect and the retriever - and all three hand off between two players: one slot
 * plays for [PLAY_DURATION_MS], the other is primed part way through at [PRELOAD_AFTER_MS], and at
 * the end the outgoing player is paused, a frame is taken from it, and the surfaces swap under that
 * frame. The fourth, [StillCaptureMode.PRELOADED_FRAME], reads the clip that is *arriving* instead
 * and needs only one player of its own. [captureStill] is the only place that asks which; the rest
 * of this class is shared structure rather than a choice between them.
 *
 * The whole cycle is driven from a single coroutine, which is also what publishes
 * [remainingSeconds], so the countdown cannot drift out of step with the actual switch.
 *
 * The fifth method, [StillCaptureMode.PIXEL_COPY], is offered on the same picker but is not run
 * here. It reads the surface rather than the player, so it has a view model, a loop and a capture
 * class of its own under `feature/pixelcopy`, and the startup screen's Go button routes it there
 * rather than starting a run - see `MainActivity`. That is also why this class no longer has to
 * decide, mid-handoff, which of two orders to pause and capture in: the one method that needed the
 * other order is the one that left.
 *
 * It also owns the startup flow: the app sits in [AppPhase] until the quality has been chosen and
 * the countdown has run out (or been skipped), and only then are the players built and the loop
 * started. Nothing about playback is torn down or rebuilt to change quality - the picker is gone by
 * the time playback exists.
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

	/**
	 * One per player, reporting what the video renderer had to drop each segment.
	 *
	 * Measurement only - nothing here changes behaviour. It exists to answer whether having the
	 * tap installed costs frame rate, which is the whole argument for removing it, and that is not
	 * something to guess at on a device this size.
	 */
	private val frameStats = PlayerSlot.entries.associateWith { FrameStats(it.name) }

	/**
	 * How the run takes its stills, chosen on the startup screen and locked when playback starts.
	 *
	 * Seeded from [StillCaptureMode.PIXEL_COPY], which is also the only entry that can never reach
	 * the loop on this view model: the startup screen's Go button routes that one to its own screen
	 * under `feature/pixelcopy`. It stays the default because it is the approach that has been
	 * measured working at both 1080p and 4K.
	 *
	 * Read at handoff time rather than captured at construction, and the loop branches on it once
	 * when it starts, so picking a mode that needs a different sequence cannot leave half of one
	 * behind.
	 */
	private val _captureMode = MutableStateFlow(StillCaptureMode.PIXEL_COPY)
	val captureMode: StateFlow<StillCaptureMode> = _captureMode.asStateFlow()

	/**
	 * The slots actually in use: both for the three modes that hand off between two players, and
	 * one for [StillCaptureMode.PRELOADED_FRAME], which stops its player before preparing the next
	 * clip and so has never needed a second.
	 *
	 * Chosen from the mode rather than from a flag. It used to be a `SINGLE_PLAYER` switch, set to
	 * answer whether two concurrent decoders were what broke 4K on this device - and it did, which
	 * is why there is now a PixelCopy feature with one player of its own. What is left here is
	 * topology: a property of the technique rather than of a measurement, so it belongs on the
	 * branch that decides the technique instead of on a switch above all of them.
	 *
	 * Exposed so the player screen draws surfaces for the players that exist. Iterating
	 * `PlayerSlot.entries` there and asking for a player that was never built would throw.
	 */
	lateinit var slots: List<PlayerSlot>
		private set

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

	/**
	 * True between [beginPlayback] building the players and them being released.
	 *
	 * Two things release them and two things need to know: [stopRun] and [onCleared] release, and
	 * [stop] - which the player screen runs when it leaves the composition - has to know there is
	 * still something to pause. Leaving a feature clears its view model store, and whether that
	 * lands before or after the screen's own disposal is a detail of how Compose tears a subtree
	 * down rather than something this class can assume; and stopping a run releases first and
	 * un-composes second, in the other order. Rather than depend on either, this asks the question
	 * that actually matters - is there a player right now - instead of tracking how we got here.
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

	fun playerFor(slot: PlayerSlot): ExoPlayer = players.getValue(slot)

	/** Records the quality to play at. Takes effect when playback starts. */
	fun select(quality: Quality) {
		_quality.value = quality
		Log.i(TAG, "picked: $quality")
	}

	/**
	 * Records how the still should be taken. Also takes effect when playback starts.
	 *
	 * Logged rather than applied immediately: the loop reads the mode once when it starts, so
	 * changing it mid-run would leave the running loop disagreeing with the picker.
	 */
	fun selectCapture(mode: StillCaptureMode) {
		_captureMode.value = mode
		Log.i(TAG, "capture: picked $mode")
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
			// Read here rather than once at the top: this is the moment the choice is locked in,
			// and the three two-player modes need both players built while the preloaded one needs
			// only the first. Everything downstream keys off what was actually built.
			val mode = captureMode.value
			slots =
				if (mode == StillCaptureMode.PRELOADED_FRAME) {
					listOf(PlayerSlot.PRIMARY)
				} else {
					PlayerSlot.entries.toList()
				}
			players = createPlayerSlots(getApplication(), quality, slots)
			// As soon as there is something to act on. [stopRun] and [onCleared] release through
			// this flag and [stop] declines to touch a player that is already gone, so it is set
			// here rather than after the captures below, which are built in a world where a player
			// already exists.
			live = true
			// After the players: the scheduled capture reads the player's application looper when it
			// is built. Keyed off what was actually built rather than off the enum, so the preloaded
			// run does not ask for a player that was never created.
			scheduled = players.keys.associateWith { ScheduledFrameCapture(players.getValue(it)) }
			retrievers = players.keys.associateWith { RetrieverFrameCapture() }
			players.forEach { (slot, player) -> player.addAnalyticsListener(frameStats.getValue(slot)) }
			// Everything below belongs to a run rather than to the view model, and a second run in
			// the same view model would otherwise inherit it. The still is the one that matters: a
			// run that ended mid-segment leaves a sealed or fading frame behind, and without this
			// the next run's player screen would put it up and hold it there for the whole of the
			// first segment, until the loop had a new one to replace it with.
			_foreground.value = PlayerSlot.PRIMARY
			_swapping.value = false
			_stillFrame.value = null
			_stillPhase.value = StillPhase.HIDDEN
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
		// time before each capture; the retriever and the preloaded frame never install one at all,
		// so for both of those the render path is left completely alone for the whole segment - which
		// is the argument for picking either of them.
		//
		// Safe get rather than getValue: taps is keyed off the whole enum, but a preloaded run only
		// builds one player, so asking for the second would throw.
		if (CAPTURE_STILLS && captureMode.value == StillCaptureMode.ALWAYS_ON_TAP) {
			taps.forEach { (slot, tap) -> players[slot]?.setVideoEffects(listOf(tap)) }
		}
		// Logged so a run's numbers can be attributed to the path that produced them. The modes
		// are only comparable next to each other, and this is the only line that says which one a
		// given set of timings came from.
		Log.i(TAG, "capture: mode=${captureMode.value} stills=$CAPTURE_STILLS")
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

	/**
	 * Stops the loop and leaves the players holding their frame, without releasing them.
	 *
	 * The counterpart to [stopRun], and deliberately the lighter of the two: nothing is built here,
	 * so nothing has to be built again if the same view model is used again. Released players are
	 * not touched - see [live].
	 *
	 * Called by the player screen when it leaves the composition, which on this path happens
	 * *after* [stopRun] has already released them, so the guard is load-bearing rather than
	 * defensive.
	 */
	fun stop() {
		if (!live) return
		loopJob?.cancel()
		loopJob = null
		memoryJob?.cancel()
		memoryJob = null
		players.values.forEach { it.pause() }
	}

	/**
	 * Ends the run and puts the picker back up.
	 *
	 * What Back does during playback. Releasing the players is what changes the screen: the
	 * player screen is composed only while the phase is [AppPhase.PLAYING], so flipping back to
	 * [AppPhase.PICKING] takes it out of the composition and the startup screen in.
	 *
	 * Releasing rather than pausing is the point. A paused run would keep both decoders and the
	 * whole of the buffered playlist alive under a screen with three buttons on it, and there is
	 * nothing to come back to - the picker re-reads nothing, because the quality and the method
	 * are still the same fields.
	 *
	 * The view model survives, so the two choices made on the picker are still there afterwards and
	 * a second run costs nothing to start. Everything that belonged to the run rather than to the
	 * view model is reset by [beginPlayback] when it does.
	 */
	fun stopRun() {
		loopJob?.cancel()
		loopJob = null
		memoryJob?.cancel()
		memoryJob = null
		countdownJob?.cancel()
		countdownJob = null
		releasePlayers()
		_phase.value = AppPhase.PICKING
		Log.i(TAG, "stopped: ${_playingQuality.value}, back to the picker")
	}

	/**
	 * Releases the players, if there are any, and forgets that there were.
	 *
	 * Shared by [stopRun], which then puts the picker back, and [onCleared], which does not
	 * because there is nothing left to put anything back into. [live] goes down first so that a
	 * [stop] arriving afterwards - from the player screen un-composing - sees no player rather
	 * than a released one.
	 */
	private fun releasePlayers() {
		if (!live) return
		live = false
		// Before the players: a capture in flight holds a pending PlayerMessage and possibly an
		// installed effect, and both have to be dropped while the player they belong to still
		// exists to drop them from. Both guards because [live] is set as soon as the players exist
		// and the captures are built after them, so a failure in between leaves one initialised
		// and the other not.
		if (::scheduled.isInitialized) scheduled.values.forEach { it.release() }
		// Uninitialised when the run is stopped from the picker, where no player was ever built.
		if (::players.isInitialized) players.values.forEach { it.release() }
	}

	/**
	 * Runs the loop for whatever the picked mode actually needs.
	 *
	 * Two shapes, not three. The single-player loop that used to sit here has left with the
	 * PixelCopy feature - it was only ever reachable by the one method that reads a surface instead
	 * of reading the player, and now that method has a view model of its own, it has its own copy of
	 * the loop rather than a branch into this one. What remains is the two-player handoff, plus the
	 * preloaded frame, which is genuinely a different sequence rather than a variant: it reads the
	 * clip that is arriving rather than the one that is leaving, and it never pauses anything.
	 *
	 * [StillCaptureMode.PIXEL_COPY] lands in the two-player loop if it ever reaches here, which it
	 * should not - it has its own screen. It would produce cuts rather than throw, and the run's
	 * first log line would name the method that produced them.
	 */
	private suspend fun runPlaylistLoop() {
		if (captureMode.value == StillCaptureMode.PRELOADED_FRAME) {
			runPreloadedFrameLoop()
		} else {
			runDoublePlayerLoop()
		}
	}

	/**
	 * One player, and the still is the *next* clip's first frame rather than this clip's last.
	 *
	 * The change that makes this work is not a tuning change - it is a different clip and a
	 * different moment. Every other path reads a frame of the clip that is leaving, at the instant
	 * the handoff begins, so the read has to happen *inside* the transition and every millisecond
	 * of it is a millisecond of frozen picture. This reads a frame of the clip that is arriving,
	 * up to five seconds earlier, while nothing on screen has changed at all.
	 *
	 * So the sequence is inverted from the others, and each step buys something specific:
	 *
	 * - **Read ahead.** The retriever's two to three seconds were fatal in a handoff and are free
	 *   here, because the outgoing clip plays straight through them. The read is off the main
	 *   thread and starts part way into the segment, not at the end of it.
	 * - **Dissolve in.** The frame rises from zero opacity over [DISSOLVE_MS] while clip one keeps
	 *   playing underneath - so the viewer sees one picture turn into the next, and clip one is
	 *   never paused. Nothing is captured from it, because nothing has to be: the still it fades
	 *   into was never its frame.
	 * - **Swap under cover.** Only once the frame is fully opaque does the player stop and clip two
	 *   come in paused. The picture does not change during any of that, because the still is
	 *   covering the entire screen. Stopping before preparing means the old decoder is released
	 *   before the new one is requested rather than the two overlapping inside a transition.
	 * - **Reveal on the real frame.** The still fades off clip two only once clip two has actually
	 *   rendered, and playback starts as it does. There is no moment where an image is shown
	 *   against an empty surface.
	 *
	 * The one thing to watch is the read competing with the decoder already running. It starts
	 * mid-segment, when the decoder is in steady state rather than spinning up, and [frameStats]
	 * is what would show it: the tell would be `dropped` climbing from the preload point rather
	 * than from the handoff.
	 */
	private suspend fun runPreloadedFrameLoop() =
		coroutineScope {
			val slot = slots.first()
			val player = players.getValue(slot)
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

				// Kicked off through the segment's preload point rather than at its end, so the read
				// has the rest of the segment to finish in. The async is a child of the loop's own
				// coroutineScope, not of the view model, so stopping the loop stops a read in flight
				// instead of leaving it to finish into nothing.
				var pending: Deferred<Bitmap?>? = null
				tickSegment { pending = async { retrievers.getValue(slot).extractAt(next.uri, 0L) } }

				// Normally already complete by now - it was started five seconds ago - so this
				// returns at once. The branch for null is only there because a segment that somehow
				// reaches here without a read still needs an answer rather than an unhandled null.
				val t0 = TimeSource.Monotonic.markNow()
				val extraction = pending ?: async { retrievers.getValue(slot).extractAt(next.uri, 0L) }
				// Generous, and not because anything is waiting on it: nothing on screen is held up
				// by this, so a read that outlives the budget costs a cut rather than a stall. Eight
				// seconds on top of whatever headroom the segment already gave it.
				val still = withTimeoutOrNull(PRELOAD_TIMEOUT_MS) { extraction.await() }
				val extractMs = t0.elapsedNow().inWholeMilliseconds

				if (still == null) {
					val name = next.uri.substringAfterLast('/')
					Log.w(TAG, "preload: no frame for $name after ${extractMs}ms, cutting instead")
					_stillFrame.value = null
					_stillPhase.value = StillPhase.HIDDEN
					player.playFromStart(next)

					// The same two lines the good path logs, and deliberately so. A run that
					// degrades every segment is the run whose numbers most need recording -
					// without them a mode that silently never produces a still would be
					// indistinguishable in the logs from one that was never given the chance to.
					// [PLAY_DURATION_MS] alone here rather than plus the dissolve, because with
					// no still there is no dissolve: the switch happens the moment the segment's
					// clock runs out.
					val frames =
						frameStats.getValue(slot).segment(player, PLAY_DURATION_MS) { taps.getValue(slot).framesSeen() }
					Log.i(TAG, "frames: $frames")
					Log.i(TAG, "timing: extractMs=$extractMs still=none handoffMs=${t0.elapsedNow().inWholeMilliseconds}")
				} else {
					// Nothing is paused here, which is the whole difference. Clip one plays on for the
					// full dissolve, so the transition costs it no time at all - where every other mode
					// freezes it for as long as its capture takes.
					_stillFrame.value = still
					_stillPhase.value = StillPhase.DISSOLVING
					// The tween runs in the overlay, which starts it a frame or two after this loop
					// sets the phase, so waiting exactly [DISSOLVE_MS] would reach the swap a frame
					// short of the image being opaque. [STILL_LEAD_IN_MS] is the same slop the
					// seal-then-swap above needs, for the same reason.
					delay(DISSOLVE_MS + STILL_LEAD_IN_MS)

					// Measured up to here rather than to the end of the loop, because this is where
					// clip one's playback stops: everything after it is the swap, and nothing plays
					// during the swap, so counting it would only make `expected` wrong.
					val frames =
						frameStats
							.getValue(slot)
							.segment(player, PLAY_DURATION_MS + DISSOLVE_MS) { taps.getValue(slot).framesSeen() }
					Log.i(TAG, "frames: $frames")

					// Release before request, and stop() rather than pause() - pausing would leave the
					// finished clip's decoder held for its own duration and hand it straight back, so
					// the next clip's would be requested while that one was still down. What the
					// surface does underneath while this happens does not matter: the still is at full
					// opacity over every pixel of it.
					val firstFrame = player.firstRenderSignal()
					player.stop()
					player.preload(next)
					val rendered = withTimeoutOrNull(_playingQuality.value.swapTimeoutMs) { firstFrame.await() } == true
					// Started as the reveal begins, so the still comes off a clip that is already
					// moving. The still is frame zero of this clip and the clip starts at frame zero,
					// so the two agree at the first instant and only then drift apart - which is what
					// makes the fade read as one image coming alive rather than two pictures swapped.
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

	/**
	 * Two players, cross-fading through a captured still.
	 *
	 * The original design: one slot plays, the other is primed part way through, and at the end the
	 * outgoing player is paused, a frame is captured from it, the surfaces swap under that frame
	 * and it is faded out.
	 */
	private suspend fun runDoublePlayerLoop() {
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

			// Capture first, then pause - and never the other way round, because every remaining
			// mode reads its frame out of the player, and a paused player produces no frames at all.
			// Pausing after the capture is also what keeps the still in step with the video: the
			// frame taken is the frame the player then stops on, rather than one taken a lead
			// earlier while the clip carried on underneath it.
			//
			// This used to branch on the mode. The one approach that could pause first was the one
			// that copies the surface rather than reading the player - a surface holds its last
			// buffer after its producer stops, a decoder does not - and it has left with the
			// PixelCopy feature, which runs that order in a loop of its own. What is left has one
			// order and nothing that could disagree about it.
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
			// Sampled against the outgoing player, because that is the one that just played the whole
			// segment. The first segment of a run is not comparable with the rest - the codec is
			// still warming up while it is being measured.
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
	 * Null is the same failure for every path and is handled the same way by the caller - the
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
		return when (captureMode.value) {
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

			// Picked here, run somewhere else. This screen's Go button answers [PIXEL_COPY] by
			// leaving for its own view model rather than by starting a run, so this view model never
			// gets to this function in that mode - and the branch is here only so the `when` stays
			// exhaustive over the enum, which is the same reason the one below it is.
			//
			// Returning null rather than throwing is deliberate for the same reason: if a route ever
			// let this through, the run degrades to plain cuts and the log line at the top of it says
			// which mode produced them. A crash at a transition with nothing on screen would be a
			// worse answer than a dull picture.
			StillCaptureMode.PIXEL_COPY -> {
				null
			}

			// Not a handoff-time capture at all, so it has nothing to give here. Its loop reads the
			// still from the *incoming* clip seconds earlier and never calls this function; the
			// branch exists so the `when` stays exhaustive over the enum rather than reaching a
			// mode it does not know at a point in the transition where there is no time to find out.
			StillCaptureMode.PRELOADED_FRAME -> {
				null
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
		if (captureMode.value != StillCaptureMode.SCHEDULED_EFFECT) return null
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
		releasePlayers()
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
 * How long the preloaded frame takes to rise from invisible to fully opaque.
 *
 * Inside the 500-1000ms that reads as a dissolve rather than as a crossfade with two different
 * pictures in it. Longer than that and the reveal of a still image starts to feel like a
 * slideshow; shorter and it stops being a dissolve and becomes the clip changing with a smear on
 * it.
 *
 * Only [StillCaptureMode.PRELOADED_FRAME] runs this. It is time the outgoing clip spends playing,
 * not time it spends frozen, so unlike every other cost in a handoff it is not on screen as a
 * stall - which is also why the frame statistics for this mode measure a segment of
 * [PLAY_DURATION_MS] plus this, not of [PLAY_DURATION_MS] alone.
 *
 * Internal rather than file-private because the loop's `delay` and the overlay's tween have to be
 * the same number or the swap lands in the middle of the fade: one is the wait and the other is
 * the animation it is waiting for, and two constants that only have to agree are two constants
 * that will eventually stop agreeing.
 */
internal const val DISSOLVE_MS = 750L

/**
 * How long a read that is already running in the background is given before it is abandoned.
 *
 * Longer than [RETRIEVER_TIMEOUT_MS] on purpose, and not because this read is slower - it is the
 * same read. The difference is what it is up against: the retriever at a handoff is competing with
 * the transition that is happening right now, so every millisecond of it is visible, whereas this
 * one is competing with a segment that still has seconds to run and has already had
 * [PRELOAD_AFTER_MS] to get started. Eight seconds here costs nothing if it is spent, whereas five
 * there would have cost the still.
 *
 * Abandoning it is not a failure of the same shape as a timeout elsewhere either: nothing is
 * waiting on it, the loop simply cuts instead of dissolving.
 */
private const val PRELOAD_TIMEOUT_MS = 8_000L

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
