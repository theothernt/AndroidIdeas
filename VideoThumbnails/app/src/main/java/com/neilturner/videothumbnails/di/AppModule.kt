package com.neilturner.videothumbnails.di

import com.neilturner.videothumbnails.data.RawResourceVideoRepository
import com.neilturner.videothumbnails.data.VideoRepository
import com.neilturner.videothumbnails.ui.screens.HomeViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module
import java.util.concurrent.Executors

val appModule = module {
    single<VideoRepository> { RawResourceVideoRepository(androidContext()) }
    single<CoroutineDispatcher>(named("ThumbnailDispatcher")) {
        Executors.newFixedThreadPool(4).asCoroutineDispatcher()
    }
    viewModel { HomeViewModel(get()) }
}
