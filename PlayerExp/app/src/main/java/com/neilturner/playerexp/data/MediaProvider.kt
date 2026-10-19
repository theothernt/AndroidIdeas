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
                uri = "https://test-videos.co.uk/vids/bigbuckbunny/mp4/h264/720/Big_Buck_Bunny_720_10s_1MB.mp4"
            ),
            MediaSource(
                id = "hls",
                title = "HLS Stream",
                uri = "https://distro001-gb-hls1-prd.delivery.skycdp.com/easel_cdn/ngrp:weather_loop.stream_all/playlist.m3u8",
                mimeType = "application/x-mpegURL"
            ),
            MediaSource(
                id = "ass",
                title = "ASS styled subtitles",
                // A file with an ASS subtitle track in it, with the colours, the placement and
                // the karaoke in the subtitle. It is in the app's assets so that the subtitles can
                // be looked at without a server or a network.
                uri = "asset:///ass-test.mkv"
            )
        )

        fun getSource(id: String): MediaSource? = sources.find { it.id == id }
    }
}