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

data class BitmapResult(
    val bitmap: Bitmap,
    val metadata: ExifMetadata
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

                Log.d("BitmapHelper", "Starting loadResizedBitmap for URI: $finalUri")

                // Step 1: Read Header Buffer (512KB) for Metadata and Dimensions
                Log.d("BitmapHelper", "Reading 512KB header buffer...")
                val HEADER_SIZE = 512 * 1024
                val headerBytes = imageRepository.openInputStream(finalUri)?.use { stream ->
                    readHeaderBytes(stream, HEADER_SIZE)
                } ?: run {
                    Log.e("BitmapHelper", "Failed to open header stream")
                    return@withContext null
                }
                Log.d("BitmapHelper", "Read ${headerBytes.size} bytes into header buffer")

                // Step 2: Extract Metadata and Orientation from Header Buffer
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

                // Step 3: Extract Dimensions from Header Buffer
                val options = BitmapFactory.Options()
                if (exifDimensions.first > 0 && exifDimensions.second > 0) {
                    options.outWidth = exifDimensions.first
                    options.outHeight = exifDimensions.second
                    Log.d("BitmapHelper", "Using EXIF dimensions: ${exifDimensions.first}x${exifDimensions.second}")
                } else {
                    options.inJustDecodeBounds = true
                    headerBytes.inputStream().use { stream ->
                        BitmapFactory.decodeStream(stream, null, options)
                    }
                    Log.d("BitmapHelper", "Using decoded dimensions: ${options.outWidth}x${options.outHeight}")
                }

                val originalWidth = options.outWidth
                val originalHeight = options.outHeight
                if (originalWidth <= 0 || originalHeight <= 0) {
                    Log.e("BitmapHelper", "Failed to determine dimensions from header buffer")
                    return@withContext null
                }

                // Calculate inSampleSize
                options.inSampleSize = calculateInSampleSize(options, targetWidth, targetHeight)
                Log.d("BitmapHelper", "Calculated inSampleSize: ${options.inSampleSize}")

                // Step 4: Final Decode (The only other connection)
                Log.d("BitmapHelper", "Opening final stream for bitmap decode...")
                options.inJustDecodeBounds = false
                val needsExifTransform = orientation != ExifInterface.ORIENTATION_NORMAL &&
                    orientation != ExifInterface.ORIENTATION_UNDEFINED
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    options.inPreferredConfig = if (needsExifTransform) {
                        Bitmap.Config.ARGB_8888
                    } else {
                        Bitmap.Config.HARDWARE
                    }
                }
                
                val bitmap = imageRepository.openInputStream(finalUri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, options)
                } ?: run {
                    Log.e("BitmapHelper", "Final Pass failed: Could not open input stream")
                    return@withContext null
                }

                Log.d("BitmapHelper", "Decoded bitmap dimensions: ${bitmap.width}x${bitmap.height} (after sampling)")

                val rotatedBitmap = rotateBitmap(bitmap, orientation)
                
                // Calculate memory savings
                val originalPixels = originalWidth.toLong() * originalHeight.toLong()
                val finalPixels = rotatedBitmap.width.toLong() * rotatedBitmap.height.toLong()
                val reductionPercent = if (originalPixels > 0) {
                    ((1.0 - finalPixels.toDouble() / originalPixels.toDouble()) * 100).roundToInt()
                } else 0
                Log.d("BitmapHelper", "Final bitmap dimensions: ${rotatedBitmap.width}x${rotatedBitmap.height} (after rotation)")
                Log.d("BitmapHelper", "Memory reduction: ${reductionPercent}% ($originalPixels -> $finalPixels pixels)")
                
                BitmapResult(rotatedBitmap, metadata)
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

            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
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

    private fun rotateBitmap(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_UNDEFINED,
            ExifInterface.ORIENTATION_NORMAL -> return bitmap
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1.0f, 1.0f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1.0f, -1.0f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1.0f, 1.0f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(-90f)
                matrix.postScale(-1.0f, 1.0f)
            }
            else -> return bitmap
        }
        return try {
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated != bitmap) {
                bitmap.recycle()
            }
            rotated
        } catch (e: Exception) {
            e.printStackTrace()
            bitmap
        }
    }
}
