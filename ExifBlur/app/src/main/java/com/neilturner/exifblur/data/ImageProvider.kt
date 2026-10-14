package com.neilturner.exifblur.data

import android.net.Uri
import java.io.InputStream

interface ImageProvider {

    suspend fun getImages(): ImageListing

    suspend fun openInputStream(uri: Uri): InputStream?
}
