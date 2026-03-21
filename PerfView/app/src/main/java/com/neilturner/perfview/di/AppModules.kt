package com.neilturner.perfview.di

import android.content.Context
import com.neilturner.perfview.data.adb.AdbShellClient
import com.neilturner.perfview.data.adb.LibAdbShellClient
import com.neilturner.perfview.data.cpu.AdbTopCpuReader
import com.neilturner.perfview.data.cpu.CpuRepository
import com.neilturner.perfview.data.cpu.CpuRepositoryImpl
import com.neilturner.perfview.domain.cpu.ObserveCpuUsageUseCase
import com.neilturner.perfview.ui.dashboard.PerfViewViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val appModule = module {
    single<AdbShellClient> { LibAdbShellClient(get<Context>()) }
    single { AdbTopCpuReader(get()) }
    single<CpuRepository> { CpuRepositoryImpl(get()) }
    single { ObserveCpuUsageUseCase(get()) }
    viewModelOf(::PerfViewViewModel)
}
