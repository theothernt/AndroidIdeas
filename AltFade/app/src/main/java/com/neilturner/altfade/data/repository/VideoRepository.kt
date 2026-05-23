package com.neilturner.altfade.data.repository

import android.net.Uri
import androidx.core.net.toUri

class VideoRepository {
    private val videoUrls = listOf(
        "http://sylvan.apple.com/itunes-assets/Aerials116/v4/e6/1a/ca/e61acac6-1c10-41a6-b796-7dc03fbc4517/ann0070_flamecomp_v0008.00289423__SDR_2K_HEVC.mov",
        "http://sylvan.apple.com/itunes-assets/Aerials116/v4/e6/1a/ca/e61acac6-1c10-41a6-b796-7dc03fbc4517/ann0040_flamecomp_v0006.00789786__SDR_2K_HEVC.mov",
        "http://sylvan.apple.com/itunes-assets/Aerials116/v4/e6/1a/ca/e61acac6-1c10-41a6-b796-7dc03fbc4517/ann0100_flamecomp_v0002.00880232__SDR_2K_HEVC.mov",
        "http://sylvan.apple.com/itunes-assets/Aerials116/v4/e6/1a/ca/e61acac6-1c10-41a6-b796-7dc03fbc4517/M010_C009_F01_2K_HEVC.mov"
    )

    fun getVideos(): List<Uri> {
        return videoUrls.map { it.toUri() }
    }
}
