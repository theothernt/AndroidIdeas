package com.neilturner.perfview.di

import org.koin.dsl.module

val appModule = module {
    includes(dataModule, domainModule, uiModule)
}
