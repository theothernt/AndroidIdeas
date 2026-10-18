package com.neilturner.fauxfade.ui.player

import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.neilturner.fauxfade.TAG
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlin.time.TimeSource

/**
 * Takes a still without the effects pipeline at all: ask [MediaMetadataRetriever] for a frame,
 * reading it out of the file rather than out of anything on screen.
 *
 * Two ways in, and the difference between them is the whole story of this class. [captureAt] has
 * to stop the player first, because the frame it wants belongs to a segment that is already
 * ending; [extractAt] does not go near a player at all, because the frame it wants is from a clip
 * that has not started yet.
 *
 * This exists as the other half of [StillCaptureMode] because the two approaches fail in
 * opposite ways. The scheduled effect puts a pipeline in front of the frame it wants, which costs
 * a reinitialisation of the render path; this puts nothing in front of anything, but reopens the
 * file and seeks it, which on a network source can cost more than the pipeline did.
 *
 * **Known risk on this app's content.** The retriever runs in the media stack rather than in the
 * app, so `network_security_config.xml` may not apply to it, and the catalogue is fetched over
 * plain http. If the retriever refuses the url it fails with null and the handoff degrades to a
 * cut, which is the same failure the tap has when it catches no frame.
 *
 * **Measured, and the bigger of the two risks: it cannot read the 4K clips at all on this device.**
 * 2K sources come back in ~2.4s; every 4K source - SDR and HDR alike, so it is not Dolby Vision -
 * returns null after 2.7-3.5s. Both qualities are read the same way, so what differs is the
 * source. See [StillCaptureMode.PRELOADED_FRAME] for what that costs whichever loop is running it.
 *
 * **Colour.** Unlike the GL path, the retriever does not tone-map, so a 10-bit HDR clip comes back
 * as it is encoded rather than as it looks on the screen. Expect it to look wrong on the HDR
 * qualities rather than subtly wrong.
 */
@OptIn(UnstableApi::class)
internal class RetrieverFrameCapture {
	/**
	 * Stops [player] and returns the frame at [targetPositionMs] of [url].
	 *
	 * The player is stopped directly rather than through a `PlayerMessage`. The frame comes back
	 * out of the file and not out of the player, so the only reason to stop playback is that
	 * reading over the network takes seconds and the outgoing clip must not carry on past its
	 * segment while that happens. An earlier version scheduled a position message to do it, and a
	 * message whose position the player has already passed never fires at all - which, called at a
	 * handoff, is every time.
	 *
	 * Must be called on the player's application looper, because it calls [ExoPlayer.pause].
	 * Returns null if the file could not be opened or held no frame at that position.
	 */
	suspend fun captureAt(
		player: ExoPlayer,
		url: String,
		targetPositionMs: Long,
	): Bitmap? {
		player.pause()
		return extractAt(url, targetPositionMs)
	}

	/**
	 * The frame at [positionMs] of [url], without going near a player.
	 *
	 * The difference that makes the read usable ahead of time. [captureAt] has to stop the
	 * playback it interrupts, and that is exactly why the retriever was unusable at a handoff:
	 * two to three seconds with the outgoing clip frozen on screen, at every single transition.
	 *
	 * Aimed at the *incoming* clip instead, part way through the *outgoing* one, and there is
	 * nothing to interrupt. The read is off the main thread, the clip on screen plays straight
	 * through it, and the seconds it takes are seconds nobody was waiting for. Same work, moved
	 * to a moment where it costs nothing.
	 *
	 * Callers are responsible for being off any thread that matters - this does network and
	 * decode work, which is why every path to it ends in [Dispatchers.IO].
	 */
	suspend fun extractAt(
		url: String,
		positionMs: Long,
	): Bitmap? {
		val started = TimeSource.Monotonic.markNow()
		val frame = withContext(Dispatchers.IO) { frameAt(url, positionMs.coerceAtLeast(0L) * 1000L) }
		// Timed here rather than at the call site, because the figure means different things in
		// the two contexts and neither is obvious from where it is read: at a handoff this is
		// frozen picture, and ahead of a handoff it is a number that only matters if it grows
		// large enough to reach the budget or to make the decoder drop frames.
		Log.i(
			TAG,
			"retriever: ${started.elapsedNow().inWholeMilliseconds}ms ok=${frame != null} " +
				"size=${frame?.width}x${frame?.height} $url",
		)
		return frame
	}

	/** Cancels a capture in flight. Nothing is held between calls, so there is nothing to do. */
	fun release() = Unit

	/** Reads the frame, applying the clip's rotation because the retriever does not. */
	private fun frameAt(
		url: String,
		positionUs: Long,
	): Bitmap? {
		val retriever = MediaMetadataRetriever()
		return try {
			retriever.setDataSource(url)
			// CLOSEST_SYNC, not the exact frame: a sync frame is one the decoder can start from, so
			// this is the difference between seeking back a few frames and decoding forward from
			// the nearest keyframe. The still is on screen for a fraction of a second either way.
			val frame = retriever.getFrameAtTime(positionUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
			if (frame == null) {
				Log.w(TAG, "retriever: no frame at ${positionUs / 1000}ms of $url")
				null
			} else {
				fitToDisplay(applyRotation(retriever, frame))
			}
		} catch (e: Exception) {
			// A retriever failure is never fatal - the handoff falls back to a cut - but it is
			// otherwise invisible, which is how a cleartext block would go unnoticed.
			Log.w(TAG, "retriever: could not read $url", e)
			null
		} finally {
			retriever.release()
		}
	}

	/**
	 * Shrinks a frame to at most [MAX_STILL_WIDTH] wide, which is the display's own limit.
	 *
	 * The other path to this number gets it for free - a surface copy is the size of the surface.
	 * This one does not: the frame comes out of the file at the file's resolution, so a 4K clip
	 * hands back roughly 33 MB of bitmap for an image that is about to be drawn at 1920 wide and
	 * then thrown away at the end of the segment.
	 *
	 * Aspect preserved and only ever shrinking, for the same reason [PixelCopyCapture] only ever
	 * shrinks: a still larger than the screen it is shown on cannot be justified by anything.
	 */
	private fun fitToDisplay(frame: Bitmap): Bitmap {
		if (frame.width <= MAX_STILL_WIDTH) return frame
		val height = (frame.height * (MAX_STILL_WIDTH.toFloat() / frame.width)).roundToInt().coerceAtLeast(1)
		val scaled = Bitmap.createScaledBitmap(frame, MAX_STILL_WIDTH, height, true)
		// Recycled rather than left to the collector. This allocation happens every segment, on a
		// loop that runs unattended for hours, and waiting on a collection is how four or five of
		// them end up in flight at once.
		frame.recycle()
		return scaled
	}

	/** Rotates if the clip carries a rotation hint. Most aerials do not; some do. */
	private fun applyRotation(
		retriever: MediaMetadataRetriever,
		frame: Bitmap,
	): Bitmap {
		val degrees =
			retriever
				.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
				?.toIntOrNull()
				?: 0
		if (degrees == 0) return frame
		return Bitmap.createBitmap(frame, 0, 0, frame.width, frame.height, Matrix().apply { postRotate(degrees.toFloat()) }, true)
	}
}
