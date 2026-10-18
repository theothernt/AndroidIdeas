package com.neilturner.fauxfade.core.stats

import android.os.SystemClock
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.PlaybackException
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import com.neilturner.fauxfade.TAG

/**
 * What the video renderer had to drop to keep up, over one segment.
 *
 * Added to settle a question rather than to drive anything: having the tap installed puts a frame
 * processing pipeline in front of every frame, and the claim is that this costs frame rate at 4K.
 * That is worth a number rather than an impression, because the two plausible explanations - the
 * extra GL passes, or the GPU being the decode bottleneck either way - lead to opposite decisions
 * about whether to remove the tap.
 *
 * Three figures, because no single one is enough:
 * - **dropped** is the renderer's own count and is reported whatever is installed, so it is the
 *   one figure that can be compared across configurations.
 * - **expected** is derived from the source frame rate over the segment, which is what the
 *   renderer was asked for.
 * - **offset** is how late the pipeline is delivering frames relative to when they were due, from
 *   the frame processor's own accounting. A number that climbs through a run means the pipeline is
 *   progressively behind rather than uniformly a little late.
 *
 * The frame processor's own processed-frame count is preferred for the delivered figure when it is
 * available, but it is only reported while a processing pipeline exists - so with the tap off there
 * is nothing to count and the delivered figure falls back to expected minus dropped.
 */
@OptIn(UnstableApi::class)
internal class FrameStats(
	/**
	 * Identifies the player this records for, in every line it logs.
	 *
	 * A plain name rather than a [com.neilturner.fauxfade.ui.player.PlayerSlot]: this lives in
	 * core, and taking the enum would make core depend on a feature's own player numbering.
	 */
	private val slot: String,
) : AnalyticsListener {
	/** Cumulative since the renderer was created, so every sample is a delta against the last. */
	private var droppedTotal = 0L
	private var processedTotal = -1L
	private var offsetUs = 0L

	private var lastDropped = 0L
	private var lastProcessed = -1L
	private var lastOffsetUs = 0L

	/**
	 * The last frame rate that was actually reported.
	 *
	 * Held rather than read fresh because the video format is not always available at the moment a
	 * segment ends - it is cleared as one item goes and set as the next is prepared, and the sample
	 * lands right in between. A remembered value beats a spurious zero.
	 */
	private var frameRate = 0f

	/** Wall clock when this recorder was attached, so decoder timings can be read against it. */
	private val startedAtMs = SystemClock.elapsedRealtime()

	/** Player errors that reached the app. Decoder failures media3 recovered from are not counted. */
	private var errorCount = 0

	/** Last value of the real pipeline frame count, so each segment reports a delta. */
	private var lastCarried = 0L

	override fun onDroppedVideoFrames(
		eventTime: AnalyticsListener.EventTime,
		rendererIndex: Int,
		droppedFrames: Long,
	) {
		droppedTotal = droppedFrames
	}

	override fun onVideoFrameProcessingOffset(
		eventTime: AnalyticsListener.EventTime,
		frameProcessingOffsetUs: Long,
		totalFrames: Int,
	) {
		processedTotal = totalFrames.toLong()
		offsetUs = frameProcessingOffsetUs
	}

	/**
	 * How long the codec took to come up, per clip.
	 *
	 * Logged as it happens rather than folded into the segment line, because this is the figure
	 * that explains a missed handoff. The handoff waits for the incoming player to render, and a
	 * clip whose codec needs most of a second to initialise has spent most of its budget before it
	 * has drawn anything - whether or not anything is in front of its render path.
	 *
	 * The four-argument form rather than the deprecated three, which media3 kept only as a
	 * forwarding stub. The extra argument is when the decoder came up on the same clock
	 * [startedAtMs] reads, so `sinceAttachMs` is a difference between two instants rather than
	 * between an instant and the moment this callback got dispatched.
	 */
	override fun onVideoDecoderInitialized(
		eventTime: AnalyticsListener.EventTime,
		decoderName: String,
		initializedTimestampMs: Long,
		initializationDurationMs: Long,
	) {
		// positionMs and bufferedMs are there to separate the two reasons a codec can be slow to
		// come up: it may have been waiting on data, or it may genuinely have taken that long once it
		// had it. Only the second is a device limit.
		Log.i(
			TAG,
			"decoder: slot=$slot name=$decoderName initMs=$initializationDurationMs " +
				"atPositionMs=${eventTime.eventPlaybackPositionMs} bufferedMs=${eventTime.totalBufferedDurationMs} " +
				"sinceAttachMs=${initializedTimestampMs - startedAtMs}",
		)
	}

	/** The resolution actually being decoded, so a downscale cannot hide behind a 4K label. */
	override fun onVideoSizeChanged(
		eventTime: AnalyticsListener.EventTime,
		videoSize: VideoSize,
	) {
		Log.i(TAG, "video: slot=$slot size=${videoSize.width}x${videoSize.height}")
	}

	/**
	 * A player error, which is not the same as a decoder that failed once and was retried.
	 *
	 * Worth its own line because the two look identical from outside the app: media3 failing over
	 * to another decoder and carrying on is invisible to a user, whereas an error it does not
	 * recover from is a black screen that keeps being one.
	 */
	override fun onPlayerError(
		eventTime: AnalyticsListener.EventTime,
		error: PlaybackException,
	) {
		errorCount++
		Log.w(TAG, "playererror: slot=$slot count=$errorCount ${error.errorCodeName}", error)
	}

	/**
	 * The figures for the segment that has just finished, as a log line.
	 *
	 * [pipelineFramesSeen] must be a count of frames that genuinely went through the render path,
	 * not one inferred from the format - which is why it is passed in rather than derived here.
	 */
	fun segment(
		player: ExoPlayer,
		segmentMs: Long,
		pipelineFramesSeen: () -> Long,
	): String {
		val dropped = droppedTotal - lastDropped
		lastDropped = droppedTotal

		val seconds = segmentMs / 1000.0
		// FRATE_UNSET is 0, and a clip that does not declare a frame rate would otherwise report an
		// expected count of zero and make every drop look like a rounding error.
		player.videoFormat
			?.frameRate
			?.takeIf { it > 0f }
			?.let { frameRate = it }
		val expected = (frameRate * segmentMs / 1000f).toInt()

		// Counted, not inferred, and this is the figure to believe. Unlike expected-minus-dropped it
		// cannot flatter a decoder that never started: if the pipeline was never reached, this is
		// zero, however healthy the drop count looks.
		val carried = pipelineFramesSeen()
		val carriedDelta = carried - lastCarried
		lastCarried = carried

		val offsetDelta = offsetUs - lastOffsetUs
		lastOffsetUs = offsetUs

		val fps = if (seconds > 0 && carriedDelta >= 0) carriedDelta / seconds else 0.0
		return "slot=$slot carried=$carriedDelta expected=$expected fps=${"%.1f".format(fps)} " +
			"dropped=$dropped errors=$errorCount offsetMs=${offsetDelta / 1000.0}"
	}
}
