package com.neilturner.exifblur.di

import coil3.ImageLoader
import coil3.SingletonImageLoader
import com.neilturner.exifblur.data.ImageRepository
import com.neilturner.exifblur.data.ImageRepositoryImpl
import com.neilturner.exifblur.data.SambaImageProvider
import com.neilturner.exifblur.ui.screens.MainViewModel
import com.neilturner.exifblur.util.ExifMetadataLoader
import com.neilturner.exifblur.util.LocationHelper
import com.neilturner.exifblur.util.RamMonitor
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule =
    module {
        single { SambaImageProvider() }
        single { LocationHelper(androidContext()) }
        single { RamMonitor(androidContext()) }
        single { ExifMetadataLoader(get()) }
        single<ImageLoader> { SingletonImageLoader.get(androidContext()) }

        singleOf(::ImageRepositoryImpl) { bind<ImageRepository>() }
        viewModel { MainViewModel(get(), get(), get(), get(), get(), androidContext()) }
    }
