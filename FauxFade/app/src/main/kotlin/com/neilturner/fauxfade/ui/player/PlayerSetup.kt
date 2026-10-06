package com.neilturner.fauxfade.ui.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer

/**
 * Builds one player per [PlayerSlot], both configured the same way.
 *
 * Kept out of the view model because how a player is built is not view state: it happens once,
 * up front, and nothing about it changes while the loop runs.
 */
@OptIn(UnstableApi::class)
internal fun createPlayerSlots(context: Context): Map<PlayerSlot, ExoPlayer> =
	PlayerSlot.entries.associateWith {
		ExoPlayer.Builder(context).setLoadControl(bufferLimit()).build()
	}

/**
 * Caps how much compressed media the loader holds.
 *
 * The default buffers by duration alone - up to 50s of a 2K clip, per player, with two players
 * live. [DefaultLoadControl.Builder.setTargetBufferBytes] is ignored unless
 * [DefaultLoadControl.Builder.setPrioritizeTimeOverSizeThresholds] is turned off, which is the
 * part that actually makes it bite.
 *
 * The byte cap is what limits memory; the durations are kept generous enough that the preloaded
 * next video still starts instantly, because the handoff depends on it being ready the moment
 * the still comes down.
 */
@OptIn(UnstableApi::class)
private fun bufferLimit() =
	DefaultLoadControl
		.Builder()
		.setBufferDurationsMs(
			MIN_BUFFER_MS,
			MAX_BUFFER_MS,
			BUFFER_FOR_PLAYBACK_MS,
			BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
		).setTargetBufferBytes(TARGET_BUFFER_BYTES)
		.setPrioritizeTimeOverSizeThresholds(false)
		.build()

/**
 * Load control limits. The defaults buffer up to 50s of a 2K clip per player and ignore the
 * byte target, which is far more than a loop that plays 10s at a time needs.
 *
 * The durations stay generous on purpose: the next video is primed mid-segment so the handoff
 * has something to show immediately, and starving it would show up as a stall at the swap.
 */
private const val MIN_BUFFER_MS = 3_000
private const val MAX_BUFFER_MS = 10_000
private const val BUFFER_FOR_PLAYBACK_MS = 2_500
private const val BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 2_000

/** Cap on buffered compressed media, per player. */
private const val TARGET_BUFFER_BYTES = 24 * 1024 * 1024
