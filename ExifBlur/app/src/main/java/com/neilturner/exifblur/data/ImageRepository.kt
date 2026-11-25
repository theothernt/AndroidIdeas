package com.neilturner.exifblur.data

import android.net.Uri
import java.io.InputStream

data class ImageListing(
    val uris: List<Uri> = emptyList(),
    val sourceName: String = "",
    val error: String? = null,
)

interface ImageRepository {

    suspend fun getImages(): ImageListing

    suspend fun openInputStream(uri: Uri): InputStream?
}

class ImageRepositoryImpl(
    private val sambaProvider: SambaImageProvider,
) : ImageRepository {
    override suspend fun getImages(): ImageListing =
        sambaProvider.getImages().copy(sourceName = SOURCE_SAMBA)

    override suspend fun openInputStream(uri: Uri): InputStream? =
        sambaProvider.openInputStream(uri)

    companion object {
        const val SOURCE_SAMBA = "Samba"
    }
}
