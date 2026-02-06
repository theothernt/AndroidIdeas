package com.neilturner.fourlayers.model

import androidx.media3.exoplayer.ExoPlayer

/**
 * Represents the overall playback state of the playlist.
 */
sealed class PlaybackState {
    data object Idle : PlaybackState()
    data class Playing(val itemIndex: Int) : PlaybackState()
    data class Transitioning(
        val fromIndex: Int,
        val toIndex: Int,
        val progress: Float = 0f
    ) : PlaybackState()
}

/**
 * Represents the state of a single media layer (current or next).
 */
sealed class LayerState {
    data object Empty : LayerState()
    data class ShowingImage(
        val item: MediaItem.Image
    ) : LayerState()
    data class ShowingVideo(
        val item: MediaItem.Video,
        val player: ExoPlayer,
        val isPlaying: Boolean = true
    ) : LayerState()
}

/**
 * Combined state for the media playlist player.
 */
data class PlayerState(
    val playbackState: PlaybackState = PlaybackState.Idle,
    val rendererA: LayerState = LayerState.Empty,
    val rendererB: LayerState = LayerState.Empty,
    val activeRenderer: RendererType = RendererType.RendererA,
    val playlist: List<MediaItem> = emptyList(),
    val ramUsageMb: Long = 0L,
    val transitionTrigger: Long = 0L, // Timestamp to trigger animation in UI
    val isFadeEnabled: Boolean = true,
    val isLoading: Boolean = true
)

enum class RendererType {
    RendererA,
    RendererB
}
