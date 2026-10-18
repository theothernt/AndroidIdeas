package com.neilturner.fauxfade.feature.pixelcopy

import android.graphics.Bitmap

/**
 * How the loop gets a frame out of the surface.
 *
 * The loop lives in the view model and the surface lives in the view, and a view model holding a
 * `View` is a thing that outlives the view and keeps it alive. So the view model is handed a
 * function instead: it can ask for a frame without ever learning what a `SurfaceView` is. The
 * implementation is built and owned by the player screen, which is the only thing that has the view.
 *
 * No slot parameter, unlike the version this was forked from. That one keyed the grab off a
 * `PlayerSlot` because it served two players; this feature has exactly one by construction, so a
 * slot here would be a constant that could never vary and would only suggest otherwise.
 */
internal fun interface SurfaceGrabber {
	/**
	 * A copy of what the surface is currently showing, or null if there was none.
	 *
	 * [target] is the shape the caller wants the bitmap in. It is passed rather than chosen here
	 * because only the caller knows whether the run is high dynamic range, and getting that wrong
	 * is what produces a still with the wrong colour rather than one that merely looks softer.
	 *
	 * Suspending because the copy is: PixelCopy is an asynchronous request, and whether it
	 * completes is not the caller's decision.
	 */
	suspend fun grab(target: PixelCopyCapture.CopyTarget): Bitmap?
}
