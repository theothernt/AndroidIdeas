package com.neilturner.fauxfade.core.player

/** Which half of a feature is up: the wait before playback, or playback itself. */
enum class AppPhase {
	/** The feature has been entered but playback has not begun. */
	PICKING,

	/** The video loop. */
	PLAYING,
}
