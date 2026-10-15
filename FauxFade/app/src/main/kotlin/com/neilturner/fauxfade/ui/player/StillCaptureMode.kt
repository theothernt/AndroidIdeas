package com.neilturner.fauxfade.ui.player

/**
 * How the still is taken from the player that is about to be swapped out.
 *
 * The three do the same job and fail the same way - all of them can come back with nothing, and
 * the handoff then degrades to a plain cut - but they sit at different points on the trade-off
 * between per-frame overhead and per-capture latency. Keeping them behind one setting is the
 * point: the numbers from one are only meaningful next to the numbers from another.
 *
 * **One of these does not work here, and it is not a tuning problem.** See
 * [SCHEDULED_EFFECT] before choosing it.
 */
enum class StillCaptureMode {
	/**
	 * The original tap, installed on both players for the whole run.
	 *
	 * Cheapest per capture - a frame is read back the moment it is asked for, with no pipeline to
	 * build first - and by far the most expensive per frame, because the effect stays in the render
	 * path for every frame of every clip. This is what made 4K miss its handoff budget.
	 */
	ALWAYS_ON_TAP,

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
	SCHEDULED_EFFECT,

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
	METADATA_RETRIEVER,
}
