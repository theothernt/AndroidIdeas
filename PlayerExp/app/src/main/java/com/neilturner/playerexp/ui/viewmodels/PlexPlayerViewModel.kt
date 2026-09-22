package com.neilturner.playerexp.ui.viewmodels

import android.app.Application
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.neilturner.playerexp.data.plex.PlexAccountStore
import com.neilturner.playerexp.data.plex.PlexApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Immutable
sealed interface PlexPlayerUiState {
    data object NotAuthorised : PlexPlayerUiState
    data object Loading : PlexPlayerUiState
    data class Ready(val episodeTitle: String) : PlexPlayerUiState
    data class Error(val message: String) : PlexPlayerUiState
}

class PlexPlayerViewModel(
    application: Application
) : AndroidViewModel(application) {
    private val store = PlexAccountStore(application.applicationContext)
    private val api = PlexApi(store.clientIdentifier())

    private val _uiState = MutableStateFlow<PlexPlayerUiState>(PlexPlayerUiState.Loading)
    val uiState: StateFlow<PlexPlayerUiState> = _uiState.asStateFlow()

    var player: ExoPlayer? by mutableStateOf(null)
        private set

    init {
        loadAndPlay()
    }

    fun loadAndPlay() {
        val token = store.accountToken()
        if (token == null) {
            _uiState.value = PlexPlayerUiState.NotAuthorised
            return
        }

        _uiState.value = PlexPlayerUiState.Loading

        viewModelScope.launch {
            try {
                // If server URL wasn't cached yet (e.g. from prior auth), fetch and store it
                var serverUrl = store.serverUrl()
                if (serverUrl == null) {
                    val info = api.serverInfo(token)
                    serverUrl = info?.uri
                    if (serverUrl != null) {
                        store.saveLinkedAccount(token, info.name, serverUrl)
                    }
                }

                if (serverUrl == null) {
                    _uiState.value = PlexPlayerUiState.Error("No Plex server URL found. Please check Plex settings.")
                    return@launch
                }

                val showSections = api.tvShowSections(serverUrl, token)
                if (showSections.isEmpty()) {
                    _uiState.value = PlexPlayerUiState.Error("No TV Show libraries found on your Plex server.")
                    return@launch
                }

                // Query the first TV show section for recently added episodes (up to 10)
                val sectionKey = showSections.first().key
                val episodes = api.recentEpisodes(serverUrl, token, sectionKey, limit = 10)
                if (episodes.isEmpty()) {
                    _uiState.value = PlexPlayerUiState.Error("No TV show episodes found in the library.")
                    return@launch
                }

                // Pick one random episode
                val selectedEpisode = episodes.random()
                val streamUrl = "${serverUrl.trimEnd('/')}${selectedEpisode.partKey}?X-Plex-Token=$token"

                val context = getApplication<Application>().applicationContext
                val p = player ?: ExoPlayer.Builder(context).build().also { player = it }

                val mediaItem = MediaItem.fromUri(streamUrl)
                p.setMediaItem(mediaItem)
                p.prepare()
                p.playWhenReady = true

                val title = listOfNotNull(selectedEpisode.showTitle, selectedEpisode.episodeTitle)
                    .joinToString(" - ")
                    .ifEmpty { "Plex Episode" }

                _uiState.value = PlexPlayerUiState.Ready(title)
            } catch (e: Exception) {
                _uiState.value = PlexPlayerUiState.Error(e.message ?: "Failed to connect to Plex server")
            }
        }
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
    }

    override fun onCleared() {
        releasePlayer()
        api.close()
        super.onCleared()
    }
}
