package com.neilturner.fourlayers.di

import com.neilturner.fourlayers.ui.player.MediaPlayerViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    viewModel { MediaPlayerViewModel(androidContext()) }
}
