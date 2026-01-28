package com.neilturner.videothumbnails

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import coil3.util.DebugLogger
import coil3.video.VideoFrameDecoder
import com.neilturner.videothumbnails.di.appModule
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class VideoThumbnailsApp : Application(), SingletonImageLoader.Factory {
    
    private val okHttpClient by lazy {
        OkHttpClient.Builder()
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
        return ImageLoader.Builder(context)
            .components {
                // Prepend the VideoFrameDecoder so it's tested before the default image decoders
                add(VideoFrameDecoder.Factory())
                add(OkHttpNetworkFetcherFactory(callFactory = okHttpClient))
            }
            .crossfade(true)
            .logger(DebugLogger())
            .build()
    }
}
