package com.neilturner.playerexp.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.neilturner.playerexp.R
import com.neilturner.playerexp.data.plex.PlexPlaybackStatus
import com.neilturner.playerexp.data.plex.PlexStreamPlayback
import com.neilturner.playerexp.ui.modifiers.playerOverlayControls
import com.neilturner.playerexp.ui.theme.PlexAmber
import com.neilturner.playerexp.ui.viewmodels.PlexPlayerUiState
import com.neilturner.playerexp.ui.viewmodels.PlexPlayerViewModel
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

    // Back acts on the progress overlay before it acts on the screen: a visible overlay is
    // dismissed rather than leaving the episode, and so is a back press that arrives during the
    // grace window after the overlay hid itself.
    BackHandler {
        if (!controls.consumeBackIfControlsShowing()) {
            viewModel.releasePlayer()
            onBack()
        }
    }

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
                    episodeTitle = state.episodeTitle,
                    controls = controls,
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
    episodeTitle: String?,
    controls: PlaybackControlsState,
    modifier: Modifier = Modifier
) {
    var showConnectionStatus by remember { mutableStateOf(false) }
    LaunchedEffect(playbackStatus) {
        if (playbackStatus != null) {
            showConnectionStatus = true
            delay(CONNECTION_STATUS_DURATION_MILLIS)
            showConnectionStatus = false
        }
    }

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

    Box(modifier = modifier.playerOverlayControls(controls::reveal)) {
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
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        )
        AnimatedVisibility(
            visible = showConnectionStatus && playbackStatus != null,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = CONNECTION_STATUS_BOTTOM_MARGIN),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            ConnectionStatusOverlay(playbackStatus = requireNotNull(playbackStatus))
        }
        AnimatedVisibility(
            visible = controls.isVisible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(animationSpec = tween(CONTROLS_FADE_MILLIS)),
            // The fade is given a fixed duration so the back-press grace that follows it can be
            // timed from its end rather than guessed at.
            exit = fadeOut(animationSpec = tween(CONTROLS_FADE_MILLIS))
        ) {
            PlaybackProgressOverlay(player = player, title = episodeTitle)
        }
    }
}

/**
 * Whether the hidden progress overlay is showing, and what Back should do about it.
 *
 * Back dismisses a visible overlay instead of leaving playback. Once the overlay hides itself on
 * its timeout, the overlay still fades out, and a back press during that fade — and for a short
 * grace window after it finishes — is still read as "dismiss the overlay", because the user saw it
 * on screen moments earlier and meant to dismiss it, not to stop playback. Dismissing the overlay
 * with Back leaves no grace, so a second Back press leaves playback straight away.
 */
private class PlaybackControlsState {
    var isVisible by mutableStateOf(false)
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

    /** Dismissal by the user. No grace: the next Back press leaves playback. */
    fun dismiss() {
        isVisible = false
        autoHiddenAtMillis = 0L
    }

    /** Dismissal by the timeout. The fade-out, and the grace after it, both swallow Back. */
    fun autoHide() {
        isVisible = false
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
}

@Composable
private fun rememberPlaybackControlsState() = remember { PlaybackControlsState() }

/**
 * Bottom bar over a gradient scrim: what is playing, how long is left, and how far in you are.
 *
 * The position is polled rather than observed because ExoPlayer exposes no position flow, and a
 * bar that only moved on a key press would read as broken while it sits on screen.
 */
@Composable
private fun PlaybackProgressOverlay(
    player: androidx.media3.common.Player,
    title: String?,
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
        Column {
            if (!title.isNullOrBlank()) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
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
        }
    }
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

@Composable
private fun ConnectionStatusOverlay(playbackStatus: PlexPlaybackStatus) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.92f))
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        ConnectionStatusRow("Video", playbackStatus.video)
        ConnectionStatusRow("Audio", playbackStatus.audio)
    }
}

@Composable
private fun ConnectionStatusRow(label: String, status: PlexStreamPlayback) {
    val codec = status.codec?.uppercase()?.let { " · $it" }.orEmpty()
    Text(
        text = "$label  ${status.mode.displayName}$codec",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.inverseOnSurface
    )
}

private const val CONNECTION_STATUS_DURATION_MILLIS = 4_000L

/** Margin above the very bottom, which keeps the start-up status above the progress overlay. */
private val CONNECTION_STATUS_BOTTOM_MARGIN = 56.dp

/** How long the progress overlay stays up after the last D-pad press. */
private const val CONTROLS_AUTO_HIDE_MILLIS = 4_000L

/** How long the overlay takes to fade in and out. The back grace is timed from the fade's end. */
private const val CONTROLS_FADE_MILLIS = 300

/** How long after the fade-out finishes a Back press is still read as "dismiss the overlay". */
private const val BACK_GRACE_MILLIS = 300

/** How often the overlay samples the play position while it is on screen. */
private const val POSITION_POLL_INTERVAL_MILLIS = 250L

/** Room for the scrim to fade out over the video rather than cut a hard edge. */
private val OVERLAY_SCRIM_HEIGHT = 180.dp
private val OVERLAY_HORIZONTAL_PADDING = 48.dp
private val OVERLAY_VERTICAL_PADDING = 48.dp
private val OVERLAY_TITLE_GAP = 12.dp
private val OVERLAY_PROGRESS_GAP = 10.dp
private val OVERLAY_PROGRESS_HEIGHT = 6.dp
private val OVERLAY_PROGRESS_RADIUS = 3.dp
private val OVERLAY_PROGRESS_SHAPE = RoundedCornerShape(OVERLAY_PROGRESS_RADIUS)

/** Matches the poster cards, so the bar moves the same way everywhere in the app. */
private const val PLAYBACK_PROGRESS_ANIMATION_MILLIS = 350
