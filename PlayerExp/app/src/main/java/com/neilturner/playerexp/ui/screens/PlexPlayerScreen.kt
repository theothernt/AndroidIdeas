package com.neilturner.playerexp.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Subtitles
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import android.os.SystemClock
import androidx.media3.ui.compose.material3.Player
import androidx.media3.common.util.UnstableApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.SubtitleView
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.neilturner.playerexp.R
import com.neilturner.playerexp.data.plex.PlexAudioStream
import com.neilturner.playerexp.data.plex.PlexPlaybackStatus
import com.neilturner.playerexp.data.plex.PlexStreamMode
import com.neilturner.playerexp.data.plex.PlexStreamPlayback
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
import kotlin.math.roundToInt
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
                    currentPosition = { player.currentPosition },
                    duration = { player.duration },
                    videoSurface = {
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
                                    // While the transcode notice is up it carries the spinner of
                                    // its own, so the shutter stays a plain black box and nothing
                                    // spins twice.
                                    if (state.transcodeNotice == null || state.playbackStatus != null) {
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

                        // The view is always there, whether or not there are any cues to draw.
                        // The styled ASS subtitles are drawn by a child of it, and that child
                        // has to be in the tree before the first one is rendered.
                        AndroidView(
                            factory = { context ->
                                SubtitleView(context).apply {
                                    setUserDefaultStyle()
                                    setUserDefaultTextSize()
                                    // The ASS subtitles ride in the same view, drawn over the
                                    // picture with the styling the file asked for.
                                    viewModel.assSupport.attachTo(this)
                                }
                            },
                            update = { subtitleView ->
                                subtitleView.setCues(cues)
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    },
                    playbackStatus = state.playbackStatus,
                    transcodeNotice = state.transcodeNotice,
                    metadata = state.metadata,
                    availableSubtitles = state.availableSubtitles,
                    selectedSubtitleId = state.selectedSubtitleId,
                    onSelectSubtitle = viewModel::selectSubtitle,
                    availableAudio = state.availableAudio,
                    selectedAudioId = state.selectedAudioId,
                    onSelectAudio = viewModel::selectAudio,
                    playbackSpeed = state.playbackSpeed,
                    onSelectSpeed = viewModel::setPlaybackSpeed,
                    videoWidth = state.videoWidth,
                    videoHeight = state.videoHeight,
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

/**
 * The chrome that sits over whatever is playing: the bar along the bottom, the rail of buttons
 * on it, and the drawer that comes in from the right.
 *
 * The picture itself is a slot, so that the same chrome can sit over a video and over the still
 * picture the mock player uses. Everything else here is the real thing.
 */
@Composable
@UnstableApi
internal fun PlaybackContent(
    currentPosition: () -> Long,
    duration: () -> Long,
    videoSurface: @Composable () -> Unit,
    playbackStatus: PlexPlaybackStatus?,
    transcodeNotice: TranscodeNotice?,
    metadata: PlexPlaybackMetadata,
    availableSubtitles: List<PlexSubtitleStream>,
    selectedSubtitleId: Long?,
    onSelectSubtitle: (Long?) -> Unit,
    availableAudio: List<PlexAudioStream>,
    selectedAudioId: Long?,
    onSelectAudio: (Long?) -> Unit,
    playbackSpeed: Float,
    onSelectSpeed: (Float) -> Unit,
    videoWidth: Int?,
    videoHeight: Int?,
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
        if (controls.isVisible && controls.activeDrawer == null) {
            delay(CONTROLS_AUTO_HIDE_MILLIS)
            controls.autoHide()
        }
    }

    // A button is only drawn for a kind of track the item actually carries, so an episode with no
    // subtitles is never sent to a button that is not there.
    val hasAudio = availableAudio.isNotEmpty()
    val hasSubtitles = availableSubtitles.isNotEmpty()
    // Where focus lands each time the overlay comes up: the button it was last on, so stepping
    // away and back does not make the user re-navigate from the start each time.
    var lastRailKey by remember { mutableStateOf(RAIL_ACTION_MORE) }

    val railFocusRequesters = remember { List(MAX_RAIL_ACTIONS) { FocusRequester() } }
    val drawerFocusRequesters = remember { List(MAX_DRAWER_ROWS) { FocusRequester() } }

    val railActions = buildList {
        if (hasAudio) {
            add(
                RailAction(
                    key = RAIL_ACTION_AUDIO,
                    icon = Icons.Outlined.Audiotrack,
                    contentDescription = stringResource(R.string.plex_select_audio),
                    active = controls.activeDrawer == DrawerPage.Audio
                )
            )
        }
        if (hasSubtitles) {
            add(
                RailAction(
                    key = RAIL_ACTION_SUBTITLES,
                    icon = Icons.Outlined.Subtitles,
                    contentDescription = stringResource(R.string.plex_select_subtitles),
                    // Amber while a subtitle is on, the way a captions button lights up when
                    // captions are showing, and while its own drawer is open.
                    active = controls.activeDrawer == DrawerPage.Subtitles || selectedSubtitleId != null
                )
            )
        }
        add(
            RailAction(
                key = RAIL_ACTION_MORE,
                icon = Icons.Outlined.MoreVert,
                contentDescription = stringResource(R.string.plex_more_options),
                active = controls.activeDrawer == DrawerPage.More
            )
        )
    }
    val railIndex = railActions.indexOfFirst { it.key == lastRailKey }.coerceAtLeast(0)

    val drawerContent = drawerContentFor(
        page = controls.activeDrawer,
        availableAudio = availableAudio,
        selectedAudioId = selectedAudioId,
        availableSubtitles = availableSubtitles,
        selectedSubtitleId = selectedSubtitleId,
        playbackSpeed = playbackSpeed,
        playbackStatus = playbackStatus,
        videoWidth = videoWidth,
        videoHeight = videoHeight
    )
    // The rows are kept for as long as the panel is on the screen, so that the panel still has
    // something to slide away with. The page it is showing is cleared at the same moment the
    // drawer starts to close, and a panel with no rows in it would not move at all.
    var shownContent by remember { mutableStateOf<DrawerContent?>(null) }
    if (drawerContent != null) {
        shownContent = drawerContent
    }

    // Focus rides the overlay. With the bar up, focus sits on the rail; with a drawer open, the
    // drawer keeps the focus it took for itself; and with the overlay gone, focus goes back to
    // the player surface, which is the node whose D-pad presses reveal the overlay in the first
    // place.
    LaunchedEffect(controls.isVisible, controls.activeDrawer) {
        when {
            controls.activeDrawer != null -> Unit
            !controls.isVisible -> playerFocusRequester.requestFocus()
            else -> {
                // The drawer is on its way out, and it keeps the focus while it goes so that
                // the row it is showing does not lose its highlight halfway down the screen.
                // The focus goes back to the rail once it has gone.
                delay(CONTROLS_FADE_MILLIS.toLong())
                if (controls.isVisible) {
                    railFocusRequesters.getOrNull(railIndex)?.requestFocus()
                }
            }
        }
    }

    // The drawer slides in from the right and slides back out again, so that it can be seen to
    // come and go. The panel is kept in the tree until the slide out has finished, which is
    // what lets it be seen leaving.
    val drawerSlide by animateFloatAsState(
        targetValue = if (controls.activeDrawer != null) 0f else 1f,
        animationSpec = tween(CONTROLS_FADE_MILLIS),
        label = "drawerSlide"
    )
    val drawerOnScreen = controls.activeDrawer != null || drawerSlide < 1f
    val drawerWidthPx = with(LocalDensity.current) { drawerWidth().toPx() }

    // The bar comes and goes the same way, fading and sliding up from the bottom edge.
    val barVisibility by animateFloatAsState(
        targetValue = if (controls.isVisible) 1f else 0f,
        animationSpec = tween(CONTROLS_FADE_MILLIS),
        label = "barVisibility"
    )
    val barHeightPx = with(LocalDensity.current) { OVERLAY_SCRIM_HEIGHT.toPx() }
    val onRailSelect: (RailAction) -> Unit = { action ->
        val page = when (action.key) {
            RAIL_ACTION_AUDIO -> DrawerPage.Audio
            RAIL_ACTION_SUBTITLES -> DrawerPage.Subtitles
            RAIL_ACTION_MORE -> DrawerPage.More
            else -> null
        }
        if (page != null) {
            // Centre on a button opens the drawer it owns; centre again on that button closes it.
            if (controls.activeDrawer == page) controls.popDrawer() else controls.openDrawer(page)
        }
    }

    val onDrawerSelect: (DrawerItem) -> Unit = { item ->
        when {
            item.informational -> Unit
            item.expandable -> {
                val page = when (item.key) {
                    DRAWER_KEY_SPEED -> DrawerPage.Speed
                    DRAWER_KEY_STREAM_INFO -> DrawerPage.StreamInfo
                    else -> null
                }
                if (page != null) controls.openDrawer(page)
            }
            item.key.startsWith("$DRAWER_KEY_PREFIX_AUDIO-") -> {
                onSelectAudio(item.key.removePrefix("$DRAWER_KEY_PREFIX_AUDIO-").toLongOrNull())
                controls.closeDrawer()
            }
            item.key == "$DRAWER_KEY_PREFIX_SUBTITLES-off" -> {
                onSelectSubtitle(null)
                controls.closeDrawer()
            }
            item.key.startsWith("$DRAWER_KEY_PREFIX_SUBTITLES-") -> {
                onSelectSubtitle(item.key.removePrefix("$DRAWER_KEY_PREFIX_SUBTITLES-").toLongOrNull())
                controls.closeDrawer()
            }
            item.key.startsWith("$DRAWER_KEY_PREFIX_SPEED-") -> {
                onSelectSpeed(item.key.removePrefix("$DRAWER_KEY_PREFIX_SPEED-").toFloatOrNull() ?: 1f)
                controls.closeDrawer()
            }
        }
    }

    Box(
        modifier = modifier.playerOverlayControls(
            controlsVisible = controls.isVisible,
            onRevealControls = controls::reveal,
            onMoveRight = {
                controls.reveal()
                // The rail is on screen by the time the overlay is up, so the request lands on a
                // row that is really there. When the overlay was hidden, the request falls on
                // nothing and the overlay coming up moves the focus anyway.
                railFocusRequesters.getOrNull(railIndex)?.requestFocus()
            }
        )
    ) {
        videoSurface()

        // A transcode explains itself while the user waits for it: centred on the same black
        // surface as the spinner, in place until the video starts. The first frame brings
        // playbackStatus, which takes the notice down. It fades under its own steam for the
        // same reason the bar does.
        val noticeVisibility by animateFloatAsState(
            targetValue = if (transcodeNotice != null && playbackStatus == null) 1f else 0f,
            animationSpec = tween(CONTROLS_FADE_MILLIS),
            label = "transcodeNotice"
        )
        if (noticeVisibility > 0f && transcodeNotice != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .graphicsLayer { alpha = noticeVisibility }
            ) {
                TranscodeNoticeContent(notice = transcodeNotice)
            }
        }
        // The bar fades and slides in and out under its own steam, rather than through an
        // AnimatedVisibility. The exit of one does not always finish: the bar would be left on
        // screen with nothing in it focused, which is worse than no bar at all. The drawer is
        // slid the same way.
        if (barVisibility > 0f) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .graphicsLayer { alpha = barVisibility }
                    .offset { IntOffset(0, ((1f - barVisibility) * barHeightPx).roundToInt()) }
            ) {
                PlaybackProgressOverlay(
                    currentPosition = currentPosition,
                    duration = duration,
                    metadata = metadata,
                    rail = {
                        PlayerActionRail(
                            actions = railActions,
                            focusRequesters = railFocusRequesters,
                            onSelect = onRailSelect,
                            onFocused = { action -> lastRailKey = action.key },
                            onUserActivity = controls::reveal,
                            onBack = onBackPress
                        )
                    }
                )
            }
        }

        // The drawer comes in over the bar rather than taking its place: the panel is slid in
        // from the right and slid back out again, so that it can be seen to come and go. It is
        // kept in the tree until the slide out has finished, which is what lets it be seen
        // leaving.
        if (drawerOnScreen) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset { IntOffset((drawerWidthPx * drawerSlide).roundToInt(), 0) }
            ) {
                val content = shownContent
                if (content != null) {
                    PlayerDrawer(
                        title = content.title,
                        items = content.items,
                        focusRequesters = drawerFocusRequesters,
                        landingIndex = content.landingIndex,
                        onSelect = onDrawerSelect,
                        onBack = onBackPress,
                        onUserActivity = controls::reveal
                    )
                }
            }
        }
    }
}

/**
 * The pages the side drawer can show.
 *
 * [Audio], [Subtitles] and [More] are the pages the rail opens; the other two are reached from
 * inside [More], one level down.
 */
internal enum class DrawerPage { Audio, Subtitles, More, Speed, StreamInfo }

/**
 * What a page of the drawer holds: its title, its rows, and the row to open on.
 */
private data class DrawerContent(
    val title: String,
    val items: List<DrawerItem>,
    val landingIndex: Int
)

/**
 * The rows of the drawer for the page it is on.
 *
 * The page the drawer lands on is the one the rail button named, and the row it opens on is the
 * one that is in use, so the drawer shows where playback currently stands rather than starting
 * the user at the top of the list.
 */
@Composable
private fun drawerContentFor(
    page: DrawerPage?,
    availableAudio: List<PlexAudioStream>,
    selectedAudioId: Long?,
    availableSubtitles: List<PlexSubtitleStream>,
    selectedSubtitleId: Long?,
    playbackSpeed: Float,
    playbackStatus: PlexPlaybackStatus?,
    videoWidth: Int?,
    videoHeight: Int?
): DrawerContent? = when (page) {
    null -> null

    DrawerPage.Audio -> {
        val choices = audioChoices(availableAudio, selectedAudioId)
        DrawerContent(
            title = stringResource(R.string.plex_audio_picker),
            items = choices.map { choice ->
                DrawerItem(
                    key = choice.key,
                    label = choice.label,
                    icon = Icons.Outlined.Audiotrack,
                    trailing = if (choice.selected) DrawerTrailing.Check else DrawerTrailing.None,
                    selected = choice.selected
                )
            },
            landingIndex = choices.indexOfFirst { it.selected }.coerceAtLeast(0)
        )
    }

    DrawerPage.Subtitles -> {
        val choices = subtitleChoices(
            subtitles = availableSubtitles,
            selectedSubtitleId = selectedSubtitleId,
            offLabel = stringResource(R.string.plex_subtitles_off)
        )
        DrawerContent(
            title = stringResource(R.string.plex_subtitles_picker),
            items = choices.map { choice ->
                DrawerItem(
                    key = choice.key,
                    label = choice.label,
                    icon = Icons.Outlined.Subtitles,
                    trailing = if (choice.selected) DrawerTrailing.Check else DrawerTrailing.None,
                    selected = choice.selected
                )
            },
            landingIndex = choices.indexOfFirst { it.selected }.coerceAtLeast(0)
        )
    }

    DrawerPage.More -> DrawerContent(
        title = stringResource(R.string.plex_more_options),
        items = listOf(
            DrawerItem(
                key = DRAWER_KEY_SPEED,
                label = stringResource(R.string.plex_playback_speed),
                subLabel = formatPlaybackSpeed(playbackSpeed),
                icon = Icons.Outlined.Speed,
                trailing = DrawerTrailing.Chevron,
                expandable = true
            ),
            DrawerItem(
                key = DRAWER_KEY_STREAM_INFO,
                label = stringResource(R.string.plex_stream_info),
                subLabel = streamModeSummary(playbackStatus),
                icon = Icons.Outlined.HighQuality,
                trailing = DrawerTrailing.Chevron,
                expandable = true
            )
        ),
        landingIndex = 0
    )

    DrawerPage.Speed -> {
        val speeds = PLAYBACK_SPEEDS
        DrawerContent(
            title = stringResource(R.string.plex_playback_speed),
            items = speeds.map { speed ->
                DrawerItem(
                    key = "$DRAWER_KEY_PREFIX_SPEED-$speed",
                    label = formatPlaybackSpeed(speed),
                    icon = Icons.Outlined.Speed,
                    trailing = if (speed == playbackSpeed) DrawerTrailing.Check else DrawerTrailing.None,
                    selected = speed == playbackSpeed
                )
            },
            landingIndex = speeds.indexOf(playbackSpeed).coerceAtLeast(0)
        )
    }

    DrawerPage.StreamInfo -> DrawerContent(
        title = stringResource(R.string.plex_stream_info),
        items = listOf(
            DrawerItem(
                key = "$DRAWER_KEY_STREAM_INFO-video",
                label = stringResource(R.string.plex_stream_video),
                subLabel = streamSegment(playbackStatus?.video),
                icon = Icons.Outlined.HighQuality,
                informational = true
            ),
            DrawerItem(
                key = "$DRAWER_KEY_STREAM_INFO-audio",
                label = stringResource(R.string.plex_stream_audio),
                subLabel = streamSegment(playbackStatus?.audio),
                icon = Icons.Outlined.Audiotrack,
                informational = true
            ),
            DrawerItem(
                key = "$DRAWER_KEY_STREAM_INFO-resolution",
                label = stringResource(R.string.plex_stream_resolution),
                subLabel = resolutionLabel(videoWidth, videoHeight),
                icon = Icons.Outlined.HighQuality,
                informational = true
            )
        ),
        landingIndex = 0
    )
}

/** "1x", "1.25x" — the rate a video is playing at, in the way people write it. */
internal fun formatPlaybackSpeed(speed: Float): String =
    if (speed % 1f == 0f) "${speed.toInt()}x" else "${speed}x"

/** "Direct · H264": what Plex did with one of the two streams. */
internal fun streamSegment(playback: PlexStreamPlayback?): String? = playback?.let {
    listOfNotNull(it.mode.displayName, it.codec?.uppercase())
        .joinToString(" · ")
        .ifBlank { null }
}

/** The one-line summary on the More page: direct for both streams, or one of them transcoded. */
internal fun streamModeSummary(playbackStatus: PlexPlaybackStatus?): String? {
    val status = playbackStatus ?: return null
    val video = status.video.mode
    val audio = status.audio.mode
    return when {
        video == PlexStreamMode.Transcoded && audio == PlexStreamMode.Transcoded -> "Transcoded"
        video == PlexStreamMode.Transcoded -> "Video Transcoded"
        audio == PlexStreamMode.Transcoded -> "Audio Transcoded"
        else -> "Direct"
    }
}

/** The size of the picture being decoded, once the player knows it. */
internal fun resolutionLabel(width: Int?, height: Int?): String? =
    if (width != null && height != null && width > 0 && height > 0) {
        "$width × $height"
    } else {
        null
    }

/**
 * Whether the progress overlay is showing, which page of the drawer is open, and what Back should
 * do about the lot.
 *
 * Back unwinds a layer at a time: an open drawer goes back one page, then a visible overlay is
 * dismissed rather than leaving playback. Once the overlay hides itself on its timeout, the
 * overlay still fades out, and a back press during that fade — and for a short grace window after
 * it finishes — is still read as "dismiss the overlay", because the user saw it on screen moments
 * earlier and meant to dismiss it, not to stop playback. Dismissing the overlay with Back leaves
 * no grace, so a second Back press leaves playback straight away.
 */
internal class PlaybackControlsState {
    var isVisible by mutableStateOf(false)
        private set

    /**
     * The pages of the drawer, oldest first. Empty when no drawer is open, so a drawer with one
     * page in it is the same as no drawer at all once that page is popped.
     */
    var drawerStack by mutableStateOf<List<DrawerPage>>(emptyList())
        private set

    /** The page the drawer is showing, or null when it is closed. */
    val activeDrawer: DrawerPage? get() = drawerStack.lastOrNull()

    /** Increments on every reveal, so the auto-hide timer restarts on a repeat press. */
    var revealCount by mutableIntStateOf(0)
        private set

    private var autoHiddenAtMillis = 0L

    fun reveal() {
        isVisible = true
        autoHiddenAtMillis = 0L
        revealCount++
    }

    /** Opens a page of the drawer, one level deeper than the one showing. */
    fun openDrawer(page: DrawerPage) {
        drawerStack = drawerStack + page
        reveal()
    }

    /**
     * Takes the drawer back one level. A drawer with a single page in it closes, which is what
     * Back does on the page the rail opened.
     */
    fun popDrawer() {
        if (drawerStack.isEmpty()) return
        drawerStack = drawerStack.dropLast(1)
        reveal()
    }

    /** Closes the drawer altogether, which is what choosing something does. */
    fun closeDrawer() {
        if (drawerStack.isEmpty()) return
        drawerStack = emptyList()
        reveal()
    }

    /** Dismissal by the user. No grace: the next Back press leaves playback. */
    fun dismiss() {
        isVisible = false
        drawerStack = emptyList()
        autoHiddenAtMillis = 0L
    }

    /** Dismissal by the timeout. The fade-out, and the grace after it, both swallow Back. */
    fun autoHide() {
        isVisible = false
        drawerStack = emptyList()
        autoHiddenAtMillis = SystemClock.elapsedRealtime()
    }

    /**
     * Consumes a Back press that should act on the overlay rather than leave playback, and reports
     * whether it did. The window runs from the start of the fade-out until [BACK_GRACE_MILLIS]
     * after it ends, and is spent once used so a following press falls through to Back.
     */
    fun consumeBackIfControlsShowing(): Boolean {
        val now = SystemClock.elapsedRealtime()
        val graceEndsAt = autoHiddenAtMillis + CONTROLS_FADE_MILLIS + BACK_GRACE_MILLIS
        val withinGrace = autoHiddenAtMillis != 0L && now in autoHiddenAtMillis..graceEndsAt
        if (withinGrace) autoHiddenAtMillis = 0L
        return withinGrace
    }

    /**
     * Back, in the order it unwinds: an open drawer goes back a page, then a visible overlay is
     * dismissed, and only then does [leavePlayback] run.
     *
     * One definition, because the press reaches this from two places. The screen's back handler
     * catches it when nothing inside the player holds focus; [catchBackPress] catches it on the
     * focused control when something does, since the focusable player surface swallows the press
     * before the handler would ever see it.
     */
    fun handleBack(leavePlayback: () -> Unit) {
        when {
            drawerStack.isNotEmpty() -> popDrawer()
            isVisible -> dismiss()
            !consumeBackIfControlsShowing() -> leavePlayback()
        }
    }
}

@Composable
internal fun rememberPlaybackControlsState() = remember { PlaybackControlsState() }

/**
 * Bottom bar over a gradient scrim: what is playing, how long is left, how far in you are, and
 * the rail of buttons along the bottom right.
 *
 * The rail is drawn by the caller and handed in as a slot, so that the bar knows how much room
 * to leave for it without having to know what is in it.
 *
 * The position is polled rather than observed because ExoPlayer exposes no position flow, and a
 * bar that only moved on a key press would read as broken while it sits on screen.
 */
@Composable
private fun PlaybackProgressOverlay(
    currentPosition: () -> Long,
    duration: () -> Long,
    metadata: PlexPlaybackMetadata,
    rail: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    var positionMillis by remember { mutableLongStateOf(currentPosition().coerceAtLeast(0L)) }
    var durationMillis by remember { mutableLongStateOf(duration()) }
    LaunchedEffect(Unit) {
        while (isActive) {
            positionMillis = currentPosition().coerceAtLeast(0L)
            durationMillis = duration()
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
            .padding(
                start = OVERLAY_HORIZONTAL_PADDING,
                end = OVERLAY_HORIZONTAL_PADDING,
                top = OVERLAY_VERTICAL_PADDING,
                bottom = OVERLAY_VERTICAL_PADDING
            ),
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
            Spacer(modifier = Modifier.height(OVERLAY_ACTIONS_GAP))
            // The buttons sit at the bottom right, on the same line as YouTube's action pill.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                rail()
            }
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
        key = "$DRAWER_KEY_PREFIX_AUDIO-${stream.id}",
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
            key = "$DRAWER_KEY_PREFIX_SUBTITLES-off",
            label = offLabel,
            selected = selectedSubtitleId == null,
            streamId = null
        )
    )
    subtitles.forEach { stream ->
        add(
            TrackChoice(
                key = "$DRAWER_KEY_PREFIX_SUBTITLES-${stream.id}",
                label = stream.label,
                selected = stream.id == selectedSubtitleId,
                streamId = stream.id
            )
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
private const val CONTROLS_AUTO_HIDE_MILLIS = 5_000L

/** Room for the scrim to fade out over the video rather than cut a hard edge. It only has to hold
 * the bottom bar inside its vertical padding — the names, the times, the progress bar and the
 * row of buttons under it. The drawer sits over the video, not on the scrim, so it is not counted
 * here. */
private val OVERLAY_SCRIM_HEIGHT = 356.dp
private val OVERLAY_HORIZONTAL_PADDING = 48.dp
private val OVERLAY_VERTICAL_PADDING = 48.dp
private val OVERLAY_TITLE_GAP = 12.dp
private val OVERLAY_PROGRESS_GAP = 10.dp
private val OVERLAY_PROGRESS_HEIGHT = 6.dp
private val OVERLAY_PROGRESS_RADIUS = 3.dp
private val OVERLAY_PROGRESS_SHAPE = RoundedCornerShape(OVERLAY_PROGRESS_RADIUS)

/** Gap between the progress bar and the row of buttons under it. */
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

/** The keys of the rail's buttons, which are also the keys of the pages they open. */
private const val RAIL_ACTION_AUDIO = "rail-audio"
private const val RAIL_ACTION_SUBTITLES = "rail-subtitles"
private const val RAIL_ACTION_MORE = "rail-more"

/** How many buttons the rail can hold: audio, subtitles and more. */
private const val MAX_RAIL_ACTIONS = 3

/** How many rows a page of the drawer can hold before the list needs more requesters than this. */
private const val MAX_DRAWER_ROWS = 32

/**
 * The keys of the drawer's own rows, and the prefixes the track rows are built from.
 *
 * The prefixes carry no dash of their own: the dash that separates the prefix from the stream id
 * belongs to the key, so that a key reads the same however it is put together.
 */
private const val DRAWER_KEY_SPEED = "drawer-speed"
private const val DRAWER_KEY_STREAM_INFO = "drawer-stream-info"
private const val DRAWER_KEY_PREFIX_AUDIO = "audio"
private const val DRAWER_KEY_PREFIX_SUBTITLES = "subtitle"
private const val DRAWER_KEY_PREFIX_SPEED = "speed"

/** The rates a video can be played at. */
private val PLAYBACK_SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
