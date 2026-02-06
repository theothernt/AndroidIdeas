package com.neilturner.fourlayers.data

import com.neilturner.fourlayers.model.MediaItem

/**
 * Simple provider for sample media playlist.
 */
object MediaProvider {
    
    fun getSamplePlaylist(): List<MediaItem> = listOf(
        // 1. Image -> Image Transition Test
	    MediaItem.Image(
            url = "https://picsum.photos/id/1015/1920/1080", // River
            duration = 6_000L
        ),
        MediaItem.Image(
            url = "https://picsum.photos/id/1018/1920/1080", // Mountain lake
            duration = 6_000L
        ),
        
        // 2. Image -> Video Transition Test
        MediaItem.Video(
            url = "https://github.com/glouel/AerialCommunity/releases/download/mw2-1080p-h264/video_inspire_florida_miami_brickell_sunset_00036.1080-h264.mov",
            duration = 12_000L
        ),
        
        // 3. Video -> Video Transition Test
        MediaItem.Video(
            url = "https://github.com/glouel/AerialCommunity/releases/download/mw2-1080p-h264/video_inspire_nevada_lasvegas_northstrip_021.1080-h264.mov",
            duration = 12_000L
        ),
        
        // 4. Video -> Image Transition Test
        MediaItem.Image(
            url = "https://picsum.photos/id/1035/1920/1080", // Bridge
            duration = 6_000L
        ),
	    MediaItem.Video(
		    url = "https://github.com/glouel/AerialCommunity/releases/download/mw2-1080p-h264/video_inspire_oregon_lone-ranch-beach_00015.1080-h264.mov",
		    duration = 12_000L
	    ),
	    MediaItem.Video(
		    url = "https://github.com/glouel/AerialCommunity/releases/download/mw2-1080p-h264/video_inspire_oregon_lone-ranch-beach_00001.1080-h264.mov",
		    duration = 12_000L
	    )


    )
}
