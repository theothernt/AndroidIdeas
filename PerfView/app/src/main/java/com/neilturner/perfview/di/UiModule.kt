package com.neilturner.perfview.di

import com.neilturner.perfview.ui.dashboard.PerfViewViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val uiModule = module {
    viewModelOf(::PerfViewViewModel)
}
