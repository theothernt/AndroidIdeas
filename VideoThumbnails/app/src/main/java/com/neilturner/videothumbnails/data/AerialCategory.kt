package com.neilturner.videothumbnails.data

import androidx.compose.runtime.Immutable

@Immutable
data class VideoCategory(
    val id: String,
    val displayName: String,
)

object AerialCategories {
    val CITYSCAPE = VideoCategory("5EF41171-4862-4F93-800C-AD86CE5E6891", "Cityscape")
    val LANDSCAPE = VideoCategory("A33A55D9-EDEA-4596-A850-6C10B54FBBB5", "Landscape")
    val UNDERWATER = VideoCategory("8BE8B524-6EAE-43F5-A3E8-01DCFA1BCD4B", "Underwater")
    val SPACE = VideoCategory("55B7C95D-CEAF-4FD8-ADEF-F5BC657D8F6D", "Space")
    val BEACH = VideoCategory("scene:beach", "Beach")
    val PATTERNS = VideoCategory("scene:patterns", "Patterns")
    val COZY = VideoCategory("scene:fire", "Cozy")

    val all: List<VideoCategory> =
        listOf(
            CITYSCAPE,
            LANDSCAPE,
            UNDERWATER,
            SPACE,
            BEACH,
            PATTERNS,
            COZY,
        )

    private val byCategoryId: Map<String, VideoCategory> =
        listOf(CITYSCAPE, LANDSCAPE, UNDERWATER, SPACE).associateBy { it.id }

    private val byScene: Map<String, VideoCategory> =
        mapOf(
            "city" to CITYSCAPE,
            "nature" to LANDSCAPE,
            "countryside" to LANDSCAPE,
            "waterfall" to LANDSCAPE,
            "sea" to UNDERWATER,
            "space" to SPACE,
            "beach" to BEACH,
            "patterns" to PATTERNS,
            "fire" to COZY,
        )

    fun forVideo(video: Video): VideoCategory =
        video.categories.firstNotNullOfOrNull { byCategoryId[it] }
            ?: byScene[video.scene]
            ?: LANDSCAPE
}
