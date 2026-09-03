package com.neilturner.playerexp.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface PlayerUiState {
    object Idle : PlayerUiState
    data class Ready(val title: String) : PlayerUiState
}

class PlayerViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<PlayerUiState>(PlayerUiState.Idle)
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    lateinit var player: ExoPlayer

    fun initialize(mediaUri: String, title: String) {
        if (this::player.isInitialized) return

        val context = getApplication<Application>().applicationContext

        player = ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(mediaUri))
            prepare()
            playWhenReady = true
        }

        _uiState.value = PlayerUiState.Ready(title)
    }

    override fun onCleared() {
        if (this::player.isInitialized) {
            player.release()
        }
        super.onCleared()
    }
}
