package com.neilturner.exifblur.util

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import com.neilturner.exifblur.data.ExifMetadata
import com.neilturner.exifblur.data.ImageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import kotlin.math.roundToInt

@RequiresApi(Build.VERSION_CODES.P)
class BitmapHelperV28(private val imageRepository: ImageRepository) : BitmapLoader {

    companion object {
        private const val TAG = "BitmapHelperV28"
        private const val HEADER_BUFFER_SIZE = 512 * 1024 // 512KB
    }

    override suspend fun loadResizedBitmap(
        uri: Uri,
        targetWidth: Int,
        targetHeight: Int
    ): BitmapResult? {
        return withContext(Dispatchers.IO) {
            try {
                val finalUri = prepareUri(uri)
                val startTime = System.currentTimeMillis()
                Log.d(TAG, "=========================================")
                Log.d(TAG, "Starting ImageDecoder load for URI: $finalUri")
                Log.d(TAG, "Target dimensions: ${targetWidth}x${targetHeight}")

                // Step 1: Read Header Buffer for EXIF metadata
                val headerBytes = readHeaderBytes(finalUri) ?: return@withContext null

                // Step 2: Extract Metadata from header
                val metadata = extractMetadata(headerBytes)

                // Step 3: Decode with ImageDecoder
                val decodeStartTime = System.currentTimeMillis()
                Log.d(TAG, "[Step 3/3] Decoding bitmap with ImageDecoder...")

                // Create a temp file to hold the image data for ImageDecoder
                val tempFile = File.createTempFile("image_decoder_", ".tmp")
                try {
                    // Copy the full image to temp file
                    imageRepository.openInputStream(finalUri)?.use { input ->
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    } ?: return@withContext null

                    val source = ImageDecoder.createSource(tempFile)

                    val bitmap = ImageDecoder.decodeBitmap(source) { decoder, imageInfo, _ ->
                        // Get original dimensions from imageInfo parameter
                        val originalWidth = imageInfo.size.width
                        val originalHeight = imageInfo.size.height
                        
                        // Calculate sample size
                        val sampleSize = calculateSampleSize(originalWidth, originalHeight, targetWidth, targetHeight)

                        // Set target size for hardware scaling
                        if (sampleSize > 1) {
                            val scaledWidth = originalWidth / sampleSize
                            val scaledHeight = originalHeight / sampleSize
                            decoder.setTargetSize(scaledWidth, scaledHeight)
                        }

                        // Use HARDWARE for GPU memory (available API 28+)
                        // Note: ImageDecoder.HARDWARE = 2
                        decoder.setAllocator(2)
                    }

                    val decodeDuration = System.currentTimeMillis() - decodeStartTime
                    Log.d(TAG, "[Step 3/3] Complete: Decoded ${bitmap.width}x${bitmap.height} in ${decodeDuration}ms")

                    // Calculate memory savings
                    val originalPixels = headerBytes.decodeDimensions()?.let {
                        it.first.toLong() * it.second.toLong()
                    } ?: (bitmap.width.toLong() * bitmap.height.toLong())

                    val finalPixels = bitmap.width.toLong() * bitmap.height.toLong()
                    val reductionPercent = if (originalPixels > 0) {
                        ((1.0 - finalPixels.toDouble() / originalPixels.toDouble()) * 100).roundToInt()
                    } else 0

                    val totalDuration = System.currentTimeMillis() - startTime
                    Log.d(TAG, "=========================================")
                    Log.d(TAG, "LOAD COMPLETE (ImageDecoder)")
                    Log.d(TAG, "  Total duration: ${totalDuration}ms")
                    Log.d(TAG, "  Memory reduction: ${reductionPercent}%")
                    Log.d(TAG, "  Final size: ${bitmap.width}x${bitmap.height}")
                    Log.d(TAG, "  EXIF orientation: Auto-applied by ImageDecoder")
                    Log.d(TAG, "=========================================")

                    // ImageDecoder auto-rotates, so rotation is 0
                    BitmapResult(bitmap, metadata, 0f)
                } finally {
                    tempFile.delete()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading bitmap with ImageDecoder", e)
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
        Log.d(TAG, "[Step 1/3] Reading 512KB header buffer...")
        return imageRepository.openInputStream(uri)?.use { stream ->
            val headerBytes = readStreamBytes(stream, HEADER_BUFFER_SIZE)
            val duration = System.currentTimeMillis() - headerStartTime
            Log.d(TAG, "[Step 1/3] Complete: Read ${headerBytes.size} bytes in ${duration}ms")
            headerBytes
        } ?: run {
            val duration = System.currentTimeMillis() - headerStartTime
            Log.e(TAG, "[Step 1/3] Failed: Could not open header stream after ${duration}ms")
            null
        }
    }

    private fun extractMetadata(headerBytes: ByteArray): ExifMetadata {
        val metadataStartTime = System.currentTimeMillis()
        Log.d(TAG, "[Step 2/3] Extracting EXIF metadata...")
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
            val duration = System.currentTimeMillis() - metadataStartTime
            Log.d(TAG, "[Step 2/3] Complete: Extracted metadata in ${duration}ms")
            if (meta.date != null) Log.d(TAG, "   - Date: ${meta.date}")
            if (meta.offset != null) Log.d(TAG, "   - Offset: ${meta.offset}")
            if (meta.latitude != null && meta.longitude != null) Log.d(TAG, "   - Location: ${meta.latitude}, ${meta.longitude}")
            meta
        }
    }

    private fun ByteArray.decodeDimensions(): Pair<Int, Int>? {
        val options = android.graphics.BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        android.graphics.BitmapFactory.decodeByteArray(this, 0, this.size, options)
        return if (options.outWidth > 0 && options.outHeight > 0) {
            options.outWidth to options.outHeight
        } else null
    }

    private fun calculateSampleSize(originalWidth: Int, originalHeight: Int, targetWidth: Int, targetHeight: Int): Int {
        var sampleSize = 1
        if (originalHeight > targetHeight || originalWidth > targetWidth) {
            val halfHeight = originalHeight / 2
            val halfWidth = originalWidth / 2
            while (maxOf(halfHeight, halfWidth) / sampleSize >= maxOf(targetHeight, targetWidth)) {
                sampleSize *= 2
            }
        }
        return sampleSize
    }

    private fun readStreamBytes(inputStream: java.io.InputStream, limit: Int): ByteArray {
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
