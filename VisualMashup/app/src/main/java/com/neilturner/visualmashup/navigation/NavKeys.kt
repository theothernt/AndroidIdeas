package com.neilturner.visualmashup.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Root screen: the app's main menu. */
@Serializable
data object MenuScreen : NavKey

/** The collage editor. Currently a blank destination. */
@Serializable
data object CollageScreen : NavKey

/** Crop-free collage preview using natural-aspect layouts. */
@Serializable
data object CollagePreviewScreen : NavKey