package com.neilturner.visualmashup.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Root screen: the app's main menu. */
@Serializable
data object MenuScreen : NavKey

/** Simple collage grid built from full-bleed, orientation-aware cells. */
@Serializable
data object SimpleCollageScreen : NavKey

/** Endless vertical collage that auto-scrolls through the images. */
@Serializable
data object ScrollingCollageScreen : NavKey

/** Slideshow that applies image effects such as Ken Burns. */
@Serializable
data object EffectScreen : NavKey