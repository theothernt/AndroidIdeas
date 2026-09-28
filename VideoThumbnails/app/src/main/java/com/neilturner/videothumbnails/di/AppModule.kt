package com.neilturner.videothumbnails.di

import com.neilturner.videothumbnails.data.DataStoreVideoSelectionStore
import com.neilturner.videothumbnails.data.RawResourceVideoRepository
import com.neilturner.videothumbnails.data.VideoRepository
import com.neilturner.videothumbnails.data.VideoSelectionStore
import com.neilturner.videothumbnails.ui.screens.HomeViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule =
    module {
        single<VideoRepository> { RawResourceVideoRepository(androidContext()) }
        single<VideoSelectionStore> { DataStoreVideoSelectionStore(androidContext()) }
        viewModel { HomeViewModel(get(), get()) }
    }
