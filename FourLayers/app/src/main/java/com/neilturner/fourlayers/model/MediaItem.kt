package com.neilturner.fourlayers.model

/**
 * Represents a media item in the playlist.
 */
sealed class MediaItem {
    abstract val duration: Long

    data class Image(
        val url: String,
        override val duration: Long = 5000L // Default 5 seconds
    ) : MediaItem()

    data class Video(
        val url: String,
        override val duration: Long = 5000L // Default 5 seconds playback
    ) : MediaItem()
}
