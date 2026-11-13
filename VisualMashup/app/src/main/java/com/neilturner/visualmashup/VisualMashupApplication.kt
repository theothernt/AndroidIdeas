package com.neilturner.visualmashup

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import okhttp3.OkHttpClient

/**
 * Provides a Coil [ImageLoader] with a descriptive User-Agent.
 *
 * Wikimedia's image CDN rejects OkHttp's default `okhttp/x.y` User-Agent with a 403, so the request
 * needs a real app User-Agent.
 */
class VisualMashupApplication : Application(), SingletonImageLoader.Factory {

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "VisualMashup/1.0 (Android TV)")
                    .build()
                chain.proceed(request)
            }
            .build()

        return ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(client)) }
            .build()
    }
}