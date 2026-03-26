package com.neilturner.perfview.di

import com.neilturner.perfview.overlay.OverlayPermissionManager
import org.koin.dsl.module

val appModule = module {
    single { OverlayPermissionManager(get()) }
    includes(dataModule, domainModule, uiModule)
}
