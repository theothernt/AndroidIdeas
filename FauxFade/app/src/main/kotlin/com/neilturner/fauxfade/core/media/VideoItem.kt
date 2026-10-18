package com.neilturner.fauxfade.core.media

import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi

/**
 * One clip, and how to hand it to a player.
 *
 * No mime type is declared on purpose. The clips are a mix of QuickTime and MP4 containers, and
 * stating the wrong one is worse than stating none - the player is better at identifying the
 * container from the bytes than from a label attached to a URL.
 */
@OptIn(UnstableApi::class)
data class VideoItem(
	val title: String,
	val uri: String,
) {
	fun toMediaItem(): MediaItem =
		MediaItem
			.Builder()
			.setUri(uri)
			.setMediaId(title)
			.build()
}
