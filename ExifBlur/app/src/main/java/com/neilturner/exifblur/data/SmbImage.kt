package com.neilturner.exifblur.data

import android.net.Uri

/**
 * Distinct model type for Coil so the SMB fetcher/keyer are selected explicitly
 * and can never be confused with content:// or file:// Uris.
 */
data class SmbImage(
    val uri: Uri,
)
