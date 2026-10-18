package com.neilturner.playerexp.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import android.os.SystemClock
import androidx.media3.ui.compose.material3.Player
import androidx.media3.common.util.UnstableApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.SubtitleView
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.IconButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.neilturner.playerexp.R
import com.neilturner.playerexp.data.plex.PlexAudioStream
import com.neilturner.playerexp.data.plex.PlexPlaybackStatus
import com.neilturner.playerexp.data.plex.PlexStreamMode
import com.neilturner.playerexp.data.plex.PlexSubtitleStream
import com.neilturner.playerexp.ui.modifiers.catchBackPress
import com.neilturner.playerexp.ui.modifiers.playerOverlayControls
import com.neilturner.playerexp.ui.theme.PlexAmber
import com.neilturner.playerexp.ui.viewmodels.PlexPlaybackMetadata
import com.neilturner.playerexp.ui.viewmodels.PlexPlayerUiState
import com.neilturner.playerexp.ui.viewmodels.PlexPlayerViewModel
import com.neilturner.playerexp.ui.viewmodels.TranscodeNotice
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.Locale

@UnstableApi
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PlexPlayerScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    ratingKey: String? = null,
    title: String? = null,
    viewModel: PlexPlayerViewModel = viewModel()
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val controls = rememberPlaybackControlsState()

    // Back unwinds one layer at a time: an open track menu closes on its own, then a visible
    // overlay is dismissed rather than leaving the episode, and so is a back press that arrives
    // during the grace window after the overlay hid itself. The same rules are handed to the
    // controls themselves, which catch a Back the focusable player surface would swallow.
    val leavePlayback = {
        viewModel.releasePlayer()
        onBack()
    }
    BackHandler { controls.handleBack(leavePlayback) }

    // A card on a shelf says what to play; with no card behind it, this is the home screen's
    // button and plays something from a TV library instead. Keyed on the item so a change of target
    // restarts rather than keeping the previous one.
    LaunchedEffect(ratingKey) {
        if (ratingKey != null) {
            viewModel.playItem(ratingKey, title)
        } else {
            viewModel.loadAndPlay()
        }
    }

    DisposableEffect(lifecycleOwner.lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                viewModel.pause()
            } else if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.resume()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.releasePlayer()
        }
    }

    when (val state = uiState) {
        is PlexPlayerUiState.NotAuthorised -> {
            val backFocusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) {
                backFocusRequester.requestFocus()
            }
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stringResource(R.string.plex_not_authorised),
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(32.dp))
                Button(
                    onClick = onBack,
                    modifier = Modifier
                        .focusRequester(backFocusRequester)
                        .width(360.dp)
                        .height(56.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.back),
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        is PlexPlayerUiState.Loading -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = stringResource(R.string.plex_loading_episode),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        is PlexPlayerUiState.Ready -> {
            val player = viewModel.player
            val focusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) { focusRequester.requestFocus() }
            if (player != null) {
                PlaybackContent(
                    player = player,
                    playbackStatus = state.playbackStatus,
                    transcodeNotice = state.transcodeNotice,
                    metadata = state.metadata,
                    availableSubtitles = state.availableSubtitles,
                    selectedSubtitleId = state.selectedSubtitleId,
                    onSelectSubtitle = viewModel::selectSubtitle,
                    availableAudio = state.availableAudio,
                    selectedAudioId = state.selectedAudioId,
                    onSelectAudio = viewModel::selectAudio,
                    playerFocusRequester = focusRequester,
                    controls = controls,
                    onBackPress = { controls.handleBack(leavePlayback) },
                    modifier = modifier
                        .fillMaxSize()
                        .focusRequester(focusRequester)
                )
            } else {
                Box(
                    modifier = modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        is PlexPlayerUiState.Error -> {
            val backFocusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) {
                backFocusRequester.requestFocus()
            }
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(32.dp))
                Button(
                    onClick = onBack,
                    modifier = Modifier
                        .focusRequester(backFocusRequester)
                        .width(360.dp)
                        .height(56.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.back),
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
@UnstableApi
private fun PlaybackContent(
    player: androidx.media3.common.Player,
    playbackStatus: PlexPlaybackStatus?,
    transcodeNotice: TranscodeNotice?,
    metadata: PlexPlaybackMetadata,
    availableSubtitles: List<PlexSubtitleStream>,
    selectedSubtitleId: Long?,
    onSelectSubtitle: (Long?) -> Unit,
    availableAudio: List<PlexAudioStream>,
    selectedAudioId: Long?,
    onSelectAudio: (Long?) -> Unit,
    playerFocusRequester: FocusRequester,
    controls: PlaybackControlsState,
    onBackPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    // The video plays unobstructed. Any D-pad press reveals how far into the episode you are, and
    // the overlay hides itself a few seconds after the last one, so glancing costs a single key.
    // The timer is keyed on a press count rather than the visibility flag so a second press while
    // the overlay is already up resets it instead of being ignored.
    LaunchedEffect(controls.revealCount) {
        if (controls.isVisible) {
            delay(CONTROLS_AUTO_HIDE_MILLIS)
            controls.autoHide()
        }
    }

    val audioButtonFocus = remember { FocusRequester() }
    val subtitleButtonFocus = remember { FocusRequester() }
    // A button is only drawn for a kind of track the item actually carries, so an episode with no
    // subtitles is never sent to a button that is not there.
    val hasAudio = availableAudio.isNotEmpty()
    val hasSubtitles = availableSubtitles.isNotEmpty()
    val hasActionButtons = hasAudio || hasSubtitles
    // Where focus lands each time the overlay comes up: the button it was last on, so stepping away
    // and back does not make the user re-navigate from the start each time.
    var lastActionButton by remember { mutableStateOf(TrackPicker.Audio) }

    // Focus rides the overlay. Up goes to the buttons, and down — Back, or the auto-hide timer —
    // goes back to the player surface, which is the node whose D-pad presses reveal the overlay in
    // the first place. An open picker keeps the focus it took for itself, so it is left alone, and
    // an item with no tracks to pick has no buttons to land on and stays on the surface.
    LaunchedEffect(controls.isVisible, controls.activePicker, hasAudio, hasSubtitles) {
        when {
            controls.activePicker != TrackPicker.None -> Unit
            !controls.isVisible || !hasActionButtons -> playerFocusRequester.requestFocus()
            lastActionButton == TrackPicker.Subtitles && hasSubtitles -> subtitleButtonFocus.requestFocus()
            hasAudio -> audioButtonFocus.requestFocus()
            else -> subtitleButtonFocus.requestFocus()
        }
    }

    Box(
        modifier = modifier.playerOverlayControls(
            controlsVisible = controls.isVisible,
            onRevealControls = controls::reveal
        )
    ) {
        Player(
            player = player,
            modifier = Modifier.fillMaxSize(),
            showControls = false,
            shutter = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    // While the transcode notice is up it carries the spinner of its own,
                    // so the shutter stays a plain black box and nothing spins twice.
                    if (transcodeNotice == null || playbackStatus != null) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        )

        var cues by remember { mutableStateOf(player.currentCues.cues) }
        DisposableEffect(player) {
            val listener = object : androidx.media3.common.Player.Listener {
                override fun onCues(cueGroup: androidx.media3.common.text.CueGroup) {
                    cues = cueGroup.cues
                }
            }
            player.addListener(listener)
            onDispose {
                player.removeListener(listener)
            }
        }

        if (cues.isNotEmpty()) {
            AndroidView(
                factory = { context ->
                    SubtitleView(context).apply {
                        setUserDefaultStyle()
                        setUserDefaultTextSize()
                    }
                },
                update = { subtitleView ->
                    subtitleView.setCues(cues)
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // A transcode explains itself while the user waits for it: centred on the same black
        // surface as the spinner, in place until the video starts. The first frame brings
        // playbackStatus, which takes the notice down.
        AnimatedVisibility(
            visible = transcodeNotice != null && playbackStatus == null,
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn(animationSpec = tween(CONTROLS_FADE_MILLIS)),
            exit = fadeOut(animationSpec = tween(CONTROLS_FADE_MILLIS))
        ) {
            val notice = transcodeNotice
            if (notice != null) {
                TranscodeNoticeContent(notice = notice)
            }
        }
        AnimatedVisibility(
            visible = controls.isVisible,
            modifier = Modifier.align(Alignment.BottomCenter),
            // The controls rise from the bottom edge, and sink back to it, over the fade's own
            // fixed duration — one duration for the whole motion, so the back-press grace that
            // follows it can still be timed from the end of the animation rather than guessed at.
            enter = fadeIn(animationSpec = tween(CONTROLS_FADE_MILLIS)) +
                slideInVertically(animationSpec = tween(CONTROLS_FADE_MILLIS)) { height -> height },
            exit = fadeOut(animationSpec = tween(CONTROLS_FADE_MILLIS)) +
                slideOutVertically(animationSpec = tween(CONTROLS_FADE_MILLIS)) { height -> height }
        ) {
            PlaybackProgressOverlay(
                player = player,
                metadata = metadata,
                audioActive = selectedAudioId != null,
                subtitlesActive = selectedSubtitleId != null,
                showAudio = hasAudio,
                showSubtitles = hasSubtitles,
                activePicker = controls.activePicker,
                onTogglePicker = controls::togglePicker,
                audioButtonFocus = audioButtonFocus,
                subtitleButtonFocus = subtitleButtonFocus,
                onActionButtonFocused = { button -> lastActionButton = button },
                onUserActivity = controls::reveal,
                onDismiss = controls::dismiss,
                onBackPress = onBackPress
            )
        }

        // The menu is a sibling of the overlay rather than part of it, because the overlay is a
        // band of fixed height and a drop-down needs to be free to grow past the top of it and
        // hang over the video. Anchoring is done with padding measured back from the bottom
        // corner, which lands it on the button that opened it: the subtitle button is the
        // rightmost of the pair, so the audio button's menu is inset by the button to its right.
        AnimatedVisibility(
            visible = controls.activePicker != TrackPicker.None,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    end = OVERLAY_HORIZONTAL_PADDING +
                        if (controls.activePicker == TrackPicker.Audio) {
                            ACTION_BUTTON_TRAILING_INSET
                        } else {
                            0.dp
                        },
                    bottom = OVERLAY_MENU_BOTTOM_INSET
                ),
            // It unrolls off the button and rolls back up into it, so the motion points at the
            // control the menu belongs to.
            enter = fadeIn(animationSpec = tween(CONTROLS_FADE_MILLIS)) +
                expandVertically(
                    animationSpec = tween(CONTROLS_FADE_MILLIS),
                    expandFrom = Alignment.Bottom
                ),
            exit = fadeOut(animationSpec = tween(CONTROLS_FADE_MILLIS)) +
                shrinkVertically(
                    animationSpec = tween(CONTROLS_FADE_MILLIS),
                    shrinkTowards = Alignment.Bottom
                )
        ) {
            when (controls.activePicker) {
                TrackPicker.Audio -> VerticalTrackMenu(
                    label = stringResource(R.string.plex_audio_picker),
                    choices = audioChoices(availableAudio, selectedAudioId),
                    onSelect = { choice ->
                        onSelectAudio(choice.streamId)
                        controls.closePicker()
                        controls.reveal()
                    },
                    onClose = controls::closePicker,
                    onUserActivity = controls::reveal,
                    onBack = onBackPress
                )

                TrackPicker.Subtitles -> VerticalTrackMenu(
                    label = stringResource(R.string.plex_subtitles_picker),
                    choices = subtitleChoices(
                        subtitles = availableSubtitles,
                        selectedSubtitleId = selectedSubtitleId,
                        offLabel = stringResource(R.string.plex_subtitles_off)
                    ),
                    onSelect = { choice ->
                        onSelectSubtitle(choice.streamId)
                        controls.closePicker()
                        controls.reveal()
                    },
                    onClose = controls::closePicker,
                    onUserActivity = controls::reveal,
                    onBack = onBackPress
                )

                TrackPicker.None -> Unit
            }
        }
    }
}

/**
 * Which of the bottom-right buttons has its picker open, if either.
 *
 * A picker only exists while the overlay does, so [None] covers both the hidden overlay and a
 * visible one with no picker open; the two are told apart by the overlay's own visibility.
 */
private enum class TrackPicker { None, Audio, Subtitles }

/**
 * Whether the progress overlay is showing, which picker it has open, and what Back should do
 * about the lot.
 *
 * Back unwinds a layer at a time: an open picker closes on its own, then a visible overlay is
 * dismissed rather than leaving playback. Once the overlay hides itself on its timeout, the
 * overlay still fades out, and a back press during that fade — and for a short grace window after
 * it finishes — is still read as "dismiss the overlay", because the user saw it on screen moments
 * earlier and meant to dismiss it, not to stop playback. Dismissing the overlay with Back leaves
 * no grace, so a second Back press leaves playback straight away.
 */
private class PlaybackControlsState {
    var isVisible by mutableStateOf(false)
        private set

    var activePicker by mutableStateOf(TrackPicker.None)
        private set

    /** Increments on every reveal, so the auto-hide timer restarts on a repeat press. */
    var revealCount by mutableIntStateOf(0)
        private set

    private var autoHiddenAtMillis = 0L

    fun reveal() {
        isVisible = true
        autoHiddenAtMillis = 0L
        revealCount++
    }

    /** Center on a button opens the picker it owns; center again on that button closes it. */
    fun togglePicker(picker: TrackPicker) {
        if (picker == TrackPicker.None) return
        activePicker = if (activePicker == picker) TrackPicker.None else picker
        reveal()
    }

    /** Picking an option closes the picker it came from, dropping focus back on the buttons. */
    fun closePicker() {
        activePicker = TrackPicker.None
    }

    /**
     * Consumes a Back press that should close an open picker rather than touch the overlay or
     * playback, and reports whether it did.
     */
    fun consumeBackForPicker(): Boolean {
        if (activePicker == TrackPicker.None) return false
        activePicker = TrackPicker.None
        return true
    }

    /** Dismissal by the user. No grace: the next Back press leaves playback. */
    fun dismiss() {
        isVisible = false
        activePicker = TrackPicker.None
        autoHiddenAtMillis = 0L
    }

    /** Dismissal by the timeout. The fade-out, and the grace after it, both swallow Back. */
    fun autoHide() {
        isVisible = false
        activePicker = TrackPicker.None
        autoHiddenAtMillis = SystemClock.elapsedRealtime()
    }

    /**
     * Consumes a Back press that should act on the overlay rather than leave playback, and reports
     * whether it did. The window runs from the start of the fade-out until [BACK_GRACE_MILLIS]
     * after it ends, and is spent once used so a following press falls through to Back.
     */
    fun consumeBackIfControlsShowing(): Boolean {
        if (isVisible) {
            dismiss()
            return true
        }
        val now = SystemClock.elapsedRealtime()
        val graceEndsAt = autoHiddenAtMillis + CONTROLS_FADE_MILLIS + BACK_GRACE_MILLIS
        val withinGrace = autoHiddenAtMillis != 0L && now in autoHiddenAtMillis..graceEndsAt
        if (withinGrace) autoHiddenAtMillis = 0L
        return withinGrace
    }

    /**
     * Back, in the order it unwinds: an open menu closes, then a visible overlay is dismissed, and
     * only then does [leavePlayback] run.
     *
     * One definition, because the press reaches this from two places. The screen's back handler
     * catches it when nothing inside the player holds focus; [catchBackPress] catches it on the
     * focused control when something does, since the focusable player surface swallows the press
     * before the handler would ever see it.
     */
    fun handleBack(leavePlayback: () -> Unit) {
        when {
            consumeBackForPicker() -> Unit
            !consumeBackIfControlsShowing() -> leavePlayback()
        }
    }
}

@Composable
private fun rememberPlaybackControlsState() = remember { PlaybackControlsState() }

/**
 * Bottom bar over a gradient scrim: what is playing, how long is left, how far in you are, and the
 * two buttons that pick an audio track and a subtitle.
 *
 * The menu either button opens is drawn by the caller, over the video rather than inside this
 * band, so the bar itself stays the height the scrim is cut for.
 *
 * The position is polled rather than observed because ExoPlayer exposes no position flow, and a
 * bar that only moved on a key press would read as broken while it sits on screen.
 */
@Composable
private fun PlaybackProgressOverlay(
    player: androidx.media3.common.Player,
    metadata: PlexPlaybackMetadata,
    audioActive: Boolean,
    subtitlesActive: Boolean,
    showAudio: Boolean,
    showSubtitles: Boolean,
    activePicker: TrackPicker,
    onTogglePicker: (TrackPicker) -> Unit,
    audioButtonFocus: FocusRequester,
    subtitleButtonFocus: FocusRequester,
    onActionButtonFocused: (TrackPicker) -> Unit,
    onUserActivity: () -> Unit,
    onDismiss: () -> Unit,
    onBackPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    var positionMillis by remember { mutableLongStateOf(player.currentPosition.coerceAtLeast(0L)) }
    var durationMillis by remember { mutableLongStateOf(player.duration) }
    LaunchedEffect(player) {
        while (isActive) {
            positionMillis = player.currentPosition.coerceAtLeast(0L)
            durationMillis = player.duration
            delay(POSITION_POLL_INTERVAL_MILLIS)
        }
    }

    val hasDuration = durationMillis > 0L
    val fraction = if (hasDuration) {
        (positionMillis.toFloat() / durationMillis.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    // The fraction animates rather than snapping: an unpolled bar would jump between samples and
    // a snapped one would stutter, exactly as on the poster cards.
    val animatedFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(PLAYBACK_PROGRESS_ANIMATION_MILLIS),
        label = "playbackProgress"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(OVERLAY_SCRIM_HEIGHT)
            .background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                )
            )
            .padding(horizontal = OVERLAY_HORIZONTAL_PADDING, vertical = OVERLAY_VERTICAL_PADDING),
        contentAlignment = Alignment.BottomStart
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            val showLine = metadata.showTitle?.takeIf { it.isNotBlank() }
            val episodeSubtitle = overlayEpisodeLine(metadata)
            // An item Plex named nothing at all still gets a line, the way the plain title did.
            val fallbackLine = stringResource(R.string.plex_episode)
                .takeIf { showLine == null && episodeSubtitle == null }
            if (showLine != null || fallbackLine != null) {
                Text(
                    text = showLine ?: fallbackLine.orEmpty(),
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(OVERLAY_TITLE_GAP))
            }
            if (episodeSubtitle != null) {
                Text(
                    text = episodeSubtitle,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.9f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(OVERLAY_TITLE_GAP))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatPlaybackTime(positionMillis),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.9f)
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = if (hasDuration) formatPlaybackTime(positionMillis - durationMillis) else "--:--",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }
            Spacer(modifier = Modifier.height(OVERLAY_PROGRESS_GAP))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(OVERLAY_PROGRESS_HEIGHT)
                    .clip(OVERLAY_PROGRESS_SHAPE)
                    .background(Color.White.copy(alpha = 0.3f))
            ) {
                if (hasDuration) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedFraction)
                            .fillMaxHeight()
                            .clip(OVERLAY_PROGRESS_SHAPE)
                            .background(PlexAmber)
                    )
                }
            }
            BottomPlayerActionButtons(
                activePicker = activePicker,
                audioActive = audioActive,
                subtitlesActive = subtitlesActive,
                showAudio = showAudio,
                showSubtitles = showSubtitles,
                onTogglePicker = onTogglePicker,
                audioButtonFocus = audioButtonFocus,
                subtitleButtonFocus = subtitleButtonFocus,
                onActionButtonFocused = onActionButtonFocused,
                onUserActivity = onUserActivity,
                onDismiss = onDismiss,
                onBackPress = onBackPress
            )
        }
    }
}

/** One chip in a track picker: what it reads, whether it is the one in use, and what it selects. */
@Immutable
internal data class TrackChoice(
    val key: String,
    val label: String,
    val selected: Boolean,
    val streamId: Long?
)

/** The audio options, one per track, with the one in use marked. */
internal fun audioChoices(
    audio: List<PlexAudioStream>,
    selectedAudioId: Long?
): List<TrackChoice> = audio.map { stream ->
    TrackChoice(
        key = "audio-${stream.id}",
        label = stream.label,
        selected = stream.id == selectedAudioId,
        streamId = stream.id
    )
}

/**
 * The subtitle options: Off first, then one per track.
 *
 * Off leads because it is the one every episode needs a way back to — turning subtitles off is a
 * correction, and the chip the user wants when they open this is the one already showing.
 */
internal fun subtitleChoices(
    subtitles: List<PlexSubtitleStream>,
    selectedSubtitleId: Long?,
    offLabel: String
): List<TrackChoice> = buildList {
    add(
        TrackChoice(
            key = "subtitle-off",
            label = offLabel,
            selected = selectedSubtitleId == null,
            streamId = null
        )
    )
    subtitles.forEach { stream ->
        add(
            TrackChoice(
                key = "subtitle-${stream.id}",
                label = stream.label,
                selected = stream.id == selectedSubtitleId,
                streamId = stream.id
            )
        )
    }
}


/**
 * The drop-down above a track button: what kind of track it is choosing, and one option per row.
 *
 * It opens on the option already in use, so it shows where playback currently stands rather than
 * starting the user at the top of the list, and it scrolls when there are more tracks than fit —
 * a file can carry a dozen subtitle streams and they all have to be reachable.
 */
@Composable
private fun VerticalTrackMenu(
    label: String,
    choices: List<TrackChoice>,
    onSelect: (TrackChoice) -> Unit,
    onClose: () -> Unit,
    onUserActivity: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()

    // The row to open on is settled in its own effect so the requester has a composed row to sit on
    // before it asks for focus: a track further down the list is not composed until it is scrolled
    // near. Keyed on the selection, so it re-runs if playback moves the selection while the menu is
    // up.
    var landingIndex by remember { mutableIntStateOf(-1) }
    LaunchedEffect(choices.firstOrNull { it.selected }?.key) {
        landingIndex = choices.indexOfFirst { it.selected }.coerceAtLeast(0)
    }
    LaunchedEffect(landingIndex) {
        if (landingIndex >= 0 && choices.isNotEmpty()) {
            listState.scrollToItem(landingIndex)
            focusRequester.requestFocus()
        }
    }

    val focusIndex = landingIndex.coerceIn(0, (choices.size - 1).coerceAtLeast(0))
    Column(
        modifier = modifier
            // Back has to be caught on the menu itself. Focus is on one of the rows below, and a
            // press from there would otherwise be swallowed on the way past the focusable player
            // surface and never reach the screen's back handler.
            .catchBackPress(onBack)
            .widthIn(min = MENU_MIN_WIDTH, max = MENU_MAX_WIDTH)
            .clip(MENU_SHAPE)
            .background(MENU_COLOR)
            .border(
                width = MENU_BORDER_WIDTH,
                color = MENU_BORDER_COLOR,
                shape = MENU_SHAPE
            )
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.7f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(
                start = MENU_HEADER_PADDING,
                end = MENU_HEADER_PADDING,
                top = MENU_PADDING,
                bottom = MENU_HEADER_GAP
            )
        )
        LazyColumn(
            state = listState,
            modifier = Modifier.heightIn(max = MENU_MAX_HEIGHT),
            contentPadding = PaddingValues(bottom = MENU_PADDING)
        ) {
            itemsIndexed(choices, key = { _, choice -> choice.key }) { index, choice ->
                val reportFocus = Modifier.onFocusChanged {
                    if (it.isFocused) onUserActivity()
                }
                val focusClaim = if (index == focusIndex) {
                    reportFocus.focusRequester(focusRequester)
                } else {
                    reportFocus
                }
                TrackMenuItem(
                    choice = choice,
                    isLast = index == choices.lastIndex,
                    onClick = { onSelect(choice) },
                    onClose = onClose,
                    modifier = focusClaim
                )
            }
        }
    }
}

/**
 * One option in the drop-down.
 *
 * The option in use is marked in amber and ticked, which is the read the whole menu exists to
 * give. Focus is a white fill rather than a ring: these are stacked rows, and a ring on one row
 * draws a box inside the menu while its neighbours stay bare.
 */
@Composable
private fun TrackMenuItem(
    choice: TrackChoice,
    isLast: Boolean,
    onClick: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = MENU_ITEM_INSET, vertical = MENU_ITEM_INSET)
            // Down off the end of the menu closes it and hands focus back to the button below,
            // which is where the user came from. Back is left alone so the screen's back handler
            // unwinds the menu in the same order.
            .onPreviewKeyEvent { keyEvent ->
                if (isLast && keyEvent.type == KeyEventType.KeyDown &&
                    keyEvent.key == Key.DirectionDown
                ) {
                    onClose()
                    true
                } else {
                    false
                }
            },
        shape = ButtonDefaults.shape(shape = MENU_ITEM_SHAPE),
        colors = ButtonDefaults.colors(
            containerColor = if (choice.selected) MENU_ITEM_SELECTED_COLOR else Color.Transparent,
            contentColor = if (choice.selected) PlexAmber else Color.White,
            focusedContainerColor = MENU_ITEM_FOCUSED_COLOR,
            focusedContentColor = Color.White,
            pressedContainerColor = MENU_ITEM_FOCUSED_COLOR,
            pressedContentColor = Color.White
        ),
        // No growth on focus. These are a list, and an item growing would shove the rows around it
        // along while the user is stepping through them with the D-pad.
        scale = ButtonDefaults.scale(focusedScale = 1f, pressedScale = MENU_ITEM_PRESSED_SCALE),
        contentPadding = PaddingValues(horizontal = MENU_ITEM_HORIZONTAL_PADDING),
        border = ButtonDefaults.border(border = Border.None)
    ) {
        Text(
            text = choice.label,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        if (choice.selected) {
            Spacer(modifier = Modifier.width(MENU_ITEM_CHECK_GAP))
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = PlexAmber,
                modifier = Modifier.size(MENU_ITEM_CHECK_SIZE)
            )
        }
    }
}

/**
 * The two buttons at the bottom right: which audio track is playing, and which subtitle is on.
 *
 * Each tints amber when it has something selected or its own menu open, which is the whole status
 * read — spelling the current track out would want a second line under the icon and make the pair
 * taller than the bar they sit in.
 *
 * Down from here dismisses the overlay, so a stray press does not leave focus parked on a button
 * the user can no longer see, and the next D-pad press brings the overlay back.
 */
@Composable
private fun BottomPlayerActionButtons(
    activePicker: TrackPicker,
    audioActive: Boolean,
    subtitlesActive: Boolean,
    showAudio: Boolean,
    showSubtitles: Boolean,
    onTogglePicker: (TrackPicker) -> Unit,
    audioButtonFocus: FocusRequester,
    subtitleButtonFocus: FocusRequester,
    onActionButtonFocused: (TrackPicker) -> Unit,
    onUserActivity: () -> Unit,
    onDismiss: () -> Unit,
    onBackPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = OVERLAY_ACTIONS_GAP)
            // Focus lands on one of these buttons while the overlay is up, and from there a Back
            // press would be swallowed on the way past the focusable player surface rather than
            // reaching the screen's back handler.
            .catchBackPress(onBackPress)
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionDown) {
                    onDismiss()
                    true
                } else {
                    false
                }
            },
        horizontalArrangement = Arrangement.spacedBy(ACTION_BUTTON_SPACING, Alignment.End),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showAudio) {
            TrackActionButton(
                icon = Icons.Rounded.Audiotrack,
                contentDescription = stringResource(R.string.plex_select_audio),
                active = audioActive || activePicker == TrackPicker.Audio,
                onClick = { onTogglePicker(TrackPicker.Audio) },
                focusRequester = audioButtonFocus,
                onFocused = { onActionButtonFocused(TrackPicker.Audio) },
                onUserActivity = onUserActivity
            )
        }
        if (showSubtitles) {
            TrackActionButton(
                icon = Icons.Rounded.Subtitles,
                contentDescription = stringResource(R.string.plex_select_subtitles),
                active = subtitlesActive || activePicker == TrackPicker.Subtitles,
                onClick = { onTogglePicker(TrackPicker.Subtitles) },
                focusRequester = subtitleButtonFocus,
                onFocused = { onActionButtonFocused(TrackPicker.Subtitles) },
                onUserActivity = onUserActivity
            )
        }
    }
}

/**
 * One round track button. Focus is read from a white ring and a small growth rather than the
 * colour swap the TV library uses, because these sit on a translucent scrim where a solid fill
 * would swallow the video behind them.
 */
@Composable
private fun TrackActionButton(
    icon: ImageVector,
    contentDescription: String,
    active: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester,
    onFocused: () -> Unit,
    onUserActivity: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(ACTION_BUTTON_SIZE)
            .focusRequester(focusRequester)
            .onFocusChanged { if (it.isFocused) onFocused() }
            .onKeyEvent {
                onUserActivity()
                false
            },
        shape = ButtonDefaults.shape(shape = CircleShape),
        colors = IconButtonDefaults.colors(
            containerColor = if (active) ACTION_BUTTON_ACTIVE_COLOR else ACTION_BUTTON_COLOR,
            contentColor = if (active) PlexAmber else Color.White,
            focusedContainerColor = if (active) {
                ACTION_BUTTON_ACTIVE_FOCUSED_COLOR
            } else {
                ACTION_BUTTON_FOCUSED_COLOR
            },
            focusedContentColor = if (active) PlexAmber else Color.White,
            pressedContainerColor = if (active) {
                ACTION_BUTTON_ACTIVE_FOCUSED_COLOR
            } else {
                ACTION_BUTTON_FOCUSED_COLOR
            },
            pressedContentColor = if (active) PlexAmber else Color.White
        ),
        scale = IconButtonDefaults.scale(focusedScale = ACTION_BUTTON_FOCUSED_SCALE),
        border = IconButtonDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(ACTION_BUTTON_FOCUSED_BORDER_WIDTH, Color.White),
                shape = CircleShape
            ),
            border = Border(
                border = BorderStroke(
                    ACTION_BUTTON_BORDER_WIDTH,
                    if (active) PlexAmber else ACTION_BUTTON_BORDER_COLOR
                ),
                shape = CircleShape
            )
        )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(ACTION_BUTTON_ICON_SIZE)
        )
    }
}

/**
 * The line under the show name: "S2 · E5 — 8:00 AM", read as where the episode sits in its
 * season and then what it is called. Every piece is optional, so a special with no season number
 * is "E5 — Title", a film with neither is just its title, and nothing at all is no line.
 */
internal fun overlayEpisodeLine(metadata: PlexPlaybackMetadata): String? {
    val title = metadata.episodeTitle?.takeIf { it.isNotBlank() }
    val positions = listOfNotNull(
        metadata.seasonNumber?.let { "S$it" },
        metadata.episodeNumber?.let { "E$it" }
    ).joinToString(" · ").ifEmpty { null }
    return when {
        positions != null && title != null -> "$positions — $title"
        positions != null -> positions
        else -> title
    }
}

/**
 * The pre-playback notice: a plain-language line about the wait, its technical detail, and the
 * spinner they sit above — the one the black shutter leaves to the notice while it is up.
 */
@Composable
private fun TranscodeNoticeContent(notice: TranscodeNotice, modifier: Modifier = Modifier) {
    val headline = stringResource(
        when {
            notice.videoTranscoded && notice.audioTranscoded -> R.string.plex_transcoding_video_and_audio
            notice.videoTranscoded -> R.string.plex_transcoding_video
            else -> R.string.plex_transcoding_audio
        }
    )
    Column(
        modifier = modifier.padding(horizontal = NOTICE_HORIZONTAL_PADDING),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = headline,
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(NOTICE_TEXT_GAP))
        Text(
            text = transcodeDetailLine(notice),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.9f),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(NOTICE_SPINNER_GAP))
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary
        )
    }
}

/**
 * The technical line under the headline, in the words the connection-status toast uses:
 * "Video Transcoded · EAC3 · Audio Direct · H264". The codec is dropped when Plex reports none
 * for a stream, so the line stays short on decisions that leave it out.
 */
internal fun transcodeDetailLine(notice: TranscodeNotice): String =
    listOfNotNull(
        transcodeStreamSegment("Video", notice.video.mode, notice.video.codec),
        transcodeStreamSegment("Audio", notice.audio.mode, notice.audio.codec)
    ).joinToString(TRANSCODE_SEGMENT_SEPARATOR)

private fun transcodeStreamSegment(label: String, mode: PlexStreamMode, codec: String?): String {
    val streamCodec = codec?.uppercase()?.let { " · $it" }.orEmpty()
    return "$label ${mode.displayName}$streamCodec"
}

/** mm:ss, or h:mm:ss once the value runs past an hour. Negative values keep their minus sign. */
private fun formatPlaybackTime(millis: Long): String {
    val roundedSeconds = (millis + 500) / 1000
    val isNegative = roundedSeconds < 0
    val totalSeconds = if (isNegative) -roundedSeconds else roundedSeconds
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    val sign = if (isNegative) "-" else ""
    return if (hours > 0) {
        String.format(Locale.US, "%s%d:%02d:%02d", sign, hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%s%d:%02d", sign, minutes, seconds)
    }
}

/** How often the overlay samples the play position while it is on screen. */
private const val POSITION_POLL_INTERVAL_MILLIS = 250L

/** How long the overlay takes to fade in and out. The back grace is timed from the fade's end. */
private const val CONTROLS_FADE_MILLIS = 300

/** How long after the fade-out finishes a Back press is still read as "dismiss the overlay". */
private const val BACK_GRACE_MILLIS = 300

/** How long the progress overlay stays up after the last D-pad press. */
private const val CONTROLS_AUTO_HIDE_MILLIS = 4_000L

/** Room for the scrim to fade out over the video rather than cut a hard edge. It only has to hold
 * the bottom bar inside its vertical padding — the names, the times, the progress bar and the
 * action buttons. A menu is a pop-up on top of the scrim, so it is not counted here. */
private val OVERLAY_SCRIM_HEIGHT = 288.dp
private val OVERLAY_HORIZONTAL_PADDING = 48.dp
private val OVERLAY_VERTICAL_PADDING = 48.dp
private val OVERLAY_TITLE_GAP = 12.dp
private val OVERLAY_PROGRESS_GAP = 10.dp
private val OVERLAY_PROGRESS_HEIGHT = 6.dp
private val OVERLAY_PROGRESS_RADIUS = 3.dp
private val OVERLAY_PROGRESS_SHAPE = RoundedCornerShape(OVERLAY_PROGRESS_RADIUS)

/** Gap between the progress bar and the action buttons under it. */
private val OVERLAY_ACTIONS_GAP = 16.dp

/** Matches the poster cards, so the bar moves the same way everywhere in the app. */
private const val PLAYBACK_PROGRESS_ANIMATION_MILLIS = 350

/** Side padding for the notice, so its lines stay clear of the screen edge on small displays. */
private val NOTICE_HORIZONTAL_PADDING = 48.dp

/** Gap between the notice's plain-language line and its technical one. */
private val NOTICE_TEXT_GAP = 8.dp

/** Gap between the notice's text and its spinner. */
private val NOTICE_SPINNER_GAP = 24.dp

private const val TRANSCODE_SEGMENT_SEPARATOR = " · "

/** The pair of round track buttons at the bottom right. */
private val ACTION_BUTTON_SIZE = 48.dp
private val ACTION_BUTTON_SPACING = 12.dp
private val ACTION_BUTTON_ICON_SIZE = 24.dp
private val ACTION_BUTTON_COLOR = Color(0x1FFFFFFF)
private val ACTION_BUTTON_FOCUSED_COLOR = Color(0x40FFFFFF)
private val ACTION_BUTTON_ACTIVE_COLOR = Color(0x33FFB703)
private val ACTION_BUTTON_ACTIVE_FOCUSED_COLOR = Color(0x59FFB703)
private val ACTION_BUTTON_BORDER_COLOR = Color(0x33FFFFFF)

/**
 * Focus on these is read from the ring and the growth rather than a colour swap, because a filled
 * focus state would swallow the video behind a scrim this translucent.
 */
private const val ACTION_BUTTON_FOCUSED_SCALE = 1.1f
private val ACTION_BUTTON_BORDER_WIDTH = 1.dp
private val ACTION_BUTTON_FOCUSED_BORDER_WIDTH = 2.dp

/** How far in from the right edge the audio button's menu is inset, so it lines up with that
 * button rather than with the one to its right. */
private val ACTION_BUTTON_TRAILING_INSET = ACTION_BUTTON_SIZE + ACTION_BUTTON_SPACING

/** Gap between the bottom of a menu and the progress bar underneath it. */
private val MENU_PROGRESS_CLEARANCE = 8.dp

/** How far up from the bottom of the screen a menu starts, measured back from the button it
 * belongs to: the scrim's own padding, the gap above the buttons, and the button itself. The
 * clearance on top of that keeps the menu's own edge off the progress bar, which lands at exactly
 * the button's top if the menu is hung straight off it. */
private val OVERLAY_MENU_BOTTOM_INSET =
    OVERLAY_VERTICAL_PADDING + OVERLAY_ACTIONS_GAP + ACTION_BUTTON_SIZE + MENU_PROGRESS_CLEARANCE

/** The drop-down itself. It is opaque rather than translucent: it sits over the episode's names,
 * and a track label has to stay readable wherever it lands. */
private val MENU_SHAPE = RoundedCornerShape(12.dp)
private val MENU_COLOR = Color(0xF21C1C1E)
private val MENU_BORDER_COLOR = Color(0x33FFFFFF)
private val MENU_BORDER_WIDTH = 1.dp
private val MENU_PADDING = 8.dp
private val MENU_HEADER_PADDING = 16.dp
private val MENU_HEADER_GAP = 4.dp

/** Wide enough for "English (EAC3 · 6ch)" to sit on one line, narrow enough to stay clear of the
 * left edge on a small display. Longer labels are cut off rather than wrapped. */
private val MENU_MIN_WIDTH = 240.dp
private val MENU_MAX_WIDTH = 360.dp

/** How many rows the menu shows before it scrolls. The rest of the screen is left to the video,
 * so the menu stops well short of the top of the frame. */
private val MENU_MAX_HEIGHT = 260.dp

private val MENU_ITEM_SHAPE = RoundedCornerShape(8.dp)
private val MENU_ITEM_SELECTED_COLOR = Color(0x33FFB703)
private val MENU_ITEM_FOCUSED_COLOR = Color(0x40FFFFFF)
private val MENU_ITEM_INSET = 4.dp
private val MENU_ITEM_HORIZONTAL_PADDING = 12.dp
private val MENU_ITEM_CHECK_GAP = 12.dp
private val MENU_ITEM_CHECK_SIZE = 18.dp
private const val MENU_ITEM_PRESSED_SCALE = 0.98f
