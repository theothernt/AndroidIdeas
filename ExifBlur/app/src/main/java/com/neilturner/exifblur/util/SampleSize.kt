package com.neilturner.exifblur.util

/**
 * Largest power-of-two subsample that still leaves the bitmap large enough to fill
 * the target box.
 *
 * The axis that overflows most relative to its own target drives the halving.
 * Comparing the largest source dimension against the largest target instead
 * over-samples extreme aspect ratios: a 1920x10000 image against a 1920x1080
 * target gets halved four times (480x2500) when eight (240x1250) is the factor
 * that actually brings it within the box.
 *
 * Shared by [BitmapHelper] and [BitmapHelperV28] so both decode paths agree.
 * Power-of-two is required because BitmapFactory's inSampleSize rounds a JPEG
 * down to the nearest power of two anyway, so a finer factor would not survive.
 */
internal fun calculateSampleSize(width: Int, height: Int, reqWidth: Int, reqHeight: Int): Int {
    val reqW = reqWidth.coerceAtLeast(1)
    val reqH = reqHeight.coerceAtLeast(1)

    if (height <= reqH && width <= reqW) return 1

    val halfHeight = height / 2
    val halfWidth = width / 2

    // Cross-multiply rather than dividing, so the axis comparison keeps its
    // precision when either ratio would truncate to zero.
    return if (halfHeight.toLong() * reqW >= halfWidth.toLong() * reqH) {
        var sampleSize = 2
        while (halfHeight / sampleSize >= reqH) sampleSize *= 2
        sampleSize
    } else {
        var sampleSize = 2
        while (halfWidth / sampleSize >= reqW) sampleSize *= 2
        sampleSize
    }
}