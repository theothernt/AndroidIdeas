package com.neilturner.exifblur.data

import android.net.Uri

interface ImageRepository {
    suspend fun getImages(): List<Uri>
    suspend fun getExifMetadata(uri: Uri): ExifMetadata
    suspend fun openInputStream(uri: Uri): java.io.InputStream?
    fun isExifEnabled(): Boolean
}

class ImageRepositoryImpl(
    private val localProvider: LocalImageProvider,
    private val sambaProvider: SambaImageProvider
) : ImageRepository {
    private var useSamba = true // Default to SMB
    private var exifEnabled = false // Default to enabled

    override suspend fun getImages(): List<Uri> {
        return if (useSamba) sambaProvider.getImages() else localProvider.getImages()
    }

    override suspend fun getExifMetadata(uri: Uri): ExifMetadata {
        return if (useSamba) sambaProvider.getExifMetadata(uri) else localProvider.getExifMetadata(uri)
    }

    override suspend fun openInputStream(uri: Uri): java.io.InputStream? {
        return if (useSamba) sambaProvider.openInputStream(uri) else localProvider.openInputStream(uri)
    }

    override fun isExifEnabled(): Boolean = exifEnabled
}
