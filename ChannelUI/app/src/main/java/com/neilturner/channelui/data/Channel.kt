package com.neilturner.channelui.data

import androidx.compose.ui.graphics.Color

data class Channel(
    val id: String,
    val name: String,
    val color: Color,
    val streamUrl: String
)
