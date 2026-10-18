package com.neilturner.fauxfade.feature.pixelcopy

import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.PixelCopy
import android.view.SurfaceView
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Copies what a [SurfaceView] is currently showing into a bitmap, with no video pipeline
 * involved, and the question that made it the default capture path: **can this copy from a paused
 * player?**
 *
 * That matters more than anything else about the approach. Every other still mechanism in this app
 * has been forced to read a frame *before* pausing, because a paused player produces no frames:
 * the tap has to be armed before the pause, the scheduled effect has to run ahead, and the
 * retriever has to pause and then wait two to three seconds for the file.
 *
 * PixelCopy is not reading the player at all - it is reading the surface, and a surface keeps its
 * last latched buffer after the producer stops. It does hold: the handoff can pause first and copy
 * afterwards, which is the natural order and removes all three problems above.
 *
 * Copied from the [SurfaceView] rather than the window on purpose: the window also contains the
 * readout chips and the quality label, and a still with the user interface baked into it is not a
 * still.
 */
internal class PixelCopyCapture {
	/**
	 * The shape of the bitmap a copy is written into.
	 *
	 * This is the whole of the HDR problem, and it is one line to get wrong.
	 *
	 * An 8-bit destination does not tone-map. Copying high dynamic range into `ARGB_8888` hands
	 * back values that are then read as sRGB, which is measurably wrong rather than subtly wrong -
	 * an underwater reef comes out with magenta coral and green water, and underexposed besides.
	 * Shown as a still under a crossfade that is a flash of the wrong colour every segment.
	 *
	 * `RGBA_F16` keeps the values in a shape that can carry the range, tagged with the colour space
	 * they are actually in, and lets the framework convert when it draws. Whether it converts them
	 * *the same way the video layer does* is the thing that has to be checked on the set rather
	 * than assumed - it is a better guess than the alternative, not a proof.
	 */
	enum class CopyTarget(
		val config: Bitmap.Config,
		val colorSpace: ColorSpace,
	) {
		/** Ordinary video. Nothing to convert, so the obvious destination. */
		SDR(Bitmap.Config.ARGB_8888, ColorSpace.get(ColorSpace.Named.SRGB)),

		/**
		 * HDR video. Half-float so values above 1.0 survive, and tagged BT.2020 PQ because that is
		 * what the Dolby Vision profiles the catalogue carries are encoded with.
		 */
		HDR(Bitmap.Config.RGBA_F16, ColorSpace.get(ColorSpace.Named.BT2020_PQ)),
	}

	/**
	 * One copy, or the reason there wasn't one.
	 *
	 * A failure is not exceptional here - the surface can legitimately have nothing in it - so the
	 * status is carried rather than thrown, and the caller decides whether to cut instead.
	 */
	class Result(
		val status: Int,
		val bitmap: Bitmap?,
		val elapsedMs: Long,
		/** True when the surface was larger than [MAX_STILL_WIDTH] and the copy was shrunk. */
		val scaled: Boolean = false,
	) {
		val succeeded: Boolean get() = status == PixelCopy.SUCCESS && bitmap != null
	}

	/**
	 * Copies the whole surface. Suspends until the copy completes.
	 *
	 * The destination is sized to the view rather than the video, which is deliberate: a still only
	 * ever has to cover the screen, so this bounds the copy at the display's resolution instead of
	 * the clip's. A 4K clip on a 1080p display costs the same as a 1080p one.
	 *
	 * The destination's config comes from [target], which is [CopyTarget.HDR] for HDR video:
	 * see [CopyTarget] for why an 8-bit destination tone-maps and so cannot match the layer it
	 * covers.
	 */
	suspend fun capture(
		surfaceView: SurfaceView,
		target: CopyTarget,
	): Result =
		suspendCancellableCoroutine { continuation ->
			val surfaceWidth = surfaceView.width
			val surfaceHeight = surfaceView.height
			if (surfaceWidth <= 0 || surfaceHeight <= 0) {
				continuation.resume(Result(PixelCopy.ERROR_SOURCE_INVALID, null, 0))
				return@suspendCancellableCoroutine
			}

			// Aspect preserved, and only ever shrinking: a still larger than the screen it is shown
			// on cannot be justified by anything. On a 1080p panel this is a no-op, because the
			// surface is already 1920 wide - it earns its keep on a 4K output, where the copy would
			// otherwise be four times the pixels for an image that is scaled back down to fit.
			val shrink = min(1f, MAX_STILL_WIDTH.toFloat() / surfaceWidth)
			val width = (surfaceWidth * shrink).roundToInt().coerceAtLeast(1)
			val height = (surfaceHeight * shrink).roundToInt().coerceAtLeast(1)

			val started = SystemClock.elapsedRealtime()
			val bitmap = Bitmap.createBitmap(width, height, target.config)
			// Tagged so the framework knows what the values are rather than assuming sRGB. Only
			// meaningful for the float destination - an 8-bit bitmap in BT.2020 would still be read
			// through the wrong transfer function, which is the bug this whole enum exists to avoid.
			if (target.config == Bitmap.Config.RGBA_F16) {
				// Called as a method rather than through a property: the accessor pair does not
				// resolve to a var here, and the guard stays because setColorSpace throws rather
				// than returning false if the bitmap cannot be tagged.
				runCatching { bitmap.setColorSpace(target.colorSpace) }
			}
			// The view's own looper when it has one, so the callback lands where the rest of the
			// player's work already does rather than on a thread of its own.
			val handler = surfaceView.handler ?: Handler(Looper.getMainLooper())

			PixelCopy.request(
				surfaceView,
				null,
				bitmap,
				{ status ->
					val elapsed = SystemClock.elapsedRealtime() - started
					continuation.resume(
						Result(
							status = status,
							bitmap = if (status == PixelCopy.SUCCESS) bitmap else null,
							elapsedMs = elapsed,
							scaled = shrink < 1f,
						),
					)
				},
				handler,
			)
		}
}

/**
 * Widest still worth keeping, in pixels.
 *
 * 1080p because that is what the set can show: a still is drawn over the screen and then faded
 * out, so anything above the display's own resolution is pixels that get allocated, copied and
 * uploaded only for the drawing to scale them back down. The other half of the saving is free -
 * the copy is the size of the surface rather than the size of the video, so a 4K clip on a 1080p
 * set already produces a 1080p bitmap.
 *
 * Private rather than shared. It was one constant between this and the retriever, and it is now a
 * constant in each, because sharing it would mean the two capture paths importing from each other
 * and neither being readable on its own. **The two copies have to stay equal**: a still only ever
 * has to cover the screen, and two paths that disagree about how wide that is would produce
 * stills of different sizes depending only on which one was picked.
 */
private const val MAX_STILL_WIDTH = 1920
