package com.neilturner.playerexp.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import com.neilturner.playerexp.ui.viewmodels.PlayerViewModel

@OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)
@androidx.media3.common.util.UnstableApi
@Composable
fun PlayerScreen(
    mediaType: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val mediaUri = when (mediaType) {
        "hls" -> "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
        else -> "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
    }

    val title = when (mediaType) {
        "hls" -> "HLS Stream"
        else -> "Big Buck Bunny"
    }

    LaunchedEffect(mediaUri) {
        viewModel.initialize(mediaUri, title)
    }

    DisposableEffect(lifecycleOwner.lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                viewModel.player.playWhenReady = false
            } else if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.player.playWhenReady = true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.player.playWhenReady = false
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        androidx.tv.material3.Surface(
            tonalElevation = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Button(onClick = onBack) {
                    Text("Back")
                }
                Spacer(modifier = Modifier.height(16.dp))
                androidx.compose.material3.Text(
                    text = title,
                    style = androidx.compose.material3.MaterialTheme.typography.titleLarge
                )
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            AndroidPlayerView(
                context = context,
                player = viewModel.player,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@androidx.media3.common.util.UnstableApi
@Composable
private fun AndroidPlayerView(
    context: Context,
    player: androidx.media3.common.Player,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                useController = true
                controllerAutoShow = true
                controllerShowTimeoutMs = 3000
            }
        },
        modifier = modifier
    )
}
