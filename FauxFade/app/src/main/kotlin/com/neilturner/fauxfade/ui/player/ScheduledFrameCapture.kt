package com.neilturner.fauxfade.ui.player

import android.graphics.Bitmap
import android.os.Handler
import androidx.annotation.OptIn
import androidx.media3.common.GlTextureInfo
import androidx.media3.common.util.GlRect
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.ByteBufferGlEffect
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.PlayerMessage
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Captures one frame at a scheduled playback position, with the effects pipeline installed only
 * for the run-up to it.
 *
 * The point is that the pipeline is *not* in the render path for most of the video. A tap left
 * installed puts a frame processor in front of every decoded frame for the whole run, which on
 * old hardware is the thing that makes 4K miss its budget. Here the pipeline is built
 * [LEAD_TIME_MS] before the frame is wanted and torn down immediately after it is taken, so the
 * cost is paid once per capture instead of once per frame.
 *
 * The sequence:
 * 1. [captureAt] schedules a [PlayerMessage] at (target - lead). Messages are delivered on the
 *    player's application looper, which is the only thread allowed to touch the player.
 * 2. On delivery the effect is installed. Media3 builds the frame processor, an EGL context and
 *    the shader program here, which is why the lead time exists - it is the window in which the
 *    pipeline becomes ready to produce a frame.
 * 3. [GrabProcessor.processImage] drops every frame before the target time and copies the first
 *    one at or after it. [ByteBufferGlEffect.Image.copyToBitmap] copies out of the buffer media3
 *    hands over, which is reused for later frames, so nothing may hold the buffer - hence the copy
 *    here rather than a reference handed out.
 * 4. Delivery is posted back to the application looper, which pauses the player and removes the
 *    effect. The player is never called from the processing thread.
 *
 * One of these per player. The effect and its processor are per-player state because each player
 * has its own GL context and its own render thread.
 *
 * **The cost of this is visible.** Installing video effects reinitialises the player's render
 * path, so on a playing clip there is a hitch where the effect goes in. Lead time does not
 * remove that - it only makes sure the pipeline is ready by the time the target arrives. See the
 * note on [StillCaptureMode].
 */
@OptIn(UnstableApi::class)
internal class ScheduledFrameCapture(
	private val player: ExoPlayer,
	/** Whether the capture pauses the player itself. False leaves that to the caller. */
	private val pauseOnCapture: Boolean = true,
) : PlayerMessage.Target {
	/** Set once the effect is installed, so a redelivered message cannot install it twice. */
	private val installed = AtomicBoolean(false)

	/** One-shot across the whole capture, so only the first qualifying frame is copied. */
	private val delivered = AtomicBoolean(false)

	// player.applicationLooper is only safe to read on the thread the player was built on, so it
	// is captured once here rather than inside the callback, which runs on the processing thread.
	private val handler = Handler(player.applicationLooper)

	private var pending: CompletableDeferred<Bitmap?>? = null
	private var message: PlayerMessage? = null
	private var targetTimeUs = 0L

	/**
	 * Captures the first frame at or after [targetPositionMs].
	 *
	 * The returned deferred completes with the frame, or with null if nothing was captured: the
	 * message was cancelled, the player stopped before reaching the target, or the pipeline never
	 * produced a qualifying frame. The caller is expected to supply its own timeout as a backstop;
	 * this does not have one of its own because it has no idea how long the pipeline will take.
	 *
	 * Any capture already in flight is cancelled first.
	 */
	fun captureAt(targetPositionMs: Long): CompletableDeferred<Bitmap?> {
		cancelPending()
		val result = CompletableDeferred<Bitmap?>()
		targetTimeUs = targetPositionMs * 1000L
		pending = result
		delivered.set(false)

		// setPosition is relative to the current item's start. Clamped because a target inside the
		// lead window would otherwise ask for a negative position, which setPosition treats as
		// "after the last one" rather than "immediately".
		val installAtMs = max(0L, targetPositionMs - LEAD_TIME_MS)
		val msg = player.createMessage(this)
		msg.setPosition(installAtMs)
		msg.setDeleteAfterDelivery(true)
		message = msg
		msg.send()
		return result
	}

	/**
	 * Delivered on the player's application looper, at install time.
	 *
	 * The message is type-agnostic: one message is sent to this target and none are reused, so
	 * every delivery here is the one that armed the capture. PlayerMessage in 1.11.1 exposes no
	 * type constants to check against.
	 */
	override fun handleMessage(
		messageType: Int,
		payload: Any?,
	) {
		// Cancelled between send and delivery, or the player has already handed back its answer.
		if (pending == null) return
		if (!installed.compareAndSet(false, true)) return
		// A fresh effect per capture: the processor holds the one-shot latch and the target time,
		// and neither should carry over from a previous handoff.
		player.setVideoEffects(listOf(ByteBufferGlEffect(GrabProcessor())))
	}

	/**
	 * Drops an in-flight capture and removes the effect if it is installed.
	 *
	 * The deferred completes with null rather than being left pending, so a caller waiting on it
	 * is not stranded when a segment is cut short.
	 */
	fun cancelPending() {
		message?.cancel()
		message = null
		pending?.complete(null)
		pending = null
		removeEffect()
	}

	/** Cancels anything in flight and makes sure the player is left without an effect. */
	fun release() {
		cancelPending()
		removeEffect()
	}

	/**
	 * Removes the effect, but only if one is installed.
	 *
	 * setVideoEffects(emptyList()) is not free even when the list is already empty - it rebuilds
	 * the render path - so it is not called speculatively.
	 */
	private fun removeEffect() {
		if (installed.compareAndSet(true, false)) player.setVideoEffects(emptyList())
	}

	/**
	 * Hands the captured frame back, on the only thread allowed to call the player.
	 *
	 * Called from the processing thread. Everything here that reaches the player is posted, and the
	 * bitmap is already a private copy by this point.
	 */
	private fun deliver(bitmap: Bitmap) {
		handler.post {
			if (pauseOnCapture) player.pause()
			removeEffect()
			val result = pending
			pending = null
			message = null
			result?.complete(bitmap)
		}
	}

	/**
	 * Copies frames out of the pipeline.
	 *
	 * The effect itself is a pass-through - media3 renders through it unchanged and this only
	 * reads - so what reaches the display is the same either way. The only thing decided here is
	 * how big the copy is.
	 */
	private inner class GrabProcessor : ByteBufferGlEffect.Processor<Unit> {
		/** Remembered from [configure] so [getScaledRegion] can describe the whole input frame. */
		private var inputWidth = 0
		private var inputHeight = 0

		/**
		 * The size the pipeline renders to, which is also the size of the buffer handed back.
		 *
		 * This is where the downscale happens: the readback is of the *output* texture, so
		 * capping the height here caps the copy. A 4K frame is 3840x2160x4 = 33MB of readback and
		 * of bitmap; capped at [MAX_CAPTURE_HEIGHT] it is under 1.5MB, and the scaling itself is a
		 * texture sample the GPU was already doing.
		 */
		override fun configure(
			inputWidth: Int,
			inputHeight: Int,
		): Size {
			this.inputWidth = inputWidth
			this.inputHeight = inputHeight
			if (inputHeight <= MAX_CAPTURE_HEIGHT) return Size(inputWidth, inputHeight)
			val scale = MAX_CAPTURE_HEIGHT.toFloat() / inputHeight
			return Size((inputWidth * scale).roundToInt(), (inputHeight * scale).roundToInt())
		}

		/**
		 * The region of the input texture to sample into the output buffer.
		 *
		 * The whole input frame, so the output is the whole frame scaled down rather than a crop.
		 * The rectangle is in input texture coordinates, which is why [inputWidth] and
		 * [inputHeight] are remembered from [configure] - media3 calls this per frame and this is
		 * the only place they are known.
		 */
		override fun getScaledRegion(presentationTimeUs: Long): GlRect = GlRect(0, 0, inputWidth, inputHeight)

		/**
		 * Copies the first frame at or after the target time and ignores everything else.
		 *
		 * The latch is one-shot for the life of this processor, which is one capture: frames after
		 * the capture are still processed - the effect is a pass-through and is about to be torn
		 * down anyway - but not copied.
		 */
		override fun processImage(
			image: ByteBufferGlEffect.Image,
			presentationTimeUs: Long,
		): ListenableFuture<Unit> {
			if (presentationTimeUs >= targetTimeUs && delivered.compareAndSet(false, true)) {
				// copyToBitmap allocates its own bitmap and copies out of image.pixelBuffer, which
				// media3 reuses. The returned bitmap is then flipped vertically, because GL hands
				// rows back bottom-up and a Bitmap addresses them from the top.
				deliver(image.copyToBitmap())
			}
			return Futures.immediateFuture(Unit)
		}

		/**
		 * Nothing to blend: the frame is already in the output texture and this effect changes
		 * nothing about what is displayed. The return value was handed over by [processImage].
		 */
		override fun finishProcessingAndBlend(
			outputFrame: GlTextureInfo,
			presentationTimeUs: Long,
			result: Unit,
		) = Unit

		/**
		 * Called by media3 when the effect is torn down.
		 *
		 * Nothing of ours outlives the capture: the bitmap has been copied, the effect instance is
		 * discarded by the caller, and the latch is scoped to a processor that is now unreachable.
		 */
		override fun release() = Unit
	}
}

/**
 * How far ahead of the target the effect is installed.
 *
 * Long enough for the frame processor to build, an EGL context to be created and the shader
 * program to compile on the GL thread, because a frame presented before that cannot be captured.
 * Too short and the first qualifying frame arrives after the player has been paused and never
 * comes at all. A second is deliberately generous: this only delays the pipeline, it does not
 * extend it - the effect is still removed as soon as the capture lands.
 */
private const val LEAD_TIME_MS = 1_000L

/**
 * Tallest bitmap the capture will produce.
 *
 * The still is drawn over the screen and then cross-faded, so it only ever has to cover 1080p of
 * display. Capturing at the source resolution would be four times the readback and the bitmap for
 * an image that is scaled to fit anyway.
 */
private const val MAX_CAPTURE_HEIGHT = 540
