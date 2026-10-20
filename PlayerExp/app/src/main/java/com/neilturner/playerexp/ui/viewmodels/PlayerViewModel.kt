package com.neilturner.playerexp.ui.viewmodels

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
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

/** The id the subtitle file that sits beside the media is given. */
private const val SIDECAR_TRACK_ID = "42"

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
                    selectTextTrack(it, tracks, SIDECAR_TRACK_ID)
                }
            })
        }

        // The test file has an ASS track inside it, and a second one beside it, so that both
        // ways of arriving at a subtitle are exercised.
        val mediaItem = MediaItem.Builder()
            .setUri(source.uri)
            .setSubtitleConfigurations(listOf(sidecarSubtitle()))
            .build()

        p.setMediaItem(mediaItem)
        p.prepare()
        p.playWhenReady = true

        _uiState.value = PlayerUiState.Ready(source.title)
    }

    /**
     * A subtitle file that sits beside the media rather than inside it.
     *
     * This is the shape a Plex subtitle file of its own arrives in: a separate thing at its own
     * address, which the player has to be told about, and which it is fetched from with the same
     * credentials as the media. The id is the stream's own, so that the track the player ends up
     * with can be matched back to the one that was asked for.
     */
    private fun sidecarSubtitle(): MediaItem.SubtitleConfiguration =
        MediaItem.SubtitleConfiguration.Builder(Uri.parse("asset:///subs.ass"))
            .setMimeType(MimeTypes.TEXT_SSA)
            .setId(SIDECAR_TRACK_ID)
            .setLanguage("eng")
            .setLabel("English")
            .build()

    /**
     * Switches on the text track with the given id.
     *
     * The track that came in beside the media has its id prefixed with the source it came
     * from, because the media and the subtitles are merged into one, so only the part after
     * the colon is the id that was given.
     */
    private fun selectTextTrack(player: Player, tracks: Tracks, wantedId: String) {
        // The tracks arrive in more than one group: a track that came in beside the media is in a
        // group of its own, so every group has to be looked at.
        val match = tracks.groups
            .filter { it.type == C.TRACK_TYPE_TEXT }
            .firstNotNullOfOrNull { group ->
                (0 until group.length)
                    .firstOrNull { i ->
                        group.getTrackFormat(i).id?.substringAfter(":") == wantedId
                    }
                    ?.let { i -> group to i }
            } ?: return
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .setOverrideForType(
                TrackSelectionOverride(match.first.mediaTrackGroup, match.second)
            )
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
