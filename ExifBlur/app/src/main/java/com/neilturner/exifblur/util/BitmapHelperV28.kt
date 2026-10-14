package com.neilturner.exifblur.util

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
class BitmapHelperV28(
    private val imageRepository: ImageRepository,
    private val contentResolver: ContentResolver
) : BitmapLoader {

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

                // Stream the image straight into ImageDecoder instead of staging
                // the whole file through a temp file and a 20MB heap buffer.
                //
                // ImageDecoder.createSource has no InputStream overload, so local
                // MediaStore images use the ContentResolver overload - the decoder
                // pulls bytes from the provider on demand and nothing is copied.
                // SMB has no ContentResolver behind it, so those images are staged
                // through a temp file, which keeps the bytes off the heap.
                var encodedDimensions: Pair<Int, Int>? = null

                val stagedSource = createSource(finalUri) ?: return@withContext null
                val source = stagedSource.source

                val bitmap = try {
                    ImageDecoder.decodeBitmap(source) { decoder, imageInfo, _ ->
                        val infoSize = imageInfo.size
                        val originalDims = if (infoSize.width > 0 && infoSize.height > 0) {
                            infoSize.width to infoSize.height
                        } else {
                            Log.w(TAG, "ImageDecoder reported no usable size, falling back to header bounds")
                            headerBytes.decodeDimensions()
                        }
                        encodedDimensions = originalDims

                        if (originalDims != null) {
                            val (originalWidth, originalHeight) = originalDims
                            val sampleSize = calculateTargetSampleSize(originalWidth, originalHeight, targetWidth, targetHeight)
                            if (sampleSize > 1) {
                                // setTargetSampleSize proportionally scales the encoded image
                                // using the decoder's native sampling without skewing the aspect ratio.
                                // ImageDecoder then automatically applies any EXIF orientation upright.
                                decoder.setTargetSampleSize(sampleSize)
                            }
                        }

                        // Use HARDWARE for GPU memory (available API 28+)
                        decoder.setAllocator(ImageDecoder.ALLOCATOR_HARDWARE)
                    }
                } finally {
                    // Delete the staging file if one was created for this decode
                    stagedSource.tempFileToDelete?.delete()
                }

                val decodeDuration = System.currentTimeMillis() - decodeStartTime
                Log.d(TAG, "[Step 3/3] Complete: Decoded ${bitmap.width}x${bitmap.height} in ${decodeDuration}ms")

                // Calculate memory savings
                val originalPixels = encodedDimensions?.let {
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

                // ImageDecoder auto-applies EXIF orientation, so no rotation is needed
                BitmapResult(bitmap, metadata)
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

    /**
     * Builds the best available [ImageDecoder.Source] for [uri].
     *
     * ImageDecoder.createSource cannot consume an arbitrary InputStream, so local
     * MediaStore images go through the ContentResolver overload - the decoder reads
     * from the provider on demand, so no temp file and no full-file heap buffer.
     * Remote (SMB) images have no ContentResolver behind them, so they are staged
     * through a temp file, which keeps the bytes off the heap at the cost of disk I/O.
     */
    private suspend fun createSource(uri: Uri): StagedSource? {
        return if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            try {
                StagedSource(ImageDecoder.createSource(contentResolver, uri))
            } catch (e: Exception) {
                Log.w(TAG, "ContentResolver source failed for $uri, falling back to temp file", e)
                stageToTempFile(uri)
            }
        } else {
            stageToTempFile(uri)
        }
    }

    private suspend fun stageToTempFile(uri: Uri): StagedSource? {
        return try {
            val tempFile = File.createTempFile("image_decoder_", ".tmp")
            try {
                imageRepository.openInputStream(uri)?.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                } ?: return null
                StagedSource(ImageDecoder.createSource(tempFile), tempFile)
            } catch (e: Exception) {
                tempFile.delete()
                throw e
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stage $uri for ImageDecoder", e)
            null
        }
    }

    /** An [ImageDecoder.Source] plus the temp file that backs it, if one was needed. */
    private class StagedSource(
        val source: ImageDecoder.Source,
        val tempFileToDelete: File? = null
    )

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
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeByteArray(this, 0, this.size, options)
        return if (options.outWidth > 0 && options.outHeight > 0) {
            options.outWidth to options.outHeight
        } else null
    }

    /**
     * Largest power-of-two sample size that still leaves the bitmap large enough to
     * fill the target box. Delegates to the shared implementation so this decode
     * path and [BitmapHelper] stay in agreement.
     */
    private fun calculateTargetSampleSize(originalWidth: Int, originalHeight: Int, targetWidth: Int, targetHeight: Int): Int =
        calculateSampleSize(originalWidth, originalHeight, targetWidth, targetHeight)

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
