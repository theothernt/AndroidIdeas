package com.neilturner.exifblur.data

data class ExifMetadata(
    val date: String? = null,
    val cameraModel: String? = null,
    val aperture: String? = null,
    val shutterSpeed: String? = null,
    val iso: String? = null,
    val focalLength: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null
)
