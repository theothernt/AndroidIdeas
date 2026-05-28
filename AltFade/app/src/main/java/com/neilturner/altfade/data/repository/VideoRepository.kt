package com.neilturner.altfade.data.repository

import android.net.Uri
import androidx.core.net.toUri

class VideoRepository {
    private val videoUrls = listOf(
        "http://sylvan.apple.com/itunes-assets/Aerials116/v4/e6/1a/ca/e61acac6-1c10-41a6-b796-7dc03fbc4517/ann0120_flamecomp_v0011.00400416__SDR_2K_AVC.mov",
        "http://sylvan.apple.com/itunes-assets/Aerials116/v4/e6/1a/ca/e61acac6-1c10-41a6-b796-7dc03fbc4517/ann0080_flamecomp_v0004.00853686__SDR_2K_AVC.mov",
        "http://sylvan.apple.com/itunes-assets/Aerials116/v4/e6/1a/ca/e61acac6-1c10-41a6-b796-7dc03fbc4517/ann0040_flamecomp_v0006.00789786__SDR_2K_AVC.mov",
        "http://sylvan.apple.com/itunes-assets/Aerials116/v4/e6/1a/ca/e61acac6-1c10-41a6-b796-7dc03fbc4517/ann0060_flamecomp_v0003.00815886__SDR_2K_AVC.mov"
    )

    private val transitionImageUrl =
        "http://mynokiablog.com/wp-content/uploads/2012/05/8081.jpg"
        // "https://images.unsplash.com/photo-1500530855697-b586d89ba3ee?auto=format&fit=crop&w=1920&q=85"

    fun getVideos(): List<Uri> {
        return videoUrls.map { it.toUri() }
    }

    fun getTransitionImage(): Uri {
        return transitionImageUrl.toUri()
    }
}
