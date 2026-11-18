package com.neilturner.fauxfade.core.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import com.neilturner.fauxfade.core.media.Quality

/**
 * Builds one player per slot in [slots], all configured the same way.
 *
 * Taken at playback start rather than at construction because the buffer cap depends on the
 * quality, and the quality is not known until the picker has been answered. Nothing about the
 * player changes after this, so building it here rather than in a view model's initialiser
 * keeps "how a player is built" out of the object's construction path.
 *
 * [slots] rather than "all of them" so a single-player mode can ask for one and get one. A second
 * player is not free: it initialises its own decoder, and at 4K on a weak device that is the
 * difference between a picture and a black screen.
 */
@OptIn(UnstableApi::class)
internal fun createPlayerSlots(
	context: Context,
	quality: Quality,
	slots: List<PlayerSlot>,
): Map<PlayerSlot, ExoPlayer> =
	slots.associateWith {
		ExoPlayer.Builder(context).setLoadControl(bufferLimit(quality)).build()
	}

/**
 * Caps how much compressed media the loader holds.
 *
 * The default buffers by duration alone - up to 50s of a 2K clip, per player, with two players
 * live. [DefaultLoadControl.Builder.setTargetBufferBytes] is ignored unless
 * [DefaultLoadControl.Builder.setPrioritizeTimeOverSizeThresholds] is turned off, which is the
 * part that actually makes it bite.
 *
 * The byte cap comes from the quality because a 4K stream blows through a 1080p-sized budget in
 * a couple of seconds, and the preload that the handoff depends on is the first casualty. The
 * durations are kept generous enough that the preloaded next video still starts instantly,
 * because the handoff needs it ready the moment the still comes down.
 */
@OptIn(UnstableApi::class)
private fun bufferLimit(quality: Quality) =
	DefaultLoadControl
		.Builder()
		.setBufferDurationsMs(
			MIN_BUFFER_MS,
			MAX_BUFFER_MS,
			BUFFER_FOR_PLAYBACK_MS,
			BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
		).setTargetBufferBytes(quality.bufferBytes)
		.setPrioritizeTimeOverSizeThresholds(false)
		.build()

/** Load control limits. The defaults are far more than a loop playing short segments needs. */
private const val MIN_BUFFER_MS = 3_000
private const val MAX_BUFFER_MS = 10_000
private const val BUFFER_FOR_PLAYBACK_MS = 2_500
private const val BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 2_000
