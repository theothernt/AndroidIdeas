package com.neilturner.visualmashup.ui.effects

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import coil3.BitmapImage
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.size.Scale
import com.neilturner.visualmashup.data.SourceImage
import com.neilturner.visualmashup.data.maxImageSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** One pre-rendered pixelation step. [srcLeft]/[srcTop]/[srcWidth]/[srcHeight] crop the source. */
data class PixelationLevel(
    val image: ImageBitmap,
    val srcLeft: Int,
    val srcTop: Int,
    val srcWidth: Int,
    val srcHeight: Int,
)

/**
 * Precomputes and caches the pixelation steps per image.
 *
 * Level 0 is the full-resolution image and is **not** cached: it is fetched from Coil on each call
 * (a memory-cache hit) and only referenced for the duration of the transition. Caching it would pin
 * a full-screen bitmap per image and keep it in graphics memory for as long as the LRU holds it.
 * The downscaled levels are small (RGB_565) and are what the LRU keeps.
 */
object PixelationCache {

    private const val LEVELS = 14

    /** Current, next and previous images are enough for a smooth transition. */
    private const val CACHE_ENTRIES = 3

    /** Downscaled levels only (level 0 is not cached). */
    private val cache = LruCache<String, List<PixelationLevel>>(CACHE_ENTRIES)
    private val mutex = Mutex()

    /** Drops every pre-rendered level; used when the Pixelate transition is no longer selected. */
    fun clear() {
        cache.evictAll()
    }

    suspend fun getLevels(
        context: Context,
        image: SourceImage,
        widthPx: Int,
        heightPx: Int,
    ): List<PixelationLevel> {
        val key = cacheKey(image, widthPx, heightPx)
        val aspect = widthPx.toFloat() / heightPx
        val cachedDowns = cache.get(key)

        val bitmap = withContext(Dispatchers.Default) {
            val request = ImageRequest.Builder(context)
                .data(image.url)
                .size(maxImageSize(widthPx, heightPx))
                // Match AsyncImage with ContentScale.Crop: fill the target and crop at draw time.
                .scale(Scale.FILL)
                .build()
            val result = SingletonImageLoader.get(context).execute(request)
            if (result !is SuccessResult) return@withContext null
            val source = result.image
            if (source is BitmapImage) {
                source.bitmap
            } else {
                val b = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
                source.draw(Canvas(b))
                b
            }
        } ?: return emptyList()

        val full = fullLevel(bitmap, aspect)
        val downs = cachedDowns ?: mutex.withLock {
            cache.get(key) ?: withContext(Dispatchers.Default) {
                buildDowns(bitmap, aspect).also { cache.put(key, it) }
            }
        }
        return listOf(full) + downs
    }

    private fun cacheKey(image: SourceImage, widthPx: Int, heightPx: Int): String =
        "${image.url}#${widthPx}x${heightPx}"

    private fun fullLevel(bitmap: Bitmap, targetAspect: Float): PixelationLevel {
        val crop = cropRect(bitmap.width, bitmap.height, targetAspect)
        return PixelationLevel(
            image = bitmap.asImageBitmap(),
            srcLeft = crop[0],
            srcTop = crop[1],
            srcWidth = crop[2],
            srcHeight = crop[3],
        )
    }

    private fun buildDowns(bitmap: Bitmap, targetAspect: Float): List<PixelationLevel> {
        val crop = cropRect(bitmap.width, bitmap.height, targetAspect)

        // Hardware bitmaps can't be drawn into a software canvas, so make a temporary copy.
        var current: Bitmap = if (bitmap.config == Bitmap.Config.HARDWARE) {
            bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: bitmap
        } else {
            bitmap
        }
        var srcRect = Rect(crop[0], crop[1], crop[0] + crop[2], crop[1] + crop[3])

        val levels = mutableListOf<PixelationLevel>()
        for (i in 1 until LEVELS) {
            val w = (crop[2] / (1 shl i)).coerceAtLeast(1)
            val h = (crop[3] / (1 shl i)).coerceAtLeast(1)
            // The mosaic doesn't need an alpha channel, so RGB_565 halves the per-level memory.
            val small = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565)
            Canvas(small).drawBitmap(
                current,
                srcRect,
                Rect(0, 0, w, h),
                Paint().apply { isFilterBitmap = true },
            )
            levels.add(PixelationLevel(small.asImageBitmap(), 0, 0, w, h))
            current = small
            srcRect = Rect(0, 0, current.width, current.height)
        }
        return levels
    }

    /** Largest centered rectangle with [targetAspect], as [left, top, width, height]. */
    private fun cropRect(width: Int, height: Int, targetAspect: Float): IntArray {
        val srcAspect = width.toFloat() / height
        return if (srcAspect > targetAspect) {
            val cropW = (height * targetAspect).toInt().coerceAtLeast(1)
            intArrayOf((width - cropW) / 2, 0, cropW, height)
        } else {
            val cropH = (width / targetAspect).toInt().coerceAtLeast(1)
            intArrayOf(0, (height - cropH) / 2, width, cropH)
        }
    }
}