package com.neilturner.playerexp.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.media3.common.util.UnstableApi
import com.neilturner.playerexp.R
import com.neilturner.playerexp.data.plex.PlexAudioStream
import com.neilturner.playerexp.data.plex.PlexPlaybackStatus
import com.neilturner.playerexp.data.plex.PlexStreamMode
import com.neilturner.playerexp.data.plex.PlexStreamPlayback
import com.neilturner.playerexp.data.plex.PlexSubtitleStream
import com.neilturner.playerexp.ui.viewmodels.PlexPlaybackMetadata
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * The player screen with a still picture behind it instead of a video.
 *
 * Everything else is the real thing: the bar along the bottom, the rail of buttons on it, the
 * drawer that comes in from the right, and the rules about what the D-pad and Back do. It is
 * here so that the chrome can be looked at and changed without a round trip to Plex, a transcode
 * to wait for, and a copy of a real episode to scrub through.
 */
@UnstableApi
@Composable
fun PlexPlayerMockScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val controls = rememberPlaybackControlsState()
    val playerFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { playerFocusRequester.requestFocus() }

    // The position walks forward on its own, so the bar under the picture moves the way it does
    // during real playback and the time remaining counts down.
    var positionMillis by remember { mutableLongStateOf(MOCK_START_POSITION_MILLIS) }
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(1_000L)
            positionMillis = (positionMillis + 1_000L) % MOCK_DURATION_MILLIS
        }
    }

    var selectedSubtitleId by remember { mutableStateOf<Long?>(null) }
    var selectedAudioId by remember { mutableStateOf<Long?>(MOCK_AUDIO.first().id) }
    var playbackSpeed by remember { mutableFloatStateOf(1f) }

    BackHandler { controls.handleBack { onBack() } }

    PlaybackContent(
        currentPosition = { positionMillis },
        duration = { MOCK_DURATION_MILLIS },
        videoSurface = {
            Image(
                painter = painterResource(R.drawable.mock_video),
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
        },
        playbackStatus = MOCK_PLAYBACK_STATUS,
        transcodeNotice = null,
        metadata = MOCK_METADATA,
        availableSubtitles = MOCK_SUBTITLES,
        selectedSubtitleId = selectedSubtitleId,
        onSelectSubtitle = { selectedSubtitleId = it },
        availableAudio = MOCK_AUDIO,
        selectedAudioId = selectedAudioId,
        onSelectAudio = { selectedAudioId = it },
        playbackSpeed = playbackSpeed,
        onSelectSpeed = { playbackSpeed = it },
        videoWidth = 1920,
        videoHeight = 1080,
        playerFocusRequester = playerFocusRequester,
        controls = controls,
        onBackPress = { controls.handleBack { onBack() } },
        modifier = modifier
            .fillMaxSize()
            .focusRequester(playerFocusRequester)
    )
}

private const val MOCK_DURATION_MILLIS = 45L * 60L * 1_000L
private const val MOCK_START_POSITION_MILLIS = 28L * 1_000L

private val MOCK_METADATA = PlexPlaybackMetadata(
    showTitle = "Last Week Tonight with John Oliver",
    episodeTitle = "October 11, 2026: Generic Drugs",
    seasonNumber = 13,
    episodeNumber = 26
)

private val MOCK_PLAYBACK_STATUS = PlexPlaybackStatus(
    video = PlexStreamPlayback(PlexStreamMode.Direct, "h264"),
    audio = PlexStreamPlayback(PlexStreamMode.Transcoded, "eac3")
)

private val MOCK_AUDIO = listOf(
    PlexAudioStream(id = 1, language = "English", codec = "eac3", channels = 6, isDefault = true),
    PlexAudioStream(id = 2, language = "English", codec = "aac", channels = 2),
    PlexAudioStream(id = 3, language = "Commentary", codec = "aac", channels = 2),
    PlexAudioStream(id = 4, language = "French", codec = "ac3", channels = 6)
)

private val MOCK_SUBTITLES = listOf(
    PlexSubtitleStream(id = 7, language = "English", codec = "srt"),
    PlexSubtitleStream(id = 8, language = "English", codec = "srt", forced = true),
    PlexSubtitleStream(id = 9, language = "Spanish", codec = "srt"),
    PlexSubtitleStream(id = 10, language = "French", codec = "srt")
)
