package com.neilturner.channelui.data

import androidx.compose.ui.graphics.Color

interface ChannelProvider {
    suspend fun getChannels(): List<Channel>
}

class ChannelProviderImpl : ChannelProvider {
    override suspend fun getChannels(): List<Channel> {
        return buildList {
            // NHK World (original working URL)
            add(
                Channel(
                    id = "nhk_world",
                    name = "NHK World",
                    color = Color(0xFFE60012.toInt()),
                    streamUrl = "https://media-tyo.hls.nhkworld.jp/hls/w/live/master.m3u8"
                )
            )

            // BBC Two (was working in original)
            add(
                Channel(
                    id = "bbc_two",
                    name = "BBC Two",
                    color = Color(0xFF0066CC.toInt()),
                    streamUrl = "https://viamotionhsi.netplus.ch/live/eds/bbc2/browser-HLS8/bbc2.m3u8"
                )
            )

            // BBC Three (was working in original)
            add(
                Channel(
                    id = "bbc_three",
                    name = "BBC Three",
                    color = Color(0xFFE60012.toInt()),
                    streamUrl = "https://streamer.nexyl.uk/39290a19-b8dd-43ea-b8dc-081c37790f24.m3u8"
                )
            )

            // Channel 4 (was working in original)
            add(
                Channel(
                    id = "channel_4",
                    name = "Channel 4",
                    color = Color(0xFF009FE3.toInt()),
                    streamUrl = "https://viamotionhsi.netplus.ch/live/eds/channel4/browser-HLS8/channel4.m3u8"
                )
            )

            // Channel 5 (was working in original)
            add(
                Channel(
                    id = "channel_5",
                    name = "Channel 5",
                    color = Color(0xFFE60012.toInt()),
                    streamUrl = "https://viamotionhsi.netplus.ch/live/eds/channel5/browser-HLS8/channel5.m3u8"
                )
            )

            // GB News
            add(
                Channel(
                    id = "gb_news",
                    name = "GB News",
                    color = Color(0xFF000000.toInt()),
                    streamUrl = "https://live-gbnews.simplestreamcdn.com/live5/gbnews/bitrate1.isml/manifest.m3u8"
                )
            )

            // TalkTV
            add(
                Channel(
                    id = "talk_tv",
                    name = "TalkTV",
                    color = Color(0xFFE60012.toInt()),
                    streamUrl = "https://488f4ce4.wurl.com/master/f36d25e7e52f1ba8d7e56eb859c636563214f541/TEctZ2JfVGFsa19ITFM/playlist.m3u8"
                )
            )

            // Now 80s
            add(
                Channel(
                    id = "now_80s",
                    name = "Now 80s",
                    color = Color(0xFF8B0000.toInt()),
                    streamUrl = "https://lightning-now80s-samsunguk.amagi.tv/playlist.m3u8"
                )
            )

            // Bloomberg TV
            add(
                Channel(
                    id = "bloomberg",
                    name = "Bloomberg TV",
                    color = Color(0xFF000000.toInt()),
                    streamUrl = "https://bloomberg.com/media-manifest/streams/eu.m3u8"
                )
            )

            // TG4 (Irish language)
            add(
                Channel(
                    id = "tg4",
                    name = "TG4",
                    color = Color(0xFF006600.toInt()),
                    streamUrl = "https://fastly.live.brightcove.com/6384196213112/eu-west-1/1555966122001/eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJob3N0IjoieDl2YzlnLmVncmVzcy55ODN1ZWIiLCJhY2NvdW50X2lkIjoiMTU1NTk2NjEyMjAwMSIsImVobiI6ImZhc3RseS5saXZlLmJyaWdodGNvdmUuY29tIiwiaXNzIjoiYmxpdmUtcGxheWJhY2stc291cmNlLWFwaSIsInN1YiI6InBhdGhtYXB0b2tlbiIsImF1ZCI6WyIxNTU1OTY2MTIyMDAxIl0sImp0aSI6IjYzODQxOTYyMTMxMTIifQ.Co2xR3kmJBoxTaFTpwU0b37lJ05vNt-y32-SwfM26EA/chunklist.m3u8"
                )
            )

            // Oireachtas TV (Irish Parliament)
            add(
                Channel(
                    id = "oireachtas_tv",
                    name = "Oireachtas TV",
                    color = Color(0xFF003399.toInt()),
                    streamUrl = "https://d33zah5htxvoxb.cloudfront.net/el/live/oirtv/hls.m3u8"
                )
            )

            // Sky News
            add(
                Channel(
                    id = "sky_news",
                    name = "Sky News",
                    color = Color(0xFF0052A5.toInt()),
                    streamUrl = "https://linear021-gb-hls1-prd-ak.cdn.skycdp.com/Content/HLS_001_hd/Live/channel(skynews)/index_mob.m3u8"
                )
            )
        }
    }
}