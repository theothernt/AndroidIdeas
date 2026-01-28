package com.neilturner.videothumbnails.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VideoResponse(
    val assets: List<Video>,
    val version: Int,
    val initialAssetCount: Int
)

@Serializable
data class Video(
    val title: String,
    @SerialName("url-1080-H264")
    val url1080H264: String
)
