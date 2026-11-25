package com.neilturner.exifblur

import android.app.Application
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import com.neilturner.exifblur.data.SambaImageProvider
import com.neilturner.exifblur.data.SmbFetcher
import com.neilturner.exifblur.data.SmbImageKeyer
import com.neilturner.exifblur.di.appModule
import com.neilturner.exifblur.slideshow.CacheConfig
import com.neilturner.exifblur.slideshow.CacheMode
import com.neilturner.exifblur.slideshow.currentDisplaySize
import okio.Path.Companion.toOkioPath
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin

class ExifBlurApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidLogger()
            androidContext(this@ExifBlurApplication)
            modules(appModule)
        }

        SingletonImageLoader.setSafe { context ->
            val sambaProvider = GlobalContext.get().get<SambaImageProvider>()
            val builder =
                ImageLoader
                    .Builder(context)
                    .components {
                        add(SmbImageKeyer())
                        add(SmbFetcher.Factory(sambaProvider))
                    }

            if (CacheConfig.mode == CacheMode.RAM_ONLY) {
                // Room for exactly current + next + previous at display resolution.
                val size = currentDisplaySize(context)
                builder.memoryCache {
                    MemoryCache
                        .Builder()
                        .maxSizeBytes(size.width.toLong() * size.height * BYTES_PER_PIXEL * RAM_CACHE_BITMAPS)
                        .build()
                }
                // Coil falls back to a shared singleton disk cache unless it is
                // explicitly disabled, so pass null as well as disabling it per request.
                builder.diskCache(null)
            } else {
                builder.memoryCache {
                    MemoryCache
                        .Builder()
                        // current + 2 ahead + 1 behind, all at display resolution
                        .maxSizePercent(context, 0.20)
                        .build()
                }
                builder.diskCache {
                    DiskCache
                        .Builder()
                        .directory(context.cacheDir.resolve("image_cache").toOkioPath())
                        .maxSizeBytes(512L * 1024 * 1024)
                        .build()
                }
            }

            builder.build()
        }
    }

    private companion object {
        const val BYTES_PER_PIXEL = 4L
        const val RAM_CACHE_BITMAPS = 3L
    }
}
