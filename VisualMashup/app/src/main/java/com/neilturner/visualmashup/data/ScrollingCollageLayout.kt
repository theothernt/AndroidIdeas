package com.neilturner.visualmashup.data

/**
 * Layout metrics shared by the scrolling collage renderer and its preloader, so both request the
 * exact pixel size each tile is drawn at.
 */
object ScrollingCollageLayout {
    const val COLUMNS = 3

    /** Tiles are cropped to fill this aspect range instead of following every extreme panorama. */
    const val MIN_TILE_ASPECT = 0.65f
    const val MAX_TILE_ASPECT = 1.5f

    fun tileAspect(image: SourceImage): Float =
        (image.height.toFloat() / image.width).coerceIn(MIN_TILE_ASPECT, MAX_TILE_ASPECT)
}