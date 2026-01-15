package com.neilturner.exifblur.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import com.neilturner.exifblur.data.ImageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BitmapHelper(private val imageRepository: ImageRepository) {
    suspend fun loadResizedBitmap(uri: Uri, targetWidth: Int = 1080, targetHeight: Int = 1920): Bitmap? {
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

                // First, decode with inJustDecodeBounds=true to check dimensions
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                imageRepository.openInputStream(finalUri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, options)
                }

                // Calculate inSampleSize
                options.inSampleSize = calculateInSampleSize(options, targetWidth, targetHeight)

                // Decode bitmap with inSampleSize set
                options.inJustDecodeBounds = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    options.inPreferredConfig = Bitmap.Config.HARDWARE
                }
                val bitmap = imageRepository.openInputStream(finalUri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, options)
                }

                bitmap?.let { bmp ->
                    val orientation = imageRepository.openInputStream(finalUri)?.use { stream ->
                        ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                    } ?: ExifInterface.ORIENTATION_NORMAL

                    rotateBitmap(bmp, orientation)
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

            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun rotateBitmap(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1.0f, 1.0f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1.0f, -1.0f)
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
