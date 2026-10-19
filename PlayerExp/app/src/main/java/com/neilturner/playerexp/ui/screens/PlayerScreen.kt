package com.neilturner.playerexp.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.ui.SubtitleView
import androidx.media3.ui.compose.material3.Player
import androidx.media3.common.util.UnstableApi
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.neilturner.playerexp.ui.viewmodels.PlayerViewModel

@UnstableApi
@OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
@Composable
fun PlayerScreen(
    mediaId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = viewModel()
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    BackHandler {
        viewModel.releasePlayer()
        onBack()
    }

    LaunchedEffect(mediaId) {
        viewModel.initialize(mediaId)
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

    val player = viewModel.player
    if (player != null) {
        Box(modifier = modifier.fillMaxSize()) {
            Player(
                player = player,
                modifier = Modifier.fillMaxSize(),
                showControls = false,
                shutter = {
                    Box(
                        modifier = Modifier.fillMaxSize().background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
            // The subtitles are drawn in a view of their own: the ordinary ones are drawn by the
            // view itself, and the styled ASS ones are drawn by libass in a child of it.
            AndroidView(
                factory = { context ->
                    SubtitleView(context).apply {
                        setUserDefaultStyle()
                        setUserDefaultTextSize()
                        viewModel.assSupport.attachTo(this)
                    }
                },
                update = { subtitleView ->
                    subtitleView.setCues(player.currentCues.cues)
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    } else {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Loading media...",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
