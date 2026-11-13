package com.neilturner.visualmashup.ui.effects

/** The transition used between images on the Effects screen. */
enum class Transition(val displayName: String) {
    NONE("None"),
    CROSSFADE("Crossfade"),
    DIP_TO_BLACK("Dip to black"),
    SLIDE("Slide"),
    BLUR_CROSSFADE("Blur crossfade"),
    ZOOM("Zoom"),
    WIPE("Wipe"),
    IRIS("Iris"),
    GRADIENT_WIPE("Gradient wipe"),
    PARALLAX("Parallax slide"),
    FLIP("3D flip"),
    FOLD("Page fold"),
    PIXELATE("Pixelate"),
    FEATHERED_WIPE("Feathered wipe"),
}