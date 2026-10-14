package com.neilturner.exifblur.data

import android.net.Uri
import java.io.InputStream

interface ImageProvider {

    suspend fun getImages(): ImageListing

    /**
     * Opens a stream for the image. Used for large images where the
     * full file should not be loaded into memory at once.
     */
    suspend fun openInputStream(uri: Uri): InputStream?
}
