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
                title = "MP4/MKV file",
                uri = "http://192.168.1.3:32400/library/parts/82147/1790059902/file.mkv?X-Plex-Token=nF39pRhsqQBvwZZjyadm"
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