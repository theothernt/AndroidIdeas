package com.neilturner.overlayparty.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.neilturner.overlayparty.ui.components.LocationOverlay
import com.neilturner.overlayparty.ui.components.MusicOverlay
import com.neilturner.overlayparty.ui.components.TimeOverlay
import com.neilturner.overlayparty.ui.components.WeatherOverlay
import org.koin.androidx.compose.koinViewModel

@Composable
fun MainScreen(viewModel: MainViewModel = koinViewModel()) {
    val weatherText by viewModel.weatherText.collectAsState()
    val timeDateText by viewModel.timeDateText.collectAsState()
    val nowPlayingText by viewModel.nowPlayingText.collectAsState()
    val locationText by viewModel.locationText.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.DarkGray)
    ) {
        Slideshow()

        WeatherOverlay(
            text = weatherText,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        )
        TimeOverlay(
            text = timeDateText,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        )

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            MusicOverlay(
                text = nowPlayingText,
                modifier = Modifier.padding(end = 16.dp)
            )
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.BottomEnd
            ) {
                LocationOverlay(
                    text = locationText
                )
            }
        }
    }
}

