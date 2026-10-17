package com.neilturner.fauxfade.ui.player

import android.graphics.Bitmap

/**
 * How the still is taken from the player that is about to be swapped out.
 *
 * Chosen on the startup screen alongside the quality, and locked for the run the way the quality
 * is. They are the same kind of decision - both change what a segment costs - and the numbers from
 * one are only meaningful next to the other.
 *
 * [label] and [supporting] exist because the names alone do not say enough to pick from: two of
 * these read pixels out of the player's render path and two do not, and on a slow device that is
 * the difference that matters.
 */
enum class StillCaptureMode(
	val label: String,
	val supporting: String,
) {
	/**
	 * The original tap, installed on both players for the whole run.
	 *
	 * Cheapest per capture - a frame is read back the moment it is asked for, with no pipeline to
	 * build first - and by far the most expensive per frame, because the effect stays in the render
	 * path for every frame of every clip. This is what made 4K miss its handoff budget.
	 */
	ALWAYS_ON_TAP(
		"Tap",
		"Reads from the video pipeline, left installed for the whole run.",
	),

	/**
	 * [ScheduledFrameCapture]: the tap is installed a fixed lead time before the frame is wanted
	 * and removed as soon as it has been taken.
	 *
	 * The idea is sound and the code works - the effect does get installed, and the processor does
	 * see frames - but on this device it does not produce a still, because **installing a video
	 * effect on a playing player does not merely hitch it, it stops it**.
	 *
	 * Measured, 1080p SDR, three consecutive segments: the install was scheduled for 8750ms and
	 * each segment's outgoing player ended at 8748 / 8742 / 8739ms of media time. Playback did not
	 * resume within the segment - during the 516ms the capture waited on top of the 1250ms the
	 * player had already lost, the position moved by 3ms. The target was 9750ms, so it was never
	 * reached, the capture timed out every time, and every handoff degraded to a cut.
	 *
	 * So a longer lead time does not help - it makes things worse, because the stall starts when
	 * the effect goes in and lasts for however long the pipeline takes to build. This cannot work
	 * against a wall-clock-driven segment: losing media time means the media-time target is never
	 * reached before the segment boundary arrives. It would need either a segment long enough to
	 * absorb the stall, or a target that is not in media time at all.
	 *
	 * Left in because the mechanism is sound and may behave differently on a device where the
	 * pipeline comes up quickly, but it is not the default for that reason.
	 */
	SCHEDULED_EFFECT(
		"Timed effect",
		"The same pipeline, installed just before the frame. Stops the player.",
	),

	/**
	 * [RetrieverFrameCapture]: no effects pipeline at all. The player is paused at the target time
	 * and the frame is read separately.
	 *
	 * Nothing is ever in front of a frame, so there is no pipeline cost and no stall - which is
	 * exactly what makes this the more promising of the two alternatives, since the failure above
	 * is the pipeline itself rather than anything to do with scheduling. What it costs instead is
	 * reopening and seeking the file, which for a network source means fetching from the start of
	 * it. Also unverified on this catalogue: the retriever runs in the media stack, where the
	 * app's cleartext policy may not apply.
	 */
	METADATA_RETRIEVER(
		"Retriever",
		"No pipeline. Reopens the file and seeks, which takes seconds.",
	),

	/**
	 * PixelCopy: no pipeline at all, and nothing reads the player. The player is paused and the
	 * surface is copied out of band, so the still is whatever was on screen when it stopped.
	 *
	 * The only mode that reads a surface rather than the player, and the only one that changes the
	 * *order* of the handoff - it can copy after the pause, where every other mode has to copy
	 * before it, because a surface keeps its last latched buffer while a paused player emits no
	 * frames at all. That property is the reason it is here: it is what lets the handoff read
	 * pause, copy, swap, fade instead of arming something in advance and hoping.
	 *
	 * Measured in the handoff at ~100ms with two players and ~400ms with one, against 96-149ms for
	 * the tap - so on latency it is a wash. What it wins on is having no pipeline at all, which is
	 * why it is also the only mode that works with a single player, and so the only way to crossfade
	 * at 4K on a device that will not give a second decoder.
	 *
	 * The still is the size of the surface, so it is bounded by the display rather than by the
	 * clip. High dynamic range stills are copied into a half-float and tagged BT.2020 PQ, because
	 * copying HDR into 8-bit does not tone-map it and comes out magenta and half as bright.
	 */
	PIXEL_COPY(
		"PixelCopy",
		"No pipeline. Copies the screen, and survives the pause.",
	),
}

/**
 * How the loop gets a frame out of a slot's surface.
 *
 * The loop lives in the view model and the surfaces live in the view, and a view model holding a
 * `View` is a thing that outlives the view and keeps it alive. So the view model is handed a
 * function instead: it can ask for a frame without ever learning what a `SurfaceView` is. The
 * implementation is built and owned by the player screen, which is the only thing that has the
 * views.
 */
internal fun interface SurfaceGrabber {
	/**
	 * A copy of what [slot]'s surface is currently showing, or null if there was none.
	 *
	 * [target] is the shape the caller wants the bitmap in. It is passed rather than chosen here
	 * because only the caller knows whether the run is high dynamic range, and getting that wrong
	 * is what produces a still with the wrong colour rather than one that merely looks softer.
	 *
	 * Suspending because the copy is: PixelCopy is an asynchronous request, and whether it
	 * completes is not the caller's decision.
	 */
	suspend fun grab(
		slot: PlayerSlot,
		target: PixelCopyCapture.CopyTarget,
	): Bitmap?
}
