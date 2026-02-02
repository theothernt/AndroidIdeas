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

	        add(
		        Channel(
			        id = "bbc-two",
			        name = "BBC Two",
			        color = Color.DarkGray, // Or a specific brand color
			        streamUrl = "https://viamotionhsi.netplus.ch/live/eds/bbc2/browser-HLS8/bbc2.m3u8"
		        )
	        )

	        add(
		        Channel(
			        id = "bbc-three",
			        name = "BBC Three",
			        color = Color.DarkGray, // Or a specific brand color
			        streamUrl = "https://streamer.nexyl.uk/39290a19-b8dd-43ea-b8dc-081c37790f24.m3u8"
		        )
	        )

	        add(
		        Channel(
			        id = "channel-4",
			        name = "Channel 4",
			        color = Color.DarkGray, // Or a specific brand color
			        streamUrl = "https://viamotionhsi.netplus.ch/live/eds/channel4/browser-HLS8/channel4.m3u8"
		        )
	        )

	        add(
		        Channel(
			        id = "channel-5",
			        name = "Channel 5",
			        color = Color.DarkGray, // Or a specific brand color
			        streamUrl = "https://viamotionhsi.netplus.ch/live/eds/channel5/browser-HLS8/channel5.m3u8"
		        )
	        )


            // Existing dummy channels
            addAll(
                List(10) { index ->
                    Channel(
                        id = index.toString(),
                        name = "Channel ${index + 1}",
                        color = Color(
                            red = (0..255).random(),
                            green = (0..255).random(),
                            blue = (0..255).random()
                        ),
                        // Keep the test stream for others or random ones
                        streamUrl = "https://viamotionhsi.netplus.ch/live/eds/film4/browser-HLS8/film4.m3u8"
                    )
                }
            )
        }
    }
}
