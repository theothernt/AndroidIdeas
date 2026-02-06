package com.neilturner.fourlayers.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.upstream.DefaultAllocator
import com.neilturner.fourlayers.model.LayerState
import com.neilturner.fourlayers.model.MediaItem
import com.neilturner.fourlayers.model.PlaybackState
import com.neilturner.fourlayers.model.PlayerState
import com.neilturner.fourlayers.model.RendererType
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel managing the media playlist playback with dual permanent renderers (A/B).
 */
class MediaPlaylistViewModel(
    private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    // Timing constants
    private val transitionDuration = 2000L // ms for fade

    // Jobs for cancellation
    private var imageTimerJob: Job? = null
    private var videoMonitorJob: Job? = null
    private var ramMonitorJob: Job? = null

    fun loadPlaylist(items: List<MediaItem>) {
        _state.update { it.copy(playlist = items) }
        if (items.isNotEmpty()) {
            startPlayback()
            startRamMonitoring()
        }
    }

    fun toggleFade() {
        _state.update { it.copy(isFadeEnabled = !it.isFadeEnabled) }
    }

    fun onMediaReady() {
        if (_state.value.isLoading) {
             _state.update { it.copy(isLoading = false) }
        }
    }

    private fun startPlayback() {
        val firstItem = _state.value.playlist.firstOrNull() ?: return

        // Start with Renderer A active
        val initialRenderer = RendererType.RendererA
        
        val layerState = when (firstItem) {
            is MediaItem.Image -> {
                 LayerState.ShowingImage(firstItem)
            }
            is MediaItem.Video -> {
                val player = createAndPreparePlayer(firstItem)
                
                // Detect first frame rendering to dismiss loading screen
                player.addListener(object : Player.Listener {
                    override fun onRenderedFirstFrame() {
                        onMediaReady()
                        player.removeListener(this)
                    }
                })
                
                player.playWhenReady = true
                monitorVideoProgress(player, 0, firstItem.duration)
                LayerState.ShowingVideo(firstItem, player)
            }
        }

        _state.update {
            it.copy(
                playbackState = PlaybackState.Playing(0),
                activeRenderer = initialRenderer,
                rendererA = layerState,
                rendererB = LayerState.Empty // Ensure B is empty
            )
        }
        
        if (firstItem is MediaItem.Image) {
             scheduleImageTransition(firstItem.duration)
        }

        // Schedule preload for the next item (into the inactive renderer B)
        schedulePreload(1, firstItem.duration)
    }

    private fun scheduleImageTransition(duration: Long) {
        imageTimerJob?.cancel()
        imageTimerJob = viewModelScope.launch {
            delay(duration)
            triggerTransition()
        }
    }

    private fun monitorVideoProgress(player: ExoPlayer, itemIndex: Int, targetDurationMs: Long) {
        videoMonitorJob?.cancel()
        videoMonitorJob = viewModelScope.launch {
            while (true) {
                delay(100) // Check every 100ms

                val currentPos = player.currentPosition
                val contentDuration = player.duration

                val timeLeftInTarget = targetDurationMs - currentPos
                val timeLeftInContent = if (contentDuration > 0) contentDuration - currentPos else Long.MAX_VALUE
                val effectiveTimeRemaining = minOf(timeLeftInTarget, timeLeftInContent)

                if (effectiveTimeRemaining <= 0 || player.playbackState == Player.STATE_ENDED) {
                    // Do NOT pause here for FadeLoop style seamlessness, 
                    // or pause if we want to freeze the last frame. 
                    // Freezing is better for fading out.
                    player.pause() 
                    triggerTransition()
                    break
                }
            }
        }
    }

    private fun schedulePreload(nextIndex: Int, currentDuration: Long) {
        viewModelScope.launch {
            // Preload 4 seconds before current item ends
            val delayMs = maxOf(0L, currentDuration - 4000L)
            delay(delayMs)
            preloadNextItem(nextIndex)
        }
    }

    private fun preloadNextItem(nextIndex: Int) {
        val currentState = _state.value
        val playlist = currentState.playlist
        if (playlist.isEmpty()) return

        val normalizedIndex = nextIndex % playlist.size
        val nextItem = playlist[normalizedIndex]
        
        // Determine target (inactive) renderer
        val targetRendererType = if (currentState.activeRenderer == RendererType.RendererA) 
            RendererType.RendererB else RendererType.RendererA

        // Clean up any existing content in the target renderer
        val oldLayer = if (targetRendererType == RendererType.RendererA) currentState.rendererA else currentState.rendererB
        if (oldLayer is LayerState.ShowingVideo) {
            oldLayer.player.release()
        }
        
        val newLayer = when (nextItem) {
            is MediaItem.Image -> LayerState.ShowingImage(nextItem)
            is MediaItem.Video -> {
                val player = createAndPreparePlayer(nextItem)
                // Do not play yet. Wait for transition trigger.
                player.playWhenReady = false 
                LayerState.ShowingVideo(nextItem, player, isPlaying = false)
            }
        }
        
        _state.update {
            if (targetRendererType == RendererType.RendererA) {
                it.copy(rendererA = newLayer)
            } else {
                it.copy(rendererB = newLayer)
            }
        }
    }

    private fun triggerTransition() {
        val currentState = _state.value
        val currentIndex = when (val ps = currentState.playbackState) {
            is PlaybackState.Playing -> ps.itemIndex
            else -> return
        }

        val nextIndex = (currentIndex + 1) % currentState.playlist.size

        // Start playback on the upcoming renderer (which is currently inactive/hidden)
        val nextRendererType = if (currentState.activeRenderer == RendererType.RendererA) RendererType.RendererB else RendererType.RendererA
        val nextLayer = if (nextRendererType == RendererType.RendererA) currentState.rendererA else currentState.rendererB
        
        if (nextLayer is LayerState.ShowingVideo) {
            nextLayer.player.playWhenReady = true
        }

        // Start transition
        _state.update {
            it.copy(
                playbackState = PlaybackState.Transitioning(
                    fromIndex = currentIndex,
                    toIndex = nextIndex,
                    progress = 0f
                ),
                transitionTrigger = System.currentTimeMillis()
            )
        }

        val duration = if (currentState.isFadeEnabled) transitionDuration else 0L
        
        viewModelScope.launch {
            delay(duration)
            completeTransition(nextIndex)
        }
    }

    private fun completeTransition(newIndex: Int) {
        val currentState = _state.value
        val oldActiveRenderer = currentState.activeRenderer
        
        // Flip active renderer
        val newActiveRenderer = if (oldActiveRenderer == RendererType.RendererA) 
            RendererType.RendererB else RendererType.RendererA

        // Clean up the renderer that we just faded OUT (oldActiveRenderer)
        val oldLayer = if (oldActiveRenderer == RendererType.RendererA) currentState.rendererA else currentState.rendererB
        if (oldLayer is LayerState.ShowingVideo) {
            oldLayer.player.release()
        }

        _state.update {
            it.copy(
                playbackState = PlaybackState.Playing(newIndex),
                activeRenderer = newActiveRenderer,
                // Set the old renderer to Empty
                rendererA = if (oldActiveRenderer == RendererType.RendererA) LayerState.Empty else it.rendererA,
                rendererB = if (oldActiveRenderer == RendererType.RendererB) LayerState.Empty else it.rendererB
            )
        }

        // Start monitoring the NEW active item
        val newLayer = if (newActiveRenderer == RendererType.RendererA) _state.value.rendererA else _state.value.rendererB
        val newItem = currentState.playlist[newIndex]

        when (newLayer) {
            is LayerState.ShowingVideo -> {
                // Ensure it's playing (might have been paused if we were holding it)
                // But preloaded videos are already playing.
                newLayer.player.playWhenReady = true
                monitorVideoProgress(newLayer.player, newIndex, newItem.duration)
            }
            is LayerState.ShowingImage -> {
                 scheduleImageTransition((newItem as MediaItem.Image).duration)
            }
            else -> {
                // Should not happen if logic is correct
                // If it does, try to recover or just wait
                if (newItem is MediaItem.Image) {
                    scheduleImageTransition(newItem.duration)
                }
            }
        }

        // Schedule preload for upcoming item
        schedulePreload(newIndex + 1, newItem.duration)
    }

	@OptIn(UnstableApi::class)
	private fun createLoadControl(): LoadControl {
        val builder = DefaultLoadControl.Builder()
        val allocator = DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE)
        builder.setAllocator(allocator)
        builder.setBufferDurationsMs(
            2000,
            10000,
            500,
            1000
        )
        return builder.build()
    }

	@OptIn(UnstableApi::class)
	private fun createAndPreparePlayer(item: MediaItem.Video): ExoPlayer {
        return ExoPlayer.Builder(context)
            .setLoadControl(createLoadControl())
            .build()
            .apply {
                // ExoPlayer manages its own volume/audio focus
                repeatMode = Player.REPEAT_MODE_OFF 
                volume = 0f // Mute video
                val mediaItem = androidx.media3.common.MediaItem.fromUri(item.url)
                setMediaItem(mediaItem)
                prepare()
            }
    }

    private fun startRamMonitoring() {
        ramMonitorJob?.cancel()
        ramMonitorJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                val runtime = Runtime.getRuntime()
                val usedMemInBytes = runtime.totalMemory() - runtime.freeMemory()
                val usedMemInMb = usedMemInBytes / (1024 * 1024)
                _state.update { it.copy(ramUsageMb = usedMemInMb) }
            }
        }
    }

    private fun cleanup() {
        imageTimerJob?.cancel()
        videoMonitorJob?.cancel()
        ramMonitorJob?.cancel()
        
        // Clean up both renderers
        val state = _state.value
        val layers = listOf(state.rendererA, state.rendererB)
        layers.forEach { layer ->
            if (layer is LayerState.ShowingVideo) {
                layer.player.release()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        cleanup()
    }
}
