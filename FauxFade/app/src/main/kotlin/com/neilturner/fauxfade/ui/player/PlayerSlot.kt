package com.neilturner.fauxfade.ui.player

/** Which of the two players is on screen. The loop alternates between them. */
enum class PlayerSlot {
	PRIMARY,
	SECONDARY,
	;

	fun opposite(): PlayerSlot = if (this == PRIMARY) SECONDARY else PRIMARY
}

/**
 * How the still overlay is being presented.
 *
 * Sealing and fading are separate states because the still has to be fully opaque *before* the
 * surfaces change, and stay opaque until the incoming player has something to show. Fading it
 * early means most of the fade runs over a surface that has just been re-created and is still
 * empty.
 */
enum class StillPhase {
	/** Not on screen. */
	HIDDEN,

	/**
	 * Coming up from invisible, over video that is still playing underneath.
	 *
	 * The mirror image of [SEALED], which is a snap rather than a ramp: there the still is a
	 * copy of the frame the outgoing clip is already frozen on, so it has to be opaque the
	 * instant it appears or the viewer sees the original flash underneath it first. Here nothing
	 * is frozen, so arriving from nothing is the whole effect - the picture being watched turns
	 * into the next one.
	 */
	DISSOLVING,

	/** On screen at full opacity, held. */
	SEALED,

	/** Fading out over the video now underneath. */
	FADING,
}
