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

/**
 * Takes the still without the effects pipeline at all: stop the player, then ask
 * [MediaMetadataRetriever] for the frame.
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
		return withContext(Dispatchers.IO) { frameAt(url, targetPositionMs.coerceAtLeast(0L) * 1000L) }
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
				applyRotation(retriever, frame)
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
