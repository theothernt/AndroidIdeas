package com.neilturner.playerexp.data

data class MediaSource(
    val id: String,
    val title: String,
    val uri: String,
    val mimeType: String? = null
)

class MediaProvider {
    companion object {
        val sources = listOf(
            MediaSource(
                id = "progressive",
                title = "Big Buck Bunny",
                uri = "https://test-videos.co.uk/vids/bigbuckbunny/mp4/h264/720/Big_Buck_Bunny_720_10s_1MB.mp4"
            ),
            MediaSource(
                id = "hls",
                title = "HLS Stream",
                uri = "https://distro001-gb-hls1-prd.delivery.skycdp.com/easel_cdn/ngrp:weather_loop.stream_all/playlist.m3u8",
                mimeType = "application/x-mpegURL"
            )
        )

        fun getSource(id: String): MediaSource? = sources.find { it.id == id }
    }
}