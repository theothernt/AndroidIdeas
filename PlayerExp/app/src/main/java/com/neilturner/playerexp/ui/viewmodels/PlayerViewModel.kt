package com.neilturner.playerexp.ui.viewmodels

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import com.neilturner.playerexp.data.MediaProvider
import com.neilturner.playerexp.ui.player.AssSubtitleSupport
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

    /** The styled ASS subtitles, when the file has any. */
    val assSupport = AssSubtitleSupport(application.applicationContext)

    var player: ExoPlayer? by mutableStateOf(null)
        private set

    fun initialize(mediaId: String) {
        val source = MediaProvider.getSource(mediaId) ?: return
        val context = getApplication<Application>().applicationContext
        val p = player ?: assSupport.buildPlayer(
            builder = ExoPlayer.Builder(context),
            dataSourceFactory = DefaultDataSource.Factory(context)
        ).also {
            player = it
            it.addListener(object : Player.Listener {
                override fun onTracksChanged(tracks: Tracks) {
                    selectFirstTextTrack(it, tracks)
                }
            })
        }

        val mediaItem = MediaItem.fromUri(source.uri)

        p.setMediaItem(mediaItem)
        p.prepare()
        p.playWhenReady = true

        _uiState.value = PlayerUiState.Ready(source.title)
    }

    /**
     * Switches on the first text track in the file.
     *
     * The ASS track in the test file is not marked as the default one, so the player would
     * otherwise leave it switched off and there would be nothing to look at.
     */
    private fun selectFirstTextTrack(player: Player, tracks: Tracks) {
        val textGroup = tracks.groups.firstOrNull { it.type == C.TRACK_TYPE_TEXT } ?: return
        if (textGroup.length == 0) return
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .setOverrideForType(TrackSelectionOverride(textGroup.mediaTrackGroup, 0))
            .build()
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
        assSupport.release()
        super.onCleared()
    }
}
