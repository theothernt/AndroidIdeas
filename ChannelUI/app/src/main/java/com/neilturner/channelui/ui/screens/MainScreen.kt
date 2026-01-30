package com.neilturner.channelui.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.neilturner.channelui.ui.components.ChannelItem
import com.neilturner.channelui.ui.components.VideoPlayer
import com.neilturner.channelui.ui.viewmodel.ChannelViewModel
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: ChannelViewModel = koinViewModel()
) {
    val channels by viewModel.channels.collectAsState()
    val currentStreamUrl by viewModel.currentStreamUrl.collectAsState()

    if (channels.isEmpty()) return

    Box(modifier = Modifier.fillMaxSize()) {
        // Video Background
        // Handle potential null URL or empty string
        currentStreamUrl?.let { url ->
            VideoPlayer(
                url = url,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Gradient Overlay for readability
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.5f),
                            Color.Black.copy(alpha = 0.9f)
                        ),
                        startY = 300f
                    )
                )
        )

        // Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Bottom
        ) {

            val scrollState = rememberScrollState()

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(32.dp)
            ) {
                Spacer(modifier = Modifier.height(24.dp))

                // Create rows manually
                channels.chunked(5).forEach { rowChannels ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        rowChannels.forEach { channel ->
                            Box(modifier = Modifier.weight(1f)) {
                                ChannelItem(
                                    channel = channel,
                                    onClick = {
                                        viewModel.playChannel(channel)
                                    }
                                )
                            }
                        }
                        // Fill remaining slots
                        repeat(5 - rowChannels.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}