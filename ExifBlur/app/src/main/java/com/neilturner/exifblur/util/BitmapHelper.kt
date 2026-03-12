package com.neilturner.exifblur.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.neilturner.exifblur.data.ExifMetadata
import com.neilturner.exifblur.data.ImageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlin.math.roundToInt

data class BitmapResult(
    val bitmap: Bitmap,
    val metadata: ExifMetadata,
    val rotation: Float
)

class BitmapHelper(private val imageRepository: ImageRepository) {

    companion object {
        private const val TAG = "BitmapHelper"
        private const val HEADER_BUFFER_SIZE = 512 * 1024 // 512KB
    }

    suspend fun loadResizedBitmap(uri: Uri, targetWidth: Int = 1920, targetHeight: Int = 1080): BitmapResult? {
        return withContext(Dispatchers.IO) {
            try {
                val finalUri = prepareUri(uri)
                val startTime = System.currentTimeMillis()
                Log.d(TAG, "=========================================")
                Log.d(TAG, "Starting loadResizedBitmap for URI: $finalUri")
                Log.d(TAG, "Target dimensions: ${targetWidth}x${targetHeight}")

                // Step 1: Read Header Buffer
                val headerBytes = readHeaderBytes(finalUri) ?: return@withContext null

                // Step 2: Extract Metadata and Orientation
                val (metadata, orientation) = extractMetadata(headerBytes)

                // Step 3: Determine Dimensions & Calculate Sample Size
                val options = decodeDimensions(headerBytes) ?: return@withContext null
                options.inSampleSize = calculateInSampleSize(options, targetWidth, targetHeight)
                options.inJustDecodeBounds = false
                val originalWidth = options.outWidth
                val originalHeight = options.outHeight

                // Step 4: Final Decode
                val rotationDegrees = getRotationDegrees(orientation)
                val originalBitmap = decodeBitmap(finalUri, options, rotationDegrees) ?: return@withContext null

                // Calculate memory savings
                val originalPixels = originalWidth.toLong() * originalHeight.toLong()
                val finalPixels = originalBitmap.width.toLong() * originalBitmap.height.toLong()
                val reductionPercent = if (originalPixels > 0) {
                    ((1.0 - finalPixels.toDouble() / originalPixels.toDouble()) * 100).roundToInt()
                } else 0

                // Step 5: Return result with rotation
                val totalDuration = System.currentTimeMillis() - startTime
                Log.d(TAG, "=========================================")
                Log.d(TAG, "LOAD COMPLETE")
                Log.d(TAG, "  Total duration: ${totalDuration}ms")
                Log.d(TAG, "  Memory reduction: ${reductionPercent}%")
                Log.d(TAG, "  Final size: ${originalBitmap.width}x${originalBitmap.height}")
                Log.d(TAG, "  Original size: ${originalWidth}x${originalHeight}")
                Log.d(TAG, "  Rotation: $rotationDegrees°")
                Log.d(TAG, "=========================================")

                BitmapResult(originalBitmap, metadata, rotationDegrees)
            } catch (e: Exception) {
                Log.e(TAG, "Error loading bitmap", e)
                null
            }
        }
    }

    private fun prepareUri(uri: Uri): Uri {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                MediaStore.setRequireOriginal(uri)
            } catch (e: UnsupportedOperationException) {
                uri
            }
        } else {
            uri
        }
    }

    private suspend fun readHeaderBytes(uri: Uri): ByteArray? {
        val headerStartTime = System.currentTimeMillis()
        Log.d(TAG, "[Step 1/4] Reading 512KB header buffer...")
        return imageRepository.openInputStream(uri)?.use { stream ->
            val headerBytes = readStreamBytes(stream, HEADER_BUFFER_SIZE)
            val duration = System.currentTimeMillis() - headerStartTime
            Log.d(TAG, "[Step 1/4] Complete: Read ${headerBytes.size} bytes in ${duration}ms")
            headerBytes
        } ?: run {
            val duration = System.currentTimeMillis() - headerStartTime
            Log.e(TAG, "[Step 1/4] Failed: Could not open header stream after ${duration}ms")
            null
        }
    }

    private fun extractMetadata(headerBytes: ByteArray): Pair<ExifMetadata, Int> {
        val metadataStartTime = System.currentTimeMillis()
        Log.d(TAG, "[Step 2/4] Extracting EXIF metadata and orientation...")
        return ByteArrayInputStream(headerBytes).use { stream ->
            val exifInterface = ExifInterface(stream)
            val date = exifInterface.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                ?: exifInterface.getAttribute(ExifInterface.TAG_DATETIME)
            val offset = exifInterface.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL)
                ?: exifInterface.getAttribute(ExifInterface.TAG_OFFSET_TIME)
                ?: exifInterface.getAttribute(ExifInterface.TAG_OFFSET_TIME_DIGITIZED)

            val meta = ExifMetadata(
                date = date,
                offset = offset,
                latitude = exifInterface.latLong?.get(0),
                longitude = exifInterface.latLong?.get(1)
            )
            val orient = exifInterface.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            val duration = System.currentTimeMillis() - metadataStartTime
            Log.d(TAG, "[Step 2/4] Complete: Extracted metadata in ${duration}ms")
            if (meta.date != null) Log.d(TAG, "   - Date: ${meta.date}")
            if (meta.offset != null) Log.d(TAG, "   - Offset: ${meta.offset}")
            if (meta.latitude != null && meta.longitude != null) Log.d(TAG, "   - Location: ${meta.latitude}, ${meta.longitude}")
            meta to orient
        }
    }

    private fun decodeDimensions(headerBytes: ByteArray): BitmapFactory.Options? {
        val dimensionsStartTime = System.currentTimeMillis()
        Log.d(TAG, "[Step 3/4] Decoding image dimensions and calculating sample size...")
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeByteArray(headerBytes, 0, headerBytes.size, options)

        if (options.outWidth <= 0 || options.outHeight <= 0) {
            val duration = System.currentTimeMillis() - dimensionsStartTime
            Log.e(TAG, "[Step 3/4] Failed: Could not determine dimensions after ${duration}ms")
            return null
        }
        val duration = System.currentTimeMillis() - dimensionsStartTime
        Log.d(TAG, "[Step 3/4] Complete: Original dimensions ${options.outWidth}x${options.outHeight} in ${duration}ms")
        return options
    }

    private fun getRotationDegrees(orientation: Int): Float {
        return when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    }

    private suspend fun decodeBitmap(uri: Uri, options: BitmapFactory.Options, rotationDegrees: Float): Bitmap? {
        val decodeStartTime = System.currentTimeMillis()
        Log.d(TAG, "[Step 4/4] Opening final stream and decoding bitmap...")
        val isRotationNeeded = rotationDegrees != 0f

        // Use HARDWARE config only if we don't need to modify (rotate) the bitmap.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            options.inPreferredConfig = if (isRotationNeeded) {
                Bitmap.Config.ARGB_8888
            } else {
                Bitmap.Config.HARDWARE
            }
        }

        val bitmap = imageRepository.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        } ?: run {
            val duration = System.currentTimeMillis() - decodeStartTime
            Log.e(TAG, "[Step 4/4] Failed: Could not open final stream after ${duration}ms")
            return null
        }

        val duration = System.currentTimeMillis() - decodeStartTime
        Log.d(TAG, "[Step 4/4] Complete: Decoded ${bitmap.width}x${bitmap.height} bitmap in ${duration}ms")
        return bitmap
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2

            while (maxOf(halfHeight, halfWidth) / inSampleSize >= maxOf(reqHeight, reqWidth)) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun readStreamBytes(inputStream: InputStream, limit: Int): ByteArray {
        val buffer = ByteArray(limit)
        var totalRead = 0
        while (totalRead < limit) {
            val read = inputStream.read(buffer, totalRead, limit - totalRead)
            if (read == -1) break
            totalRead += read
        }
        return if (totalRead == limit) buffer else buffer.copyOf(totalRead)
    }
}
