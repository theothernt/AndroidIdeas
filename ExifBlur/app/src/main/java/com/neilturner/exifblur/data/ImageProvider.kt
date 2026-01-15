package com.neilturner.exifblur.data

import android.net.Uri

interface ImageProvider {
    suspend fun getImages(): List<Uri>
    suspend fun getExifMetadata(uri: Uri): ExifMetadata
    suspend fun openInputStream(uri: Uri): java.io.InputStream?
}
