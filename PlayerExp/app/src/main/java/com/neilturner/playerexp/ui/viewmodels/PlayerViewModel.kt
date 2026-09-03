package com.neilturner.playerexp.ui.viewmodels

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface PlayerUiState {
    data object Idle : PlayerUiState
    data class Ready(val title: String) : PlayerUiState
}

class PlayerViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<PlayerUiState>(PlayerUiState.Idle)
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    var player: ExoPlayer? by mutableStateOf(null)
        private set

    fun initialize(mediaUri: String, title: String) {
        val context = getApplication<Application>().applicationContext
        val p = player ?: ExoPlayer.Builder(context).build().also { player = it }

        val mediaItem = MediaItem.Builder()
            .setUri(mediaUri)
            .apply {
                if (mediaUri.contains("m3u8")) {
                    setMimeType(androidx.media3.common.MimeTypes.APPLICATION_M3U8)
                }
            }
            .build()

        p.setMediaItem(mediaItem)
        p.prepare()
        p.playWhenReady = true

        _uiState.value = PlayerUiState.Ready(title)
    }

    fun pause() {
        player?.playWhenReady = false
    }

    fun resume() {
        player?.playWhenReady = true
    }

    fun releasePlayer() {
        player?.stop()
        player?.release()
        player = null
        _uiState.value = PlayerUiState.Idle
    }

    override fun onCleared() {
        releasePlayer()
        super.onCleared()
    }
}
