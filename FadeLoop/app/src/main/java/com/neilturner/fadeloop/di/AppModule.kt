package com.neilturner.fadeloop.di

import com.neilturner.fadeloop.data.cache.VideoCacheManager
import com.neilturner.fadeloop.data.repository.VideoRepository
import com.neilturner.fadeloop.ui.player.PlayerViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    // Singleton definition for the Repository
    single { VideoRepository() }

    // Video Cache Manager
    single { VideoCacheManager(androidContext()) }

    // ViewModel definition
    viewModel { PlayerViewModel(get()) }
}
