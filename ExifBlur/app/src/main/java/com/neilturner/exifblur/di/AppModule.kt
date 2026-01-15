package com.neilturner.exifblur.di

import com.neilturner.exifblur.data.ImageRepository
import com.neilturner.exifblur.data.ImageRepositoryImpl
import com.neilturner.exifblur.data.LocalImageProvider
import com.neilturner.exifblur.data.SambaImageProvider
import com.neilturner.exifblur.ui.screens.MainViewModel
import com.neilturner.exifblur.util.BitmapHelper
import com.neilturner.exifblur.util.LocationHelper
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single { LocalImageProvider(androidContext()) }
    single { SambaImageProvider() }
    single { LocationHelper(androidContext()) }
    single { BitmapHelper(get()) }
    singleOf(::ImageRepositoryImpl) { bind<ImageRepository>() }
    viewModel { MainViewModel(get(), get(), get()) }
}
