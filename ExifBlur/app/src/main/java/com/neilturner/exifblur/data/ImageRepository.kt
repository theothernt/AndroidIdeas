package com.neilturner.exifblur.data

import android.net.Uri

data class ImageListing(
    val uris: List<Uri> = emptyList(),
    val sourceName: String = "",
    val error: String? = null,
)

interface ImageRepository {

    suspend fun getImages(): ImageListing

    suspend fun openInputStream(uri: Uri): java.io.InputStream?
}

class ImageRepositoryImpl(
    private val localProvider: LocalImageProvider,
    private val sambaProvider: SambaImageProvider,
) : ImageRepository {
    override suspend fun getImages(): ImageListing {
        val sambaListing = sambaProvider.getImages()
        if (sambaListing.error == null && sambaListing.uris.isNotEmpty()) {
            return sambaListing.copy(sourceName = SOURCE_SAMBA)
        }

        // Fall back to local media only when the Samba source failed or is empty
        val localListing = localProvider.getImages()
        return if (localListing.error == null && localListing.uris.isNotEmpty()) {
            localListing.copy(sourceName = SOURCE_LOCAL)
        } else {
            localListing.copy(
                sourceName = SOURCE_LOCAL,
                error = sambaListing.error ?: localListing.error ?: "No images found",
            )
        }
    }

    override suspend fun openInputStream(uri: Uri): java.io.InputStream? =
        if (uri.scheme == SAMBA_SCHEME) {
            sambaProvider.openInputStream(uri)
        } else {
            localProvider.openInputStream(uri)
        }

    companion object {
        const val SOURCE_SAMBA = "Samba"
        const val SOURCE_LOCAL = "Local"
        private const val SAMBA_SCHEME = "smb"
    }
}
