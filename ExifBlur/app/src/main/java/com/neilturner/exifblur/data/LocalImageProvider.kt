package com.neilturner.exifblur.data

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocalImageProvider(private val context: Context) : ImageProvider {
    override suspend fun getImages(): ImageListing = withContext(Dispatchers.IO) {
        try {
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
            ImageListing(images)
        } catch (e: Exception) {
            Log.e("LocalImageProvider", "Failed to list images from media store", e)
            ImageListing(error = "Local media unavailable: ${e.message ?: "unknown error"}")
        }
    }

    override suspend fun openInputStream(uri: Uri): java.io.InputStream? = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)
    }
}
