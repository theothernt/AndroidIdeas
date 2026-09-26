package com.neilturner.playerexp

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.neilturner.playerexp.data.plex.PlexAccountStore
import com.neilturner.playerexp.data.plex.PlexWebSocketObserver
import com.neilturner.playerexp.data.plex.SessionCleanupManager
import com.neilturner.playerexp.data.plex.plexImageHttpClient

class PlayerExpApplication : Application(), SingletonImageLoader.Factory {

    override fun onCreate() {
        super.onCreate()
        SessionCleanupManager.initialize(PlexAccountStore(applicationContext).clientIdentifier())
        PlexWebSocketObserver.start(applicationContext)
    }

    override fun onTerminate() {
        PlexWebSocketObserver.stop()
        SessionCleanupManager.shutdown()
        super.onTerminate()
    }

    /** Posters go through a client of our own so resized requests can fall back to full size. */
    override fun newImageLoader(context: Context): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = ::plexImageHttpClient))
            }
            .build()
}
