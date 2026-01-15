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
import java.io.InputStream
import kotlin.math.roundToInt
import androidx.core.graphics.createBitmap

data class BitmapResult(
    val bitmap: Bitmap,
    val metadata: ExifMetadata,
    val rotationDegrees: Float
)

class BitmapHelper(private val imageRepository: ImageRepository) {
    suspend fun loadResizedBitmap(uri: Uri, targetWidth: Int = 1080, targetHeight: Int = 1920): BitmapResult? {
        return withContext(Dispatchers.IO) {
            try {
                val finalUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    try {
                        MediaStore.setRequireOriginal(uri)
                    } catch (e: UnsupportedOperationException) {
                        uri
                    }
                } else {
                    uri
                }

                val startTime = System.currentTimeMillis()
                Log.d("BitmapHelper", "Starting loadResizedBitmap for URI: $finalUri")

                // Step 1: Read Header Buffer (512KB) for Metadata and Dimensions
                val headerStartTime = System.currentTimeMillis()
                Log.d("BitmapHelper", "Step 1: Reading 512KB header buffer...")
                val HEADER_SIZE = 512 * 1024
                val headerBytes = imageRepository.openInputStream(finalUri)?.use { stream ->
                    readHeaderBytes(stream, HEADER_SIZE)
                } ?: run {
                    Log.e("BitmapHelper", "Step 1 failed: Could not open header stream after ${System.currentTimeMillis() - headerStartTime}ms")
                    return@withContext null
                }
                Log.d("BitmapHelper", "Step 1 complete: Read ${headerBytes.size} bytes in ${System.currentTimeMillis() - headerStartTime}ms")

                // Step 2: Extract Metadata and Orientation from Header Buffer
                val metadataStartTime = System.currentTimeMillis()
                val headerResult = headerBytes.inputStream().use { stream ->
                    val exifInterface = ExifInterface(stream)
                    val metadata = ExifMetadata(
                        date = exifInterface.getAttribute(ExifInterface.TAG_DATETIME),
                        cameraModel = exifInterface.getAttribute(ExifInterface.TAG_MODEL),
                        aperture = exifInterface.getAttribute(ExifInterface.TAG_F_NUMBER),
                        shutterSpeed = exifInterface.getAttribute(ExifInterface.TAG_EXPOSURE_TIME),
                        iso = exifInterface.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY),
                        focalLength = exifInterface.getAttribute(ExifInterface.TAG_FOCAL_LENGTH),
                        latitude = exifInterface.latLong?.get(0),
                        longitude = exifInterface.latLong?.get(1)
                    )
                    val orientation = exifInterface.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                    val width = exifInterface.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0)
                    val height = exifInterface.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0)
                    
                    Triple(metadata, orientation, width to height)
                }
                
                val metadata = headerResult.first
                val orientation = headerResult.second
                val exifDimensions = headerResult.third
                Log.d("BitmapHelper", "Step 2 complete: Extracted metadata in ${System.currentTimeMillis() - metadataStartTime}ms")

                // Step 3: Extract Dimensions from Header Buffer
                val dimensionsStartTime = System.currentTimeMillis()
                val options = BitmapFactory.Options()
                if (exifDimensions.first > 0 && exifDimensions.second > 0) {
                    options.outWidth = exifDimensions.first
                    options.outHeight = exifDimensions.second
                    Log.d("BitmapHelper", "Step 3: Using EXIF dimensions: ${exifDimensions.first}x${exifDimensions.second}")
                } else {
                    options.inJustDecodeBounds = true
                    headerBytes.inputStream().use { stream ->
                        BitmapFactory.decodeStream(stream, null, options)
                    }
                    Log.d("BitmapHelper", "Step 3: Decoded dimensions from header: ${options.outWidth}x${options.outHeight}")
                }

                val originalWidth = options.outWidth
                val originalHeight = options.outHeight
                if (originalWidth <= 0 || originalHeight <= 0) {
                    Log.e("BitmapHelper", "Step 3 failed: Could not determine dimensions after ${System.currentTimeMillis() - dimensionsStartTime}ms")
                    return@withContext null
                }
                Log.d("BitmapHelper", "Step 3 complete in ${System.currentTimeMillis() - dimensionsStartTime}ms")

                // Calculate inSampleSize
                options.inSampleSize = calculateInSampleSize(options, targetWidth, targetHeight)
                Log.d("BitmapHelper", "Sampling: inSampleSize=${options.inSampleSize} for target ${targetWidth}x${targetHeight}")

                // Step 4: Final Decode (The only other connection)
                val decodeStartTime = System.currentTimeMillis()
                Log.d("BitmapHelper", "Step 4: Opening final stream for decoding...")
                options.inJustDecodeBounds = false
                val needsExifTransform = orientation != ExifInterface.ORIENTATION_NORMAL &&
                    orientation != ExifInterface.ORIENTATION_UNDEFINED
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    options.inPreferredConfig = if (needsExifTransform) {
                        Bitmap.Config.HARDWARE
                    } else {
                        Bitmap.Config.ARGB_8888
                    }
                }
                
                val bitmap = imageRepository.openInputStream(finalUri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, options)
                } ?: run {
                    Log.e("BitmapHelper", "Step 4 failed: Could not open final stream after ${System.currentTimeMillis() - decodeStartTime}ms")
                    return@withContext null
                }

                Log.d("BitmapHelper", "Step 4 complete: Decoded ${bitmap.width}x${bitmap.height} bitmap in ${System.currentTimeMillis() - decodeStartTime}ms")

                val rotateStartTime = System.currentTimeMillis()
                Log.d("BitmapHelper", "Rotation analysis complete in ${System.currentTimeMillis() - rotateStartTime}ms")
                
                // Calculate memory savings
                val originalPixels = originalWidth.toLong() * originalHeight.toLong()
                val finalPixels = bitmap.width.toLong() * bitmap.height.toLong()
                val reductionPercent = if (originalPixels > 0) {
                    ((1.0 - finalPixels.toDouble() / originalPixels.toDouble()) * 100).roundToInt()
                } else 0
                
                Log.d("BitmapHelper", "LOAD COMPLETE: Total duration ${System.currentTimeMillis() - startTime}ms. Memory reduction: ${reductionPercent}%")
                
                // Calculate rotation degrees from EXIF orientation
                val rotationDegrees = when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }

                if (rotationDegrees > 0) {
                    Log.i("BitmapHelper", "Rotating bitmap by $rotationDegrees degrees")
                    val matrix = Matrix().apply { postRotate(rotationDegrees) }
                    val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                    Log.i("BitmapHelper", "Rotated bitmap size: ${rotatedBitmap.width}x${rotatedBitmap.height}")
                    BitmapResult(rotatedBitmap, metadata, rotationDegrees)
                } else {
                    BitmapResult(bitmap, metadata, rotationDegrees)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2

            // Calculate sample size based on the larger dimension
            while (maxOf(halfHeight, halfWidth) / inSampleSize >= maxOf(reqHeight, reqWidth)) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun readHeaderBytes(inputStream: InputStream, limit: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var totalRead = 0
        while (totalRead < limit) {
            val read = inputStream.read(buffer, 0, buffer.size.coerceAtMost(limit - totalRead))
            if (read == -1) break
            output.write(buffer, 0, read)
            totalRead += read
        }
        return output.toByteArray()
    }
}
