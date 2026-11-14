package com.neilturner.musicfun.data

import android.graphics.Bitmap

/**
 * Metadata for the track a media app is currently playing.
 *
 * @param packageName package that posted the media notification, e.g. `com.spotify.music`.
 * @param artwork decoded cover from `MediaMetadata.METADATA_KEY_ART`.
 * @param artworkUri cover URI from `MediaMetadata.METADATA_KEY_ART_URI`, if the app sent one.
 */
data class MusicMetadata(
	val packageName: String,
	val title: String?,
	val artist: String?,
	val album: String?,
	val artwork: Bitmap? = null,
	val artworkUri: String? = null,
)