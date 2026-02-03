package com.neilturner.exifblur.data

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocalImageProvider(private val context: Context) : ImageProvider {
    override suspend fun getImages(): List<Uri> = withContext(Dispatchers.IO) {
        val images = mutableListOf<Uri>()
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        val query = context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        )

        query?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val contentUri = Uri.withAppendedPath(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id.toString()
                )
                images.add(contentUri)
            }
        }
        images
    }

    override suspend fun getExifMetadata(uri: Uri): ExifMetadata = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val exifInterface = ExifInterface(inputStream)
                val date = exifInterface.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                    ?: exifInterface.getAttribute(ExifInterface.TAG_DATETIME)
                val offset = exifInterface.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL)
                    ?: exifInterface.getAttribute(ExifInterface.TAG_OFFSET_TIME)
                    ?: exifInterface.getAttribute(ExifInterface.TAG_OFFSET_TIME_DIGITIZED)
                val latLng = exifInterface.latLong

                ExifMetadata(
                    date = date,
                    offset = offset,
                    latitude = latLng?.get(0),
                    longitude = latLng?.get(1)
                )
            } ?: ExifMetadata()
        } catch (e: Exception) {
            ExifMetadata()
        }
    }

    override suspend fun openInputStream(uri: Uri): java.io.InputStream? = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)
    }
}
