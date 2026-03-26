package com.neilturner.channelui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import com.neilturner.channelui.ui.screens.MainScreen
import com.neilturner.channelui.ui.theme.ChannelUITheme

class MainActivity : ComponentActivity() {
    private var isPlaying by mutableStateOf(true)

    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ChannelUITheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RectangleShape
                ) {
                    MainScreen(playWhenReady = isPlaying)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        isPlaying = true
    }

    override fun onStop() {
        super.onStop()
        isPlaying = false
    }
}