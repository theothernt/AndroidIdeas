package com.neilturner.fadeloop.data.cache

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

@OptIn(UnstableApi::class)
class VideoCacheManager(context: Context) {

    private val cacheDir = File(context.cacheDir, "video_cache")
    private val cacheEvictor = LeastRecentlyUsedCacheEvictor(500L * 1024 * 1024) // 500MB Cache
    private val databaseProvider: DatabaseProvider = StandaloneDatabaseProvider(context)

    val simpleCache: Cache by lazy {
        SimpleCache(cacheDir, cacheEvictor, databaseProvider)
    }

    fun getUsedSpace(): Long {
        return simpleCache.cacheSpace
    }

    fun getCachedPercentage(url: String): Int {
        val key = url // Default key is the URL
        val contentMetadata = simpleCache.getContentMetadata(key)
        val contentLength = ContentMetadata.getContentLength(contentMetadata)

        if (contentLength.toInt() == C.LENGTH_UNSET || contentLength == 0L) {
            return 0 // Length unknown or empty
        }

        val cachedBytes = simpleCache.getCachedBytes(key, 0, contentLength)
        return ((cachedBytes.toFloat() / contentLength) * 100).toInt()
    }
}
