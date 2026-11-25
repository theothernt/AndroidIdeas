package com.neilturner.exifblur.slideshow

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.util.Size
import android.view.WindowManager
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.bitmapConfig
import coil3.request.crossfade
import coil3.size.Precision
import coil3.size.Scale

/** Shared so the controller can delay prefetch until the fade-in has finished. */
const val SLIDESHOW_TRANSITION_MS = 2000

/** Display resolution in px, read from the real window rather than hardcoded. */
fun currentDisplaySize(context: Context): Size {
    val bounds =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.getSystemService(WindowManager::class.java)?.currentWindowMetrics?.bounds
        } else {
            null
        }
    return if (bounds != null) {
        Size(bounds.width(), bounds.height())
    } else {
        @Suppress("DEPRECATION")
        val metrics = context.resources.displayMetrics
        Size(metrics.widthPixels, metrics.heightPixels)
    }
}

/**
 * The only place requests are built. The prefetcher and the display layer both
 * call this, so Coil's memory-cache key (data + size + scale + precision +
 * transformation signatures) is identical and the hardware-bitmap cache
 * compatibility check passes on both sides.
 */
fun buildSlideshowRequest(
    context: Context,
    model: Any,
    targetSize: Size,
): ImageRequest =
    ImageRequest
        .Builder(context)
        .data(model)
        .size(targetSize.width, targetSize.height)
        .precision(Precision.INEXACT)
        .scale(Scale.FIT)
        .memoryCachePolicy(CachePolicy.ENABLED)
        .diskCachePolicy(
            if (CacheConfig.diskCacheEnabled) CachePolicy.ENABLED else CachePolicy.DISABLED,
        )
        .networkCachePolicy(CachePolicy.ENABLED)
        .allowHardware(true)
        .bitmapConfig(Bitmap.Config.HARDWARE)
        // No transformations: both layers use this exact builder, so their keys match.
        .crossfade(false) // the Compose Crossfade owns the transition
        .build()
