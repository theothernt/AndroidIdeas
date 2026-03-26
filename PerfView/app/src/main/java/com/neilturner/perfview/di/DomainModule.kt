package com.neilturner.perfview.di

import com.neilturner.perfview.domain.cpu.CpuMonitor
import org.koin.dsl.module

val domainModule = module {
    single { CpuMonitor(get()) }
}
