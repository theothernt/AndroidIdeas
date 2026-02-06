package com.neilturner.fourlayers

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import com.neilturner.fourlayers.data.MediaProvider
import com.neilturner.fourlayers.player.MediaPlaylistPlayer
import com.neilturner.fourlayers.ui.theme.FourLayersTheme
import com.neilturner.fourlayers.player.MediaPlaylistViewModel

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FourLayersTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RectangleShape
                ) {
                    MediaPlaylistScreen()
                }
            }
        }
    }
}

@Composable
fun MediaPlaylistScreen() {
    val context = LocalContext.current

    val viewModel = remember {
        MediaPlaylistViewModel(context)
    }

    LaunchedEffect(Unit) {
        val playlist = MediaProvider.getSamplePlaylist()
        viewModel.loadPlaylist(playlist)
    }

    MediaPlaylistPlayer(
        viewModel = viewModel,
        modifier = Modifier.fillMaxSize()
    )
}