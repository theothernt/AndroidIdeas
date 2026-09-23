package com.neilturner.playerexp.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import android.widget.Toast
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import android.util.Log
import com.neilturner.playerexp.ui.modifiers.playerDpadControls
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.ui.compose.material3.Player
import androidx.media3.common.util.UnstableApi
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.neilturner.playerexp.R
import com.neilturner.playerexp.data.plex.PlexPlaybackStatus
import com.neilturner.playerexp.data.plex.PlexStreamPlayback
import com.neilturner.playerexp.ui.viewmodels.PlexPlayerUiState
import com.neilturner.playerexp.ui.viewmodels.PlexPlayerViewModel
import kotlinx.coroutines.delay

@UnstableApi
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PlexPlayerScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlexPlayerViewModel = viewModel()
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    BackHandler {
        viewModel.releasePlayer()
        onBack()
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
            val context = LocalContext.current
            val focusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) { focusRequester.requestFocus() }
            if (player != null) {
                PlaybackContent(
                    player = player,
                    playbackStatus = state.playbackStatus,
                    modifier = modifier
                        .fillMaxSize()
                        .focusRequester(focusRequester)
                        .playerDpadControls(
                            onSeekBackward = {
                                val target = maxOf(0L, player.currentPosition - 10_000L)
                                Log.d("PlexPlayerScreen", "Seeking -10s from ${player.currentPosition} to $target")
                                player.seekTo(target)
                                Toast.makeText(context, "Seek -10s", Toast.LENGTH_SHORT).show()
                            },
                            onSeekForward = {
                                val duration = player.duration
                                val target = player.currentPosition + 30_000L
                                val newPosition = if (duration != androidx.media3.common.C.TIME_UNSET && duration > 0L) {
                                    minOf(duration, target)
                                } else {
                                    target
                                }
                                Log.d("PlexPlayerScreen", "Seeking +30s from ${player.currentPosition} to $newPosition")
                                player.seekTo(newPosition)
                                Toast.makeText(context, "Seek +30s", Toast.LENGTH_SHORT).show()
                            },
                            onTogglePlayPause = {
                                if (player.isPlaying) {
                                    Log.d("PlexPlayerScreen", "Pausing playback")
                                    viewModel.pause()
                                } else {
                                    Log.d("PlexPlayerScreen", "Resuming playback")
                                    viewModel.resume()
                                }
                            }
                        )
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

    Box(modifier = modifier) {
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
                .padding(bottom = 48.dp),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            ConnectionStatusOverlay(playbackStatus = requireNotNull(playbackStatus))
        }
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
