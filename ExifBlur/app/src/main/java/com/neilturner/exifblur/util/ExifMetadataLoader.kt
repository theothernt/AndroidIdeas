package com.neilturner.exifblur.util

import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.neilturner.exifblur.data.ExifMetadata
import com.neilturner.exifblur.data.ImageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream

/** Reads only the image header for caption metadata; decoding is left to Coil. */
class ExifMetadataLoader(
    private val imageRepository: ImageRepository,
) {
    suspend fun load(uri: Uri): ExifMetadata? =
        withContext(Dispatchers.IO) {
            val header =
                imageRepository.openInputStream(uri)?.use { stream ->
                    val buffer = ByteArray(HEADER_BYTES)
                    var total = 0
                    while (total < buffer.size) {
                        val read = stream.read(buffer, total, buffer.size - total)
                        if (read == -1) break
                        total += read
                    }
                    if (total > 0) buffer.copyOf(total) else null
                } ?: return@withContext null

            try {
                ByteArrayInputStream(header).use { input ->
                    val exif = ExifInterface(input)
                    val latitude = exif.latLong?.getOrNull(0)
                    val longitude = exif.latLong?.getOrNull(1)
                    ExifMetadata(
                        date =
                            exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                                ?: exif.getAttribute(ExifInterface.TAG_DATETIME),
                        offset =
                            exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL)
                                ?: exif.getAttribute(ExifInterface.TAG_OFFSET_TIME)
                                ?: exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_DIGITIZED),
                        latitude = latitude,
                        longitude = longitude,
                    )
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to read EXIF for $uri", t)
                null
            }
        }

    private companion object {
        const val TAG = "ExifMetadataLoader"
        const val HEADER_BYTES = 512 * 1024
    }
}
