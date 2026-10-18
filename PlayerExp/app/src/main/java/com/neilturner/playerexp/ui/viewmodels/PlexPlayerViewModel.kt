package com.neilturner.playerexp.ui.viewmodels

import android.util.Log
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.SystemClock
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.AudioTrackAudioOutputProvider
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.neilturner.playerexp.data.plex.PlexAccountStore
import com.neilturner.playerexp.data.plex.PlexEpisode
import com.neilturner.playerexp.data.plex.PlexLibrarySections
import com.neilturner.playerexp.data.plex.PlexApi
import com.neilturner.playerexp.data.plex.AndroidPlexCapabilityProbe
import com.neilturner.playerexp.data.plex.PlexPlaybackPlan
import com.neilturner.playerexp.data.plex.PlexPlaybackProfile
import com.neilturner.playerexp.data.plex.PlexPlaybackStatus
import com.neilturner.playerexp.data.plex.PlexStreamMode
import com.neilturner.playerexp.data.plex.PlexStreamPlayback
import com.neilturner.playerexp.data.plex.PlexAudioStream
import com.neilturner.playerexp.data.plex.PlexSubtitleStream
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

/**
 * What is playing, in the pieces the overlay lays out: the show, the episode, and where the
 * episode sits in its season.
 *
 * Each piece stands on its own rather than arriving pre-joined because the overlay shows them on
 * separate lines, and because [PlexEpisode] fields go missing for real items — a special has no
 * season number, and Plex guarantees none of the titles.
 */
@Immutable
data class PlexPlaybackMetadata(
    val showTitle: String?,
    val episodeTitle: String?,
    val seasonNumber: Int?,
    val episodeNumber: Int?
)

/**
 * What Plex decided to do with each stream, shown as a notice before playback starts.
 *
 * The decision is in hand before the player is prepared, so a transcode can be announced while
 * the spinner is still up, rather than only after the wait it explains. Each stream stands alone
 * because Plex commonly direct-plays video while transcoding audio — an EAC3 track on a device
 * whose profile excludes it, for instance.
 */
@Immutable
data class TranscodeNotice(
    val video: PlexStreamPlayback,
    val audio: PlexStreamPlayback
) {
    val videoTranscoded: Boolean get() = video.mode == PlexStreamMode.Transcoded
    val audioTranscoded: Boolean get() = audio.mode == PlexStreamMode.Transcoded

    companion object {
        /**
         * The notice Plex's decision calls for, or null when both streams direct-play and there
         * is nothing to explain.
         */
        internal fun fromPlaybackStatus(status: PlexPlaybackStatus): TranscodeNotice? =
            TranscodeNotice(status.video, status.audio).takeIf { notice ->
                notice.videoTranscoded || notice.audioTranscoded
            }
    }
}

@Immutable
sealed interface PlexPlayerUiState {
    data object NotAuthorised : PlexPlayerUiState
    data object Loading : PlexPlayerUiState
    data class Ready(
        val metadata: PlexPlaybackMetadata,
        val transcodeNotice: TranscodeNotice? = null,
        val playbackStatus: PlexPlaybackStatus? = null,
        val availableSubtitles: List<PlexSubtitleStream> = emptyList(),
        val selectedSubtitleId: Long? = null,
        val availableAudio: List<PlexAudioStream> = emptyList(),
        val selectedAudioId: Long? = null
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
    private var currentServerUrl: String? = null
    private var currentAccountToken: String? = null
    private var currentEpisode: PlexEpisode? = null
    private var currentProfile: PlexPlaybackProfile? = null
    private var currentPlaybackPlan: PlexPlaybackPlan? = null

    /**
     * Plays one specific item, which is what a card on a shelf asks for. [title] is what the card
     * already had to show, so the screen can name what is playing before its own metadata lands.
     */
    fun playItem(ratingKey: String, title: String?) {
        viewModelScope.launch {
            val token = store.accountToken()
            if (token == null) {
                _uiState.value = PlexPlayerUiState.NotAuthorised
                return@launch
            }
            _uiState.value = PlexPlayerUiState.Loading
            try {
                // resolveServerUrl reports the missing-server case itself.
                val serverUrl = resolveServerUrl(token) ?: return@launch
                val item = api.mediaForRatingKey(serverUrl, token, ratingKey)
                if (item == null) {
                    _uiState.value = PlexPlayerUiState.Error("Plex has nothing playable for this item.")
                    return@launch
                }
                startPlayback(serverUrl, token, item, title)
            } catch (e: Exception) {
                failToStart(e)
            }
        }
    }

    /** Plays a random episode from a TV library, which is what the home screen's player button does. */
    fun loadAndPlay() {
        val token = store.accountToken()
        if (token == null) {
            _uiState.value = PlexPlayerUiState.NotAuthorised
            return
        }

        _uiState.value = PlexPlayerUiState.Loading

        viewModelScope.launch {
            try {
                val serverUrl = resolveServerUrl(token) ?: return@launch
                val showSections = api.tvShowSections(serverUrl, token)
                // By index this picked whichever show library happened to be first, which on a server
                // with alternates is often the alternate one.
                val sectionKey = PlexLibrarySections.pickTvShows(showSections)?.key
                if (sectionKey == null) {
                    _uiState.value = PlexPlayerUiState.Error("No TV Show libraries found on your Plex server.")
                    return@launch
                }

                val episodes = api.recentEpisodes(serverUrl, token, sectionKey, limit = 10)
                if (episodes.isEmpty()) {
                    _uiState.value = PlexPlayerUiState.Error("No TV show episodes found in the library.")
                    return@launch
                }

                startPlayback(serverUrl, token, episodes.random(), title = null)
            } catch (e: Exception) {
                failToStart(e)
            }
        }
    }

    /**
     * Everything playback needs once the item is known: what this device can play, what Plex decides
     * to do about it, and a player wired up with the session headers that decision expects.
     */
    private suspend fun startPlayback(
        serverUrl: String,
        token: String,
        selectedEpisode: PlexEpisode,
        title: String?
    ) {
        try {
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
            Log.d(PLAYBACK_LOG_TAG, "EAC3 direct play profile: ${profile.supportsEac3Directly}")

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
            // The resume point seeds the timeline so the first progress report tells Plex the
            // position playback actually opened at, not the start of the file.
            lastKnownPositionMillis = selectedEpisode.viewOffset
            lastKnownDurationMillis = 0L

            currentServerUrl = serverUrl
            currentAccountToken = token
            currentEpisode = selectedEpisode
            currentProfile = profile
            currentPlaybackPlan = playbackPlan

            // The card's title stands in for an episode Plex gave no title of its own; everything
            // else comes from the item, which is fetched before playback starts, so the overlay
            // always has the full set of pieces to lay out. The transcode notice comes from the
            // decision, which is already in hand, and stays until the first frame replaces it.
            _uiState.value = PlexPlayerUiState.Ready(
                metadata = PlexPlaybackMetadata(
                    showTitle = selectedEpisode.showTitle,
                    episodeTitle = selectedEpisode.episodeTitle ?: title,
                    seasonNumber = selectedEpisode.seasonNumber,
                    episodeNumber = selectedEpisode.episodeNumber
                ),
                transcodeNotice = TranscodeNotice.fromPlaybackStatus(playbackPlan.playbackStatus),
                availableSubtitles = selectedEpisode.subtitleStreams,
                selectedSubtitleId = null,
                availableAudio = selectedEpisode.audioStreams,
                selectedAudioId = selectedEpisode.audioStreams.firstOrNull { it.isDefault }?.id
            )
            val prepareStartedAt = SystemClock.elapsedRealtime()
            p.addListener(playbackListener(playbackPlan.playbackStatus, prepareStartedAt))

            // Subtitle tracks start disabled unless selected
            p.trackSelectionParameters = p.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()

            p.setMediaItem(MediaItem.fromUri(playbackPlan.url))
            // A seek set before prepare is where playback opens: ExoPlayer holds it as the start
            // position, so playWhenReady begins mid-episode rather than at the first frame.
            val resumeAtMillis = selectedEpisode.viewOffset
            if (resumeAtMillis > 0L) {
                p.seekTo(resumeAtMillis)
                Log.d(PLAYBACK_LOG_TAG, "Resuming at ${resumeAtMillis}ms")
            }
            Log.d(PLAYBACK_LOG_TAG, "Preparing ${playbackPlan::class.simpleName} media source")
            p.prepare()
            p.playWhenReady = true
        } catch (e: Exception) {
            failToStart(e)
        }
    }

    /** Resolves and caches the server URL, reporting the missing-server case itself. */
    private suspend fun resolveServerUrl(token: String): String? {
        store.serverUrl()?.let { return it }
        val info = api.serverInfo(token)
        val serverUrl = info?.uri
        if (serverUrl != null) {
            store.saveLinkedAccount(token, info.name, serverUrl)
        } else {
            Log.w(PLAYBACK_LOG_TAG, "No Plex server URL found")
            _uiState.value = PlexPlayerUiState.Error("No Plex server URL found. Please check Plex settings.")
        }
        return serverUrl
    }

    private fun failToStart(e: Exception) {
        Log.e(
            PLAYBACK_LOG_TAG,
            "Playback setup failed: type=${e.javaClass.simpleName}, message=${e.message.safeForLog()}"
        )
        _uiState.value = PlexPlayerUiState.Error("Failed to start Plex playback. Check device logs.")
    }

    fun pause() {
        player?.playWhenReady = false
    }

    fun resume() {
        player?.playWhenReady = true
    }

    /**
     * Selects a subtitle track by its Plex stream id, or null to disable subtitles.
     *
     * In Direct Play, ExoPlayer applies the change immediately via track selection parameters.
     * In HLS transcode, the universal transcode session is re-established with the new subtitle
     * stream requested, resuming playback from the current position.
     */
    fun selectSubtitle(subtitleStreamId: Long?) {
        val readyState = _uiState.value as? PlexPlayerUiState.Ready ?: return
        if (readyState.selectedSubtitleId == subtitleStreamId) return

        val p = player ?: return
        val selectedStream = readyState.availableSubtitles.firstOrNull { it.id == subtitleStreamId }

        _uiState.value = readyState.copy(selectedSubtitleId = subtitleStreamId)

        val plan = currentPlaybackPlan
        if (plan is PlexPlaybackPlan.DirectPlay) {
            updateExoPlayerSubtitleTrack(p, selectedStream)
            return
        }

        val episode = currentEpisode ?: return
        val serverUrl = currentServerUrl ?: return
        val token = currentAccountToken ?: return
        val profile = currentProfile ?: return

        viewModelScope.launch {
            try {
                updateLastKnownTime(p)
                val currentPos = p.currentPosition.coerceAtLeast(0L)
                val oldTimeline = timeline
                val newSessionIdentifier = UUID.randomUUID().toString()

                if (oldTimeline != null) {
                    sessionCleanup.enqueueTranscodeStop(
                        serverUrl = oldTimeline.serverUrl,
                        accountToken = oldTimeline.accountToken,
                        sessionIdentifier = oldTimeline.sessionIdentifier
                    )
                }

                val newPlan = api.playbackPlan(
                    serverUrl = serverUrl,
                    accountToken = token,
                    episode = episode,
                    profile = profile,
                    sessionIdentifier = newSessionIdentifier,
                    audioStreamId = readyState.selectedAudioId,
                    subtitleStreamId = subtitleStreamId
                )
                currentPlaybackPlan = newPlan
                timeline = PlexTimeline(
                    serverUrl = serverUrl,
                    accountToken = token,
                    ratingKey = requireNotNull(episode.ratingKey),
                    sessionIdentifier = newSessionIdentifier
                )

                p.setMediaItem(MediaItem.fromUri(newPlan.url))
                if (currentPos > 0L) {
                    p.seekTo(currentPos)
                }
                p.prepare()
                p.playWhenReady = true
                updateExoPlayerSubtitleTrack(p, selectedStream)
            } catch (e: Exception) {
                Log.e(
                    PLAYBACK_LOG_TAG,
                    "Failed to switch subtitle in HLS: type=${e.javaClass.simpleName}, message=${e.message.safeForLog()}"
                )
            }
        }
    }

    /**
     * Selects an audio track by its Plex stream id.
     *
     * In Direct Play, ExoPlayer applies the change immediately via preferred audio language.
     * In HLS transcode, the session is re-established with the new audioStreamID, resuming from
     * the current position.
     */
    fun selectAudio(audioStreamId: Long?) {
        val readyState = _uiState.value as? PlexPlayerUiState.Ready ?: return
        if (readyState.selectedAudioId == audioStreamId) return

        val p = player ?: return
        val selectedStream = readyState.availableAudio.firstOrNull { it.id == audioStreamId }

        _uiState.value = readyState.copy(selectedAudioId = audioStreamId)

        val plan = currentPlaybackPlan
        if (plan is PlexPlaybackPlan.DirectPlay) {
            val builder = p.trackSelectionParameters.buildUpon()
            selectedStream?.languageCode?.let { builder.setPreferredAudioLanguage(it) }
                ?: builder.setPreferredAudioLanguage(null)
            p.trackSelectionParameters = builder.build()
            return
        }

        val episode = currentEpisode ?: return
        val serverUrl = currentServerUrl ?: return
        val token = currentAccountToken ?: return
        val profile = currentProfile ?: return

        viewModelScope.launch {
            try {
                updateLastKnownTime(p)
                val currentPos = p.currentPosition.coerceAtLeast(0L)
                val oldTimeline = timeline
                val newSessionIdentifier = UUID.randomUUID().toString()

                if (oldTimeline != null) {
                    sessionCleanup.enqueueTranscodeStop(
                        serverUrl = oldTimeline.serverUrl,
                        accountToken = oldTimeline.accountToken,
                        sessionIdentifier = oldTimeline.sessionIdentifier
                    )
                }

                val newPlan = api.playbackPlan(
                    serverUrl = serverUrl,
                    accountToken = token,
                    episode = episode,
                    profile = profile,
                    sessionIdentifier = newSessionIdentifier,
                    audioStreamId = audioStreamId,
                    subtitleStreamId = readyState.selectedSubtitleId
                )
                currentPlaybackPlan = newPlan
                timeline = PlexTimeline(
                    serverUrl = serverUrl,
                    accountToken = token,
                    ratingKey = requireNotNull(episode.ratingKey),
                    sessionIdentifier = newSessionIdentifier
                )

                p.setMediaItem(MediaItem.fromUri(newPlan.url))
                if (currentPos > 0L) {
                    p.seekTo(currentPos)
                }
                p.prepare()
                p.playWhenReady = true
            } catch (e: Exception) {
                Log.e(
                    PLAYBACK_LOG_TAG,
                    "Failed to switch audio in HLS: type=${e.javaClass.simpleName}, message=${e.message.safeForLog()}"
                )
            }
        }
    }

    private fun updateExoPlayerSubtitleTrack(
        player: Player,
        chosenStream: PlexSubtitleStream?
    ) {
        val builder = player.trackSelectionParameters.buildUpon()
        if (chosenStream == null) {
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            builder.clearOverridesOfType(C.TRACK_TYPE_TEXT)
        } else {
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            builder.clearOverridesOfType(C.TRACK_TYPE_TEXT)
            chosenStream.languageCode?.let { builder.setPreferredTextLanguage(it) }

            val tracks = player.currentTracks
            for (group in tracks.groups) {
                if (group.type == C.TRACK_TYPE_TEXT) {
                    for (i in 0 until group.length) {
                        val format = group.getTrackFormat(i)
                        val matchesId = format.id == chosenStream.id.toString()
                        val matchesLang = chosenStream.languageCode != null &&
                            format.language.equals(chosenStream.languageCode, ignoreCase = true)
                        if (matchesId || matchesLang) {
                            builder.setOverrideForType(
                                TrackSelectionOverride(group.mediaTrackGroup, i)
                            )
                            break
                        }
                    }
                }
            }
        }
        player.trackSelectionParameters = builder.build()
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
        currentServerUrl = null
        currentAccountToken = null
        currentEpisode = null
        currentProfile = null
        currentPlaybackPlan = null
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
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
        val forcePcmAudio = shouldForcePcmAudio()
        Log.d(
            PLAYBACK_LOG_TAG,
            "Audio sink: forcePcmAudio=$forcePcmAudio, device=${Build.DEVICE}, " +
                "product=${Build.PRODUCT}, model=${Build.MODEL}"
        )
        if (!forcePcmAudio) {
            return ExoPlayer.Builder(context)
                .setMediaSourceFactory(mediaSourceFactory)
                .build()
        }

        val renderersFactory = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioOutputPlaybackParams: Boolean
            ): AudioSink = DefaultAudioSink.Builder(context)
                .setAudioOutputProvider(
                    // No context => static DEFAULT_AUDIO_CAPABILITIES (no passthrough), same as the old no-arg builder path.
                    AudioTrackAudioOutputProvider.Builder(null).build()
                )
                .setEnableFloatOutput(enableFloatOutput)
                .setEnableAudioOutputPlaybackParameters(enableAudioOutputPlaybackParams)
                .build()
        }.setEnableDecoderFallback(true)
        return ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
    }

    private fun shouldForcePcmAudio(): Boolean {
        val identifiers = listOf(Build.DEVICE, Build.PRODUCT, Build.MODEL)
            .joinToString(" ")
            .lowercase()
        return identifiers.contains("sabrina") || identifiers.contains("boreal")
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
                val selectedSubtitleId = (_uiState.value as? PlexPlayerUiState.Ready)?.selectedSubtitleId
                val selectedStream = (_uiState.value as? PlexPlayerUiState.Ready)?.availableSubtitles
                    ?.firstOrNull { it.id == selectedSubtitleId }
                player?.let { p ->
                    updateExoPlayerSubtitleTrack(p, selectedStream)
                }
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
