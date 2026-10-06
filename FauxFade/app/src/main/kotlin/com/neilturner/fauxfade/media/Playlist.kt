package com.neilturner.fauxfade.media

import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi

/**
 * One clip, and how to hand it to a player.
 *
 * No mime type is declared on purpose. The clips are a mix of QuickTime and MP4 containers, and
 * stating the wrong one is worse than stating none - the player is better at identifying the
 * container from the bytes than from a label attached to a URL.
 */
@OptIn(UnstableApi::class)
data class VideoItem(
	val title: String,
	val uri: String,
) {
	fun toMediaItem(): MediaItem =
		MediaItem
			.Builder()
			.setUri(uri)
			.setMediaId(title)
			.build()
}

/**
 * The clips to loop between, in order.
 *
 * A fixed list rather than something fetched, because nothing here varies at runtime. The only
 * thing that changes is the order they are played in, which [shuffled] chooses per launch.
 */
@Suppress("ktlint:standard:max-line-length")
object Playlist {
	/**
	 * The clips in a random order, re-chosen on every call.
	 *
	 * The caller is expected to keep the result for the whole run: the loop walks one list, so
	 * asking again partway through would swap clips out from under it.
	 */
	fun shuffled(): List<VideoItem> = items.shuffled()

	/**
	 * The clip URLs are Apple CDN asset paths with no shorter form and nowhere to break them
	 * across, so they are exempt from the line length rule rather than the limit being raised
	 * for the whole project.
	 */
	val items: List<VideoItem> =
		listOf(
			VideoItem(
				title = "Red Canyon",
				uri = "http://sylvan.apple.com/itunes-assets/Aerials116/v4/e6/1a/ca/e61acac6-1c10-41a6-b796-7dc03fbc4517/M010_C009_F01_2K_AVC.mov",
			),
			VideoItem(
				title = "Tea Terraces",
				uri = "http://sylvan.apple.com/itunes-assets/Aerials116/v4/e6/1a/ca/e61acac6-1c10-41a6-b796-7dc03fbc4517/ann0040_flamecomp_v0006.00789786__SDR_2K_AVC.mov",
			),
			VideoItem(
				title = "Outrigger Beach",
				uri = "http://sylvan.apple.com/itunes-assets/Aerials116/v4/e6/1a/ca/e61acac6-1c10-41a6-b796-7dc03fbc4517/ann0060_flamecomp_v0003.00815886__SDR_2K_AVC.mov",
			),
			VideoItem(
				title = "Alabama",
				uri = "https://github.com/glouel/AerialCommunity/releases/download/mw2-1080p-h264/video_inspire_alabama_montgomery_countryside_00007.1080-h264.mov",
			),
			VideoItem(
				title = "Reine",
				uri = "https://github.com/RobinFrcd/AerialShots/releases/download/norway-2021-v1/robinfrcd_reine_1080p_H264.m4v",
			),
			VideoItem(
				title = "Los Angeles",
				uri = "http://sylvan.apple.com/itunes-assets/Aerials116/v4/97/e9/06/97e90616-1227-c670-6118-bd5ae991b43b/comp_LA_A011_C003_DGRN_LNFIX_STAB_v57_SDR_PS_20181002_SDR_2K_AVC.mov",
			),
		)
}
