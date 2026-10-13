package com.neilturner.videothumbnails

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.util.DebugLogger
import coil3.video.VideoFrameDecoder
import com.neilturner.videothumbnails.di.appModule
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class VideoThumbnailsApp :
    Application(),
    SingletonImageLoader.Factory {
    private val okHttpClient by lazy {
        OkHttpClient
            .Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@VideoThumbnailsApp)
            modules(appModule)
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val builder =
            ImageLoader
                .Builder(context)
                .components {
                    // Prepend the VideoFrameDecoder so it's tested before the default image
                    // decoders. Debug only: prepended, it header-sniffs every image request
                    // before the real decoder gets a chance to run.
                    if (BuildConfig.DEBUG) {
                        add(VideoFrameDecoder.Factory())
                    }
                    add(OkHttpNetworkFetcherFactory(callFactory = okHttpClient))
                }.diskCache(null)

        // Coil's own docs warn that DebugLogger reduces performance, and it defaults to Debug
        // level, so every memory cache hit/miss and decode event builds a message string.
        if (BuildConfig.DEBUG) {
            builder.logger(DebugLogger())
        }

        return builder.build()
    }
}
