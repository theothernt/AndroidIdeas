package com.neilturner.overlayparty.di

import com.neilturner.overlayparty.ui.main.MainViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    viewModel { MainViewModel() }
}
