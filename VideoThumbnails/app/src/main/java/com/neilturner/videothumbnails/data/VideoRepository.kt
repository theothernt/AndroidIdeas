package com.neilturner.videothumbnails.data

import android.content.Context
import com.neilturner.videothumbnails.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

interface VideoRepository {
    suspend fun getVideos(): List<Video>
}

class RawResourceVideoRepository(
    private val context: Context,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : VideoRepository {
    override suspend fun getVideos(): List<Video> =
        withContext(Dispatchers.IO) {
            val videoFiles =
                listOf(
                    R.raw.comm1,
                    R.raw.comm2,
                    R.raw.fireos8,
                    R.raw.tvos15,
                )

            val allVideos = mutableListOf<Video>()

            videoFiles.forEach { resourceId ->
                try {
                    val jsonString =
                        context.resources
                            .openRawResource(resourceId)
                            .bufferedReader()
                            .use { it.readText() }
                    val videoResponse = json.decodeFromString<VideoResponse>(jsonString)
                    // Pre-resolve drawable IDs for all videos
                    val videosWithDrawableIds = videoResponse.assets.map { video ->
                        val drawableName = video.getThumbnailDrawableName()
                        val drawableId = context.resources.getIdentifier(
                            drawableName,
                            "drawable",
                            context.packageName
                        )
                        video.copy(thumbnailDrawableId = drawableId)
                    }
                    allVideos.addAll(videosWithDrawableIds)
                } catch (e: Exception) {
                    // Log error but continue with other files
                    println("Error loading video resource $resourceId: ${e.message}")
                }
            }

            allVideos
        }
}
