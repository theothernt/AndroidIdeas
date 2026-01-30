package com.neilturner.channelui.data

import androidx.compose.ui.graphics.Color

interface ChannelProvider {
    suspend fun getChannels(): List<Channel>
}

class ChannelProviderImpl : ChannelProvider {
    override suspend fun getChannels(): List<Channel> {
        return buildList {
            // First channel is NHK World
            add(
                Channel(
                    id = "nhk_world",
                    name = "NHK World",
                    color = Color.DarkGray, // Or a specific brand color
                    streamUrl = "https://media-tyo.hls.nhkworld.jp/hls/w/live/master.m3u8"
                )
            )
            // Existing dummy channels
            addAll(
                List(20) { index ->
                    Channel(
                        id = index.toString(),
                        name = "Channel ${index + 1}",
                        color = Color(
                            red = (0..255).random(),
                            green = (0..255).random(),
                            blue = (0..255).random()
                        ),
                        // Keep the test stream for others or random ones
                        streamUrl = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
                    )
                }
            )
        }
    }
}
