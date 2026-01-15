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

                // Open stream once and reuse for all operations
                imageRepository.openInputStream(finalUri)?.use { inputStream ->
                    // Create a buffered copy of the stream for multiple reads
                    val bufferedBytes = inputStream.readBytes()

                    // Extract EXIF metadata and orientation from the same data
                    val (metadata, orientation) = bufferedBytes.inputStream().use { exifStream ->
                        //Log.d("BitmapHelper", "Trying to get EXIF info...")
                        val exifInterface = ExifInterface(exifStream)
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
                        metadata to orientation
                    }

                    // First, decode with inJustDecodeBounds=true to check dimensions
                    val options = BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    bufferedBytes.inputStream().use { boundsStream ->
                        BitmapFactory.decodeStream(boundsStream, null, options)
                    }
                    
                    val originalWidth = options.outWidth
                    val originalHeight = options.outHeight
                    Log.d("BitmapHelper", "Original image dimensions: ${originalWidth}x${originalHeight}")

                    // Calculate inSampleSize
                    options.inSampleSize = calculateInSampleSize(options, targetWidth, targetHeight)
                    Log.d("BitmapHelper", "Calculated inSampleSize: ${options.inSampleSize} for target ${targetWidth}x${targetHeight}")

                    // Decode bitmap with inSampleSize set
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
                    val bitmap = bufferedBytes.inputStream().use { bitmapStream ->
                        BitmapFactory.decodeStream(bitmapStream, null, options)
                    }

                    if (bitmap == null) return@withContext null

                    Log.d("BitmapHelper", "Decoded bitmap dimensions: ${bitmap.width}x${bitmap.height} (after sampling)")

                    //Log.d("BitmapHelper", "Loaded bitmap with dimensions: ${bitmap.width}x${bitmap.height}")

                    val rotatedBitmap = rotateBitmap(bitmap, orientation)
                    
                    // Calculate memory savings
                    val originalPixels = originalWidth * originalHeight
                    val finalPixels = rotatedBitmap.width * rotatedBitmap.height
                    val reductionPercent = ((1.0 - finalPixels.toDouble() / originalPixels.toDouble()) * 100).roundToInt()
                    Log.d("BitmapHelper", "Final bitmap dimensions: ${rotatedBitmap.width}x${rotatedBitmap.height} (after rotation)")
                    Log.d("BitmapHelper", "Memory reduction: ${reductionPercent}% (${originalPixels} -> $finalPixels pixels)")
                    
                    BitmapResult(rotatedBitmap, metadata)
                } ?: return@withContext null
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
