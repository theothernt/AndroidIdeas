package com.neilturner.playerexp.ui.viewmodels

import android.util.Log
import android.app.Application
import android.os.SystemClock
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.neilturner.playerexp.data.plex.PlexAccountStore
import com.neilturner.playerexp.data.plex.PlexApi
import com.neilturner.playerexp.data.plex.AndroidPlexCapabilityProbe
import com.neilturner.playerexp.data.plex.PlexPlaybackProfile
import com.neilturner.playerexp.data.plex.PlexPlaybackStatus
import com.neilturner.playerexp.data.plex.SessionCleanup
import com.neilturner.playerexp.data.plex.SessionCleanupManager
import okhttp3.OkHttpClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    private val sessionCleanup: SessionCleanup = SessionCleanupManager.getInstance()

    private val _uiState = MutableStateFlow<PlexPlayerUiState>(PlexPlayerUiState.Loading)
    val uiState: StateFlow<PlexPlayerUiState> = _uiState.asStateFlow()

    var player: ExoPlayer? by mutableStateOf(null)
        private set

    private var progressReportingJob: Job? = null
    private var timeline: PlexTimeline? = null
    private var stoppedSessionIdentifier: String? = null
    private var lastKnownPositionMillis: Long = 0L
    private var lastKnownDurationMillis: Long = 0L

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
                    PLAYBACK_LOG_TAG,
                    "Capabilities: video=${capabilities.videoCodecs.joinToString { codec ->
                        "${codec.plexName}[${codec.maximumWidth}x${codec.maximumHeight}@${codec.maximumFrameRate}]"
                    }}, audio=${capabilities.audioCodecs.joinToString { codec ->
                        "${codec.plexName}[channels=${codec.maximumChannelCount}]"
                    }}"
                )
                Log.d(PLAYBACK_LOG_TAG, "Client profile: ${profile.clientProfileExtra}")
                val sessionIdentifier = UUID.randomUUID().toString()
                val playbackPlan = api.playbackPlan(
                    serverUrl = serverUrl,
                    accountToken = token,
                    episode = selectedEpisode,
                    profile = profile,
                    sessionIdentifier = sessionIdentifier
                )
                Log.d(
                    PLAYBACK_LOG_TAG,
                    "Playback plan: mode=${playbackPlan::class.simpleName}, " +
                        "status=${playbackPlan.playbackStatus}, decision=${playbackPlan.decisionText}"
                )

                val context = getApplication<Application>().applicationContext
                // A new decision has a new Plex session and profile, so its HTTP headers must not be
                // inherited from a previous player instance.
                player?.release()
                val p = createPlayer(context, token, profile, sessionIdentifier).also { player = it }
                timeline = PlexTimeline(
                    serverUrl = serverUrl,
                    accountToken = token,
                    ratingKey = requireNotNull(selectedEpisode.ratingKey),
                    sessionIdentifier = sessionIdentifier
                )
                stoppedSessionIdentifier = null
                lastKnownPositionMillis = 0L
                lastKnownDurationMillis = 0L

                val title = listOfNotNull(selectedEpisode.showTitle, selectedEpisode.episodeTitle)
                    .joinToString(" - ")
                    .ifEmpty { "Plex Episode" }
                _uiState.value = PlexPlayerUiState.Ready(title)
                val prepareStartedAt = SystemClock.elapsedRealtime()
                p.addListener(playbackListener(playbackPlan.playbackStatus, prepareStartedAt))

                val mediaItem = MediaItem.fromUri(playbackPlan.url)
                p.setMediaItem(mediaItem)
                Log.d(PLAYBACK_LOG_TAG, "Preparing ${playbackPlan::class.simpleName} media source")
                p.prepare()
                p.playWhenReady = true
            } catch (e: Exception) {
                Log.e(
                    PLAYBACK_LOG_TAG,
                    "Playback setup failed: type=${e.javaClass.simpleName}, message=${e.message.safeForLog()}"
                )
                _uiState.value = PlexPlayerUiState.Error("Failed to start Plex playback. Check device logs.")
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
        val currentPlayer = player
        stopProgressReporting()
        reportProgress(state = TIMELINE_STATE_PAUSED)
        stopPlaybackSession(currentPlayer)
        currentPlayer?.stop()
        currentPlayer?.release()
        player = null
        timeline = null
    }

    override fun onCleared() {
        releasePlayer()
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

    private fun playbackListener(
        playbackStatus: PlexPlaybackStatus,
        prepareStartedAt: Long
    ): Player.Listener =
        object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                Log.d(PLAYBACK_LOG_TAG, "Player state=${playbackStateName(playbackState)}")
                updateLastKnownTime(player)
                if (playbackState == Player.STATE_READY) {
                    _uiState.value = (_uiState.value as? PlexPlayerUiState.Ready)
                        ?.copy(playbackStatus = playbackStatus)
                        ?: return
                } else if (playbackState == Player.STATE_ENDED) {
                    stopProgressReporting()
                    stopPlaybackSession(player)
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                Log.d(PLAYBACK_LOG_TAG, "Player isPlaying=$isPlaying")
                updateLastKnownTime(player)
                if (isPlaying) {
                    reportProgress(state = TIMELINE_STATE_PLAYING)
                    startProgressReporting()
                } else {
                    stopProgressReporting()
                    // Buffering also makes isPlaying false; only report a pause when the user
                    // (or player controls) has actually disabled playWhenReady.
                    if (player?.playWhenReady == false) {
                        reportProgress(state = TIMELINE_STATE_PAUSED)
                    }
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                Log.d(PLAYBACK_LOG_TAG, "Player position changed: reason=$reason")
                updateLastKnownTime(player)
                reportProgress(state = if (player?.playWhenReady == true) {
                    TIMELINE_STATE_PLAYING
                } else {
                    TIMELINE_STATE_PAUSED
                })
            }

            override fun onRenderedFirstFrame() {
                Log.d(
                    PLAYBACK_LOG_TAG,
                    "First video frame rendered after ${SystemClock.elapsedRealtime() - prepareStartedAt}ms"
                )
            }

            override fun onTracksChanged(tracks: Tracks) {
                val selectedFormats = tracks.groups.flatMap { group ->
                    (0 until group.length)
                        .filter(group::isTrackSelected)
                        .map(group::getTrackFormat)
                }
                Log.d(
                    PLAYBACK_LOG_TAG,
                    "Selected tracks: ${selectedFormats.joinToString { format ->
                        "mime=${format.sampleMimeType}, codecs=${format.codecs}, " +
                            "size=${format.width}x${format.height}, channels=${format.channelCount}, " +
                            "bitrate=${format.bitrate}"
                    }}"
                )
            }

            override fun onPlayerError(error: PlaybackException) {
                val httpError = error.findHttpError()
                Log.e(
                    PLAYBACK_LOG_TAG,
                    "Player error: code=${error.errorCodeName}, type=${error.javaClass.simpleName}, " +
                        "cause=${error.cause?.javaClass?.simpleName}, " +
                        "httpStatus=${httpError?.responseCode}, message=${error.message.safeForLog()}"
                )
            }
        }

    private fun startProgressReporting() {
        if (progressReportingJob?.isActive == true) return

        progressReportingJob = viewModelScope.launch {
            while (player?.isPlaying == true) {
                delay(PROGRESS_REPORT_INTERVAL_MILLIS)
                if (player?.isPlaying == true) {
                    reportProgress(state = TIMELINE_STATE_PLAYING)
                }
            }
        }
    }

    private fun stopProgressReporting() {
        progressReportingJob?.cancel()
        progressReportingJob = null
    }

    private fun updateLastKnownTime(player: Player?) {
        if (player == null) return
        val dur = player.duration
        if (dur != androidx.media3.common.C.TIME_UNSET && dur > 0L) {
            lastKnownDurationMillis = dur
        }
        val pos = player.currentPosition
        if (pos >= 0L) {
            lastKnownPositionMillis = pos
        }
    }

    private fun stopPlaybackSession(playerInstance: ExoPlayer? = player) {
        val currentTimeline = timeline ?: return
        if (stoppedSessionIdentifier == currentTimeline.sessionIdentifier) return
        stoppedSessionIdentifier = currentTimeline.sessionIdentifier

        updateLastKnownTime(playerInstance)

        val duration = when {
            playerInstance != null && playerInstance.duration != androidx.media3.common.C.TIME_UNSET && playerInstance.duration > 0L ->
                playerInstance.duration
            lastKnownDurationMillis > 0L ->
                lastKnownDurationMillis
            else ->
                -1L
        }

        val rawPosition = playerInstance?.currentPosition ?: lastKnownPositionMillis
        val finalPosition = when {
            playerInstance?.playbackState == Player.STATE_ENDED && duration > 0L ->
                duration
            duration > 0L ->
                rawPosition.coerceIn(0L, duration)
            else ->
                maxOf(0L, rawPosition)
        }

        sessionCleanup.enqueueFullCleanup(
            serverUrl = currentTimeline.serverUrl,
            accountToken = currentTimeline.accountToken,
            sessionIdentifier = currentTimeline.sessionIdentifier,
            ratingKey = currentTimeline.ratingKey,
            finalPositionMillis = finalPosition,
            durationMillis = duration
        )
    }

    private fun reportProgress(state: String) {
        val currentPlayer = player ?: return
        val currentTimeline = timeline ?: return
        updateLastKnownTime(currentPlayer)

        val durationMillis = when {
            currentPlayer.duration != androidx.media3.common.C.TIME_UNSET && currentPlayer.duration > 0L ->
                currentPlayer.duration
            lastKnownDurationMillis > 0L ->
                lastKnownDurationMillis
            else ->
                return
        }

        val positionMillis = currentPlayer.currentPosition.coerceIn(0L, durationMillis)
        viewModelScope.launch {
            try {
                api.reportTimeline(
                    serverUrl = currentTimeline.serverUrl,
                    accountToken = currentTimeline.accountToken,
                    ratingKey = currentTimeline.ratingKey,
                    state = state,
                    timeMillis = positionMillis,
                    durationMillis = durationMillis,
                    sessionIdentifier = currentTimeline.sessionIdentifier
                )
            } catch (e: Exception) {
                Log.w(
                    PLAYBACK_LOG_TAG,
                    "Timeline update failed: type=${e.javaClass.simpleName}, message=${e.message.safeForLog()}"
                )
            }
        }
    }

    private fun PlaybackException.findHttpError(): HttpDataSource.InvalidResponseCodeException? {
        var cause: Throwable? = this
        while (cause != null) {
            if (cause is HttpDataSource.InvalidResponseCodeException) return cause
            cause = cause.cause
        }
        return null
    }

    private fun String?.safeForLog(): String = this
        ?.replace(Regex("\\?[^\\s]+"), "?<query-redacted>")
        ?.take(2000)
        ?: "<none>"

    private fun playbackStateName(state: Int): String = when (state) {
        Player.STATE_IDLE -> "IDLE"
        Player.STATE_BUFFERING -> "BUFFERING"
        Player.STATE_READY -> "READY"
        Player.STATE_ENDED -> "ENDED"
        else -> "UNKNOWN($state)"
    }

    private companion object {
        const val PLAYBACK_LOG_TAG = "PlexPlayback"
        const val PROGRESS_REPORT_INTERVAL_MILLIS = 5_000L
        const val TIMELINE_STATE_PLAYING = "playing"
        const val TIMELINE_STATE_PAUSED = "paused"
    }
}

private data class PlexTimeline(
    val serverUrl: String,
    val accountToken: String,
    val ratingKey: String,
    val sessionIdentifier: String
)
