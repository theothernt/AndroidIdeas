package com.neilturner.videothumbnails.data

import android.content.Context
import com.neilturner.videothumbnails.R
import kotlinx.serialization.json.Json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface VideoRepository {
    suspend fun getVideos(): List<Video>
}

class RawResourceVideoRepository(
    private val context: Context,
    private val json: Json = Json { ignoreUnknownKeys = true }
) : VideoRepository {
    override suspend fun getVideos(): List<Video> = withContext(Dispatchers.IO) {
        val jsonString = context.resources.openRawResource(R.raw.videos)
            .bufferedReader()
            .use { it.readText() }
        json.decodeFromString<VideoResponse>(jsonString).assets
    }
}
