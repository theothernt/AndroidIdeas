package com.neilturner.fauxfade.core.player

/**
 * How a run takes its still, and therefore which feature runs it.
 *
 * The first three read a frame of the clip that is leaving, at the handoff, with a two-player
 * crossfade. [PRELOADED_FRAME] reads the first frame of the clip that is arriving, early, over a
 * single player. [PIXEL_COPY] copies the paused surface instead of reading the player, which is
 * why it is the only one that works at 4K on this device.
 *
 * Chosen on the startup screen alongside the quality, and locked for the run. The numbers from
 * one decision are only meaningful next to the other.
 */
enum class StillCaptureMode(
	val label: String,
	val supporting: String,
) {
	/** Effect installed on both players for the whole run. Cheapest per capture, dearest per frame. */
	ALWAYS_ON_TAP(
		"Tap",
		"Reads from the video pipeline, left installed for the whole run. Two players.",
	),

	/** Copy of the Tap feature, kept separate so blur can be developed independently. */
	TAP_BLUR(
		"Tap Blur",
		"Copy of Tap, left installed for the whole run. Two players.",
	),

	/** Effect installed just before the frame. Installing an effect stops the player. */
	SCHEDULED_EFFECT(
		"Timed effect",
		"The same pipeline, installed just before the frame. Stops the player.",
	),

	/** Reopens and seeks the file. ~2.4s at 2K; cannot read 4K or tone-map HDR. */
	METADATA_RETRIEVER(
		"Retriever",
		"No pipeline. Reopens the file and seeks, which takes seconds. Can't read 4K.",
	),

	/** Copies the paused surface out-of-band. The only approach that works at 4K here. */
	PIXEL_COPY(
		"PixelCopy",
		"No pipeline. Copies the screen, and survives the pause. Works at 4K.",
	),

	/** Copy of the PixelCopy feature, kept separate so blur can be developed independently. */
	PIXEL_COPY_BLUR(
		"PixelCopy Blur",
		"Copy of PixelCopy. Copies the screen, and survives the pause. Works at 4K.",
	),

	/** Reads the next clip's first frame early and dissolves into it while this one keeps playing. */
	PRELOADED_FRAME(
		"Preloaded frame",
		"Reads the next clip's first frame early, while this one keeps playing. 1080p only.",
	),
}
