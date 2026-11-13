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
import coil3.size.Size
import com.neilturner.visualmashup.data.SourceImage
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
 * Precomputes and caches a small set of pixelation levels per image.
 *
 * Level 0 is the original image (cropped to the target aspect at draw time); the rest are
 * progressively smaller 16:9 versions. The pixelate transition animates the level index instead of
 * re-downsampling on every frame, so the cost is paid once per image.
 */
object PixelationCache {

    private const val LEVELS = 14
    private const val CACHE_ENTRIES = 8

    private val cache = LruCache<String, List<PixelationLevel>>(CACHE_ENTRIES)
    private val mutex = Mutex()

    suspend fun getLevels(
        context: Context,
        image: SourceImage,
        widthPx: Int,
        heightPx: Int,
    ): List<PixelationLevel> {
        cache.get(image.url)?.let { return it }
        return mutex.withLock {
            cache.get(image.url)?.let { return@withLock it }
            val levels = withContext(Dispatchers.Default) {
                val request = ImageRequest.Builder(context)
                    .data(image.url)
                    .size(Size(widthPx, heightPx))
                    .build()
                val result = SingletonImageLoader.get(context).execute(request)
                if (result !is SuccessResult) return@withContext emptyList()
                val source = result.image
                val bitmap = if (source is BitmapImage) {
                    source.bitmap
                } else {
                    val b = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
                    source.draw(Canvas(b))
                    b
                }
                buildLevels(bitmap, widthPx.toFloat() / heightPx)
            }
            cache.put(image.url, levels)
            levels
        }
    }

    private fun buildLevels(bitmap: Bitmap, targetAspect: Float): List<PixelationLevel> {
        val crop = cropRect(bitmap.width, bitmap.height, targetAspect)

        // Level 0 is the original bitmap (already in Coil's cache), cropped at draw time.
        val full = PixelationLevel(
            image = bitmap.asImageBitmap(),
            srcLeft = crop[0],
            srcTop = crop[1],
            srcWidth = crop[2],
            srcHeight = crop[3],
        )
        val levels = mutableListOf(full)

        // Hardware bitmaps can't be drawn into a software canvas, so make a temporary copy.
        var current: Bitmap = if (bitmap.config == Bitmap.Config.HARDWARE) {
            bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: bitmap
        } else {
            bitmap
        }
        val cropped = Bitmap.createBitmap(current, crop[0], crop[1], crop[2], crop[3])
        current = cropped

        for (i in 1 until LEVELS) {
            val w = (cropped.width / (1 shl i)).coerceAtLeast(1)
            val h = (cropped.height / (1 shl i)).coerceAtLeast(1)
            val small = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            Canvas(small).drawBitmap(
                current,
                null,
                Rect(0, 0, w, h),
                Paint().apply { isFilterBitmap = true },
            )
            levels.add(PixelationLevel(small.asImageBitmap(), 0, 0, w, h))
            current = small
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