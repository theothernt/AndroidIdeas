package com.neilturner.dreamcompose.dream

/**
 * A video the screensaver can play.
 *
 * @param id the id of the entry in the Aerial Community list
 * @param title the name of the place the video was filmed at
 * @param location the country or region the place is in
 * @param url where the video is downloaded from
 */
data class DreamVideo(
	val id: String,
	val title: String,
	val location: String,
	val url: String,
)

/**
 * The base of the download links of the 1080p H.264 videos of the Aerial Community list
 * (https://github.com/AerialScreensaver/AerialCommunity, community-2020). They play on the
 * emulator without a hardware decoder.
 */
private const val AERIAL_1080P_H264 =
	"https://github.com/glouel/AerialCommunity/releases/download/mw2-1080p-h264/"

/**
 * The videos the screensaver plays, in the order they are played.
 */
val DREAM_VIDEOS =
	listOf(
		DreamVideo(
			id = "video_inspire_florida_miami_brickell_sunset_00036",
			title = "Miami",
			location = "Florida",
			url = AERIAL_1080P_H264 + "video_inspire_florida_miami_brickell_sunset_00036.1080-h264.mov",
		),
		DreamVideo(
			id = "video_inspire_nevada_lasvegas_northstrip_021",
			title = "Las Vegas",
			location = "Nevada",
			url = AERIAL_1080P_H264 + "video_inspire_nevada_lasvegas_northstrip_021.1080-h264.mov",
		),
		DreamVideo(
			id = "video_inspire_oregon_lone-ranch-beach_00015",
			title = "Lone Ranch Beach",
			location = "Oregon",
			url = AERIAL_1080P_H264 + "video_inspire_oregon_lone-ranch-beach_00015.1080-h264.mov",
		),
		DreamVideo(
			id = "video_inspire_california_vineyard_sierra-mar_sunrise_110",
			title = "Sierra Mar",
			location = "California",
			url = AERIAL_1080P_H264 + "video_inspire_california_vineyard_sierra-mar_sunrise_110.1080-h264.mov",
		),
		DreamVideo(
			id = "video_inspire_italy_como_cerano-dintelvi_006",
			title = "Como Cerano d'Intelvi",
			location = "Italy",
			url = AERIAL_1080P_H264 + "video_inspire_italy_como_cerano-dintelvi_006.1080-h264.mov",
		),
		DreamVideo(
			id = "video_inspire_oregon_portland_00064",
			title = "Portland",
			location = "Oregon",
			url = AERIAL_1080P_H264 + "video_inspire_oregon_portland_00064.1080-h264.mov",
		),
		DreamVideo(
			id = "video_inspire_california_big-sur_2020_00001",
			title = "Big Sur",
			location = "California",
			url = AERIAL_1080P_H264 + "video_inspire_california_big-sur_2020_00001.1080-h264.mov",
		),
		DreamVideo(
			id = "video_inspire_texas_austin_oct2019_evening_00085",
			title = "Austin",
			location = "Texas",
			url = AERIAL_1080P_H264 + "video_inspire_texas_austin_oct2019_evening_00085.1080-h264.mov",
		),
	)
