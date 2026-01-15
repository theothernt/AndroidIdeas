package com.neilturner.exifblur.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
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

data class BitmapResult(
    val bitmap: Bitmap,
    val metadata: ExifMetadata,
    val rotationDegrees: Float
)

class BitmapHelper(private val imageRepository: ImageRepository) {

    companion object {
        private const val TAG = "BitmapHelper"
        private const val HEADER_BUFFER_SIZE = 512 * 1024 // 512KB
    }

    suspend fun loadResizedBitmap(uri: Uri, targetWidth: Int = 1080, targetHeight: Int = 1920): BitmapResult? {
        return withContext(Dispatchers.IO) {
            try {
                val finalUri = prepareUri(uri)
                val startTime = System.currentTimeMillis()
                Log.d(TAG, "Starting loadResizedBitmap for URI: $finalUri")

                // Step 1: Read Header Buffer
                val headerBytes = readHeaderBytes(finalUri) ?: return@withContext null

                // Step 2: Extract Metadata and Orientation
                val (metadata, orientation) = extractMetadata(headerBytes)

                // Step 3: Determine Dimensions & Calculate Sample Size
                val options = decodeDimensions(headerBytes) ?: return@withContext null
                options.inSampleSize = calculateInSampleSize(options, targetWidth, targetHeight)
                options.inJustDecodeBounds = false

                // Step 4: Final Decode
                val rotationDegrees = getRotationDegrees(orientation)
                val originalBitmap = decodeBitmap(finalUri, options, rotationDegrees) ?: return@withContext null

                // Step 5: Apply Rotation
                val resultBitmap = applyRotation(originalBitmap, rotationDegrees)

                Log.d(TAG, "Load complete in ${System.currentTimeMillis() - startTime}ms. " +
                        "Final size: ${resultBitmap.width}x${resultBitmap.height}")

                BitmapResult(resultBitmap, metadata, rotationDegrees)

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
        return imageRepository.openInputStream(uri)?.use { stream ->
            readStreamBytes(stream, HEADER_BUFFER_SIZE)
        } ?: run {
            Log.e(TAG, "Failed to open input stream or read header.")
            null
        }
    }

    private fun extractMetadata(headerBytes: ByteArray): Pair<ExifMetadata, Int> {
        return ByteArrayInputStream(headerBytes).use { stream ->
            val exifInterface = ExifInterface(stream)
            val meta = ExifMetadata(
                date = exifInterface.getAttribute(ExifInterface.TAG_DATETIME),
                cameraModel = exifInterface.getAttribute(ExifInterface.TAG_MODEL),
                aperture = exifInterface.getAttribute(ExifInterface.TAG_F_NUMBER),
                shutterSpeed = exifInterface.getAttribute(ExifInterface.TAG_EXPOSURE_TIME),
                iso = exifInterface.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY),
                focalLength = exifInterface.getAttribute(ExifInterface.TAG_FOCAL_LENGTH),
                latitude = exifInterface.latLong?.get(0),
                longitude = exifInterface.latLong?.get(1)
            )
            val orient = exifInterface.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            meta to orient
        }
    }

    private fun decodeDimensions(headerBytes: ByteArray): BitmapFactory.Options? {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeByteArray(headerBytes, 0, headerBytes.size, options)
        
        if (options.outWidth <= 0 || options.outHeight <= 0) {
            Log.e(TAG, "Failed to decode bounds from header bytes.")
            return null
        }
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
        }
        
        if (bitmap == null) {
            Log.e(TAG, "Failed to decode final bitmap.")
        }
        return bitmap
    }

    private fun applyRotation(originalBitmap: Bitmap, rotationDegrees: Float): Bitmap {
        if (rotationDegrees == 0f) return originalBitmap

        Log.d(TAG, "Rotating bitmap by $rotationDegrees degrees")
        return try {
            val matrix = Matrix().apply { postRotate(rotationDegrees) }
            // Filter=true for better quality rotation
            val rotated = Bitmap.createBitmap(
                originalBitmap, 0, 0,
                originalBitmap.width, originalBitmap.height,
                matrix, true
            )

            // Critical: Recycle the original if it's different to free memory immediately
            if (rotated != originalBitmap) {
                originalBitmap.recycle()
            }
            rotated
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "OOM during rotation", e)
            // Fallback: return original (wrong orientation is better than crash)
            originalBitmap
        }
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
