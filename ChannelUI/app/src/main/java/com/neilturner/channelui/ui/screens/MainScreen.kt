package com.neilturner.channelui.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.neilturner.channelui.data.dummyChannels
import com.neilturner.channelui.ui.components.ChannelItem
import com.neilturner.channelui.ui.components.VideoPlayer

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun MainScreen() {
    val channels = dummyChannels
    // Use the first channel's stream or a default one
    var currentStreamUrl by remember { mutableStateOf(channels.first().streamUrl) }

    Box(modifier = Modifier.fillMaxSize()) {
        // Video Background
        VideoPlayer(
            url = currentStreamUrl,
            modifier = Modifier.fillMaxSize()
        )

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
								        currentStreamUrl = channel.streamUrl
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