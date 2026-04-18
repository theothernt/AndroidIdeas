package com.neilturner.videothumbnails.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VideoResponse(
    val assets: List<Video>,
    val version: Int,
    val initialAssetCount: Int = 0,
)

@Serializable
data class Video(
    val id: String,
    val title: String = "",
    @SerialName("accessibilityLabel")
    val accessibilityLabel: String,
    @SerialName("timeOfDay")
    val timeOfDay: String,
    val scene: String,
    @SerialName("url-1080-H264")
    val url1080H264: String? = null,
    @SerialName("url-1080-HDR")
    val url1080HDR: String? = null,
    @SerialName("url-1080-SDR")
    val url1080SDR: String? = null,
    @SerialName("url-4K-HDR")
    val url4KHDR: String? = null,
    @SerialName("url-4K-SDR")
    val url4KSDR: String? = null,
    @SerialName("pointsOfInterest")
    val pointsOfInterest: Map<String, String>? = null,
) {
    // Get the preferred video URL: H264 first, then H265/HEVC as fallback
    fun getPreferredVideoUrl(): String =
        // Prefer H264 (most compatible)
        url1080H264
            ?: url1080HDR
            ?: url1080SDR
            // If no 1080p formats, try 4K formats
            ?: url4KHDR
            ?: url4KSDR
            ?: throw IllegalStateException("No valid video URL found for video: $id")

    // Get display title (use accessibilityLabel if title is empty)
    fun getDisplayTitle(): String = if (title.isBlank()) accessibilityLabel else title
}
