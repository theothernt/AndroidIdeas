package com.neilturner.channelui.data

import androidx.compose.ui.graphics.Color

data class Channel(
    val id: String,
    val name: String,
    val color: Color,
    val streamUrl: String
)

val dummyChannels = List(20) { index ->
    Channel(
        id = index.toString(),
        name = "Channel ${index + 1}",
        color = Color(
            red = (0..255).random(),
            green = (0..255).random(),
            blue = (0..255).random()
        ),
        streamUrl = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
    )
}
