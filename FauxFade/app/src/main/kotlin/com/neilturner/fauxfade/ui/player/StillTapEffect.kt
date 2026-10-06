package com.neilturner.fauxfade.ui.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.opengl.GLES20
import android.opengl.GLES30
import android.util.Log
import androidx.core.graphics.createBitmap
import androidx.media3.common.GlObjectsProvider
import androidx.media3.common.GlTextureInfo
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import androidx.media3.effect.PassthroughShaderProgram
import com.neilturner.fauxfade.TAG
import kotlinx.coroutines.CompletableDeferred
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executor

/**
 * Taps the frames the player is already decoding, so the still can be taken from the decoder
 * rather than copied back off the surface.
 *
 * This is installed with [androidx.media3.exoplayer.ExoPlayer.setVideoEffects], which puts a
 * frame processor in the player's render path while Media3 keeps owning the output surface.
 * The pass-through is untouched: [PassthroughShaderProgram] draws the input straight through,
 * and the tap only reads pixels, so what reaches the display is unchanged.
 *
 * Readback happens inside the frame callback because that is the only place with a current GL
 * context - [GlObjectsProvider] exposes no GL thread to post to. That is why [arm] has to be
 * called while the player is still playing: a paused player produces no frames, so a readback
 * requested after the pause would never be serviced. [PlayerViewModel] therefore arms this
 * just before it pauses and reads the result just after.
 */
@UnstableApi
internal class StillTapEffect : GlEffect {
	private val shaderProgram = TapShaderProgram()

	/** The most recent frame read back, or null if none has landed. */
	fun latest(): Bitmap? = shaderProgram.latest()

	/**
	 * Asks for a readback on the next frames to arrive, and returns a signal that completes when
	 * one has been captured. Re-arming after a capture is a no-op, so a late caller joins the
	 * capture already in flight rather than restarting it.
	 */
	fun arm(): CompletableDeferred<Unit> = shaderProgram.arm()

	override fun toGlShaderProgram(
		context: Context,
		useTempPool: Boolean,
	): GlShaderProgram = shaderProgram
}

/**
 * A pass-through [GlShaderProgram] that copies the incoming frame out of the framebuffer into
 * a [Bitmap] whenever it is armed.
 *
 * Readback is a GPU-to-CPU stall, so it only happens on armed frames. Arming well before the
 * pause costs a stall on the frames leading into the handoff, which is why [PlayerViewModel]
 * keeps the armed window short.
 */
@UnstableApi
private class TapShaderProgram : GlShaderProgram {
	private val delegate = PassthroughShaderProgram()

	@Volatile
	private var armed = false

	@Volatile
	private var lastCapture: Bitmap? = null

	@Volatile
	private var captured = CompletableDeferred<Unit>()

	// Owned framebuffer for input textures that arrive without one. The decoder's own output
	// usually has no FBO bound, and there is nothing to read pixels out of without one.
	private var ownedTexId = 0
	private var ownedFboId = 0

	fun latest(): Bitmap? = lastCapture

	fun arm(): CompletableDeferred<Unit> {
		// A fresh signal per request. A single one-shot completed on the first capture ever and
		// returned instantly for every later arm, so the caller never waited for a new frame.
		// The previous capture is dropped too: latest() is only meaningful for the arm that is in
		// flight, and with no fallback a stale still from the last handoff would be far worse than
		// no still at all.
		lastCapture = null
		captured = CompletableDeferred()
		armed = true
		return captured
	}

	override fun setInputListener(inputListener: GlShaderProgram.InputListener) {
		delegate.setInputListener(inputListener)
	}

	override fun setOutputListener(outputListener: GlShaderProgram.OutputListener) {
		delegate.setOutputListener(outputListener)
	}

	override fun setErrorListener(
		executor: Executor,
		errorListener: GlShaderProgram.ErrorListener,
	) {
		delegate.setErrorListener(executor, errorListener)
	}

	override fun queueInputFrame(
		glObjectsProvider: GlObjectsProvider,
		inputTexture: GlTextureInfo,
		presentationTimeNs: Long,
	) {
		if (armed) {
			armed = false
			readBack(inputTexture)
		}
		delegate.queueInputFrame(glObjectsProvider, inputTexture, presentationTimeNs)
	}

	override fun releaseOutputFrame(outputFrame: GlTextureInfo) {
		delegate.releaseOutputFrame(outputFrame)
	}

	override fun signalEndOfCurrentInputStream() {
		delegate.signalEndOfCurrentInputStream()
	}

	override fun flush() {
		delegate.flush()
	}

	override fun release() {
		delegate.release()
		lastCapture?.recycle()
		lastCapture = null
		deleteOwnedFbo()
	}

	/**
	 * The framebuffer holding [texture]. A texture that arrived without one gets an FBO we own
	 * and keep, because creating one per capture would churn GL objects every handoff.
	 */
	private fun framebufferFor(texture: GlTextureInfo): Int {
		if (texture.fboId != 0) return texture.fboId
		if (ownedFboId != 0 && ownedTexId == texture.texId) return ownedFboId
		deleteOwnedFbo()
		ownedFboId = GlUtil.createFboForTexture(texture.texId)
		ownedTexId = texture.texId
		return ownedFboId
	}

	private fun deleteOwnedFbo() {
		if (ownedFboId != 0) runCatching { GlUtil.deleteFbo(ownedFboId) }
		ownedFboId = 0
		ownedTexId = 0
	}

	/**
	 * Reads the texture this frame is bound to. Everything is inside [runCatching] because this
	 * runs on the GL thread inside the render path, where throwing would take the player down
	 * rather than just losing a still.
	 */
	private fun readBack(texture: GlTextureInfo) {
		var bitmap: Bitmap? = null
		runCatching { framebufferFor(texture) }
			.mapCatching { framebuffer -> readBackOrNull(texture, framebuffer) }
			.onSuccess { bitmap = it }
			.onFailure {
				// Swallowed deliberately below - this runs on the GL thread inside the render
				// path, where throwing would take the player down rather than losing a still.
				// Logged here so a tap that never works is visible instead of silent.
				Log.w(TAG, "tap: readback failed", it)
			}
		if (bitmap == null) Log.w(TAG, "tap: no bitmap for ${texture.width}x${texture.height}")
		lastCapture = bitmap
		captured.complete(Unit)
	}

	private fun readBackOrNull(
		texture: GlTextureInfo,
		framebufferId: Int,
	): Bitmap? {
		val width = texture.width
		val height = texture.height
		if (width <= 0 || height <= 0) return null

		GlUtil.focusFramebufferUsingCurrentContext(framebufferId, width, height)
		GLES30.glReadBuffer(GLES30.GL_COLOR_ATTACHMENT0)
		val raw = createBitmap(width, height)
		val pixels =
			ByteBuffer
				.allocateDirect(raw.byteCount)
				.order(ByteOrder.nativeOrder())
		GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pixels)
		GlUtil.checkGlError()
		// rewind, not flip. flip sets limit to the current position, and the position is still 0
		// because glReadPixels writes through a direct address rather than advancing it - so flip
		// left remaining() at 0 and copyPixelsFromBuffer rejected the readback every time with
		// "Buffer not large enough for pixels". Rewind puts the position back to 0 and leaves the
		// limit alone, which is what a buffer GL has just filled actually needs.
		pixels.rewind()

		raw.copyPixelsFromBuffer(pixels)
		// GL reads bottom-up, the bitmap is top-down.
		return flipVertically(raw)
	}

	/**
	 * GL reads from the bottom-left of the framebuffer, a [Bitmap] addresses rows from the top,
	 * so the readback comes out mirrored vertically.
	 */
	private fun flipVertically(source: Bitmap): Bitmap {
		val flipped = createBitmap(source.width, source.height)
		Canvas(flipped).apply {
			// Scale by -1 about the vertical midpoint to turn the readback the right way up.
			scale(1f, -1f, 0f, source.height / 2f)
			drawBitmap(source, 0f, 0f, null)
		}
		source.recycle()
		return flipped
	}
}
