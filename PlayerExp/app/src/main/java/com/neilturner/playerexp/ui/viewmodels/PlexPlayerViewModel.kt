package com.neilturner.playerexp.ui.viewmodels

import android.util.Log
import android.app.Application
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.neilturner.playerexp.data.plex.PlexAccountStore
import com.neilturner.playerexp.data.plex.PlexApi
import com.neilturner.playerexp.data.plex.AndroidPlexCapabilityProbe
import com.neilturner.playerexp.data.plex.PlexPlaybackProfile
import com.neilturner.playerexp.data.plex.PlexPlaybackStatus
import okhttp3.OkHttpClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

@Immutable
sealed interface PlexPlayerUiState {
    data object NotAuthorised : PlexPlayerUiState
    data object Loading : PlexPlayerUiState
    data class Ready(
        val episodeTitle: String,
        val playbackStatus: PlexPlaybackStatus? = null
    ) : PlexPlayerUiState
    data class Error(val message: String) : PlexPlayerUiState
}

@UnstableApi
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
                Log.d("PlexApi", "PlayerViewModel: token present: ${token.isNotBlank()}")
                // If server URL wasn't cached yet (e.g. from prior auth), fetch and store it
                var serverUrl = store.serverUrl()
                Log.d("PlexApi", "PlayerViewModel: cached serverUrl: $serverUrl")
                if (serverUrl == null) {
                    Log.d("PlexApi", "PlayerViewModel: no cached URL, calling serverInfo()")
                    val info = api.serverInfo(token)
                    Log.d("PlexApi", "PlayerViewModel: serverInfo() returned: name=${info?.name}, uri=${info?.uri}")
                    serverUrl = info?.uri
                    if (serverUrl != null) {
                        store.saveLinkedAccount(token, info.name, serverUrl)
                        Log.d("PlexApi", "PlayerViewModel: saved linked account, serverUrl=$serverUrl")
                    }
                }

                if (serverUrl == null) {
                    Log.w("PlexApi", "PlayerViewModel: No Plex server URL found")
                    _uiState.value = PlexPlayerUiState.Error("No Plex server URL found. Please check Plex settings.")
                    return@launch
                }

                val showSections = api.tvShowSections(serverUrl, token)
                if (showSections.isEmpty()) {
                    _uiState.value = PlexPlayerUiState.Error("No TV Show libraries found on your Plex server.")
                    return@launch
                }

                // Query the first TV show section for recently added episodes (up to 10)
                val sectionKey = showSections[1].key
                val episodes = api.recentEpisodes(serverUrl, token, sectionKey, limit = 10)
                if (episodes.isEmpty()) {
                    _uiState.value = PlexPlayerUiState.Error("No TV show episodes found in the library.")
                    return@launch
                }

                // Pick one random episode
                val selectedEpisode = episodes.random()
                val capabilities = AndroidPlexCapabilityProbe.probe()
                val profile = capabilities.playbackProfile()
                Log.d(
                    "PlexApi",
                    "PlayerViewModel: advertising video=${capabilities.videoCodecs.map { it.plexName }}, " +
                        "audio=${capabilities.audioCodecs.map { it.plexName }}"
                )
                val sessionIdentifier = UUID.randomUUID().toString()
                val playbackPlan = api.playbackPlan(
                    serverUrl = serverUrl,
                    accountToken = token,
                    episode = selectedEpisode,
                    profile = profile,
                    sessionIdentifier = sessionIdentifier
                )
                Log.d(
                    "PlexApi",
                    "PlayerViewModel: playback mode=${playbackPlan::class.simpleName}, " +
                        "decision=${playbackPlan.decisionText}"
                )

                val context = getApplication<Application>().applicationContext
                // A new decision has a new Plex session and profile, so its HTTP headers must not be
                // inherited from a previous player instance.
                player?.release()
                val p = createPlayer(context, token, profile, sessionIdentifier).also { player = it }

                val title = listOfNotNull(selectedEpisode.showTitle, selectedEpisode.episodeTitle)
                    .joinToString(" - ")
                    .ifEmpty { "Plex Episode" }
                _uiState.value = PlexPlayerUiState.Ready(title)
                p.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY) {
                            _uiState.value = (_uiState.value as? PlexPlayerUiState.Ready)
                                ?.copy(playbackStatus = playbackPlan.playbackStatus)
                                ?: return
                        }
                    }
                })

                val mediaItem = MediaItem.fromUri(playbackPlan.url)
                p.setMediaItem(mediaItem)
                p.prepare()
                p.playWhenReady = true
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

    private fun createPlayer(
        context: android.content.Context,
        accountToken: String,
        profile: PlexPlaybackProfile,
        sessionIdentifier: String
    ): ExoPlayer {
        val headers = mapOf(
            "X-Plex-Client-Identifier" to store.clientIdentifier(),
            "X-Plex-Product" to "Player Exp",
            "X-Plex-Version" to "1.0",
            "X-Plex-Platform" to "Android TV",
            "X-Plex-Token" to accountToken,
            "X-Plex-Client-Profile-Name" to PlexPlaybackProfile.GENERIC_PROFILE_NAME,
            "X-Plex-Client-Profile-Extra" to profile.clientProfileExtra,
            "X-Plex-Session-Identifier" to sessionIdentifier
        )
        val upstreamFactory = OkHttpDataSource.Factory(OkHttpClient())
            .setDefaultRequestProperties(headers)
        val dataSourceFactory = DefaultDataSource.Factory(context, upstreamFactory)
        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
    }
}
