package com.neilturner.overlayparty.di

import com.neilturner.overlayparty.data.LocationRepository
import com.neilturner.overlayparty.data.MessageRepository
import com.neilturner.overlayparty.data.MusicRepository
import com.neilturner.overlayparty.data.TimeRepository
import com.neilturner.overlayparty.data.WeatherRepository
import com.neilturner.overlayparty.ui.main.MainViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single { WeatherRepository() }
    single { TimeRepository() }
    single { MusicRepository() }
    single { LocationRepository() }
    single { MessageRepository() }

    viewModel { MainViewModel(get(), get(), get(), get(), get()) }
}