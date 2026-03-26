package com.neilturner.perfview.di

import com.neilturner.perfview.data.adb.AdbAccessManager
import com.neilturner.perfview.data.adb.AdbShellClient
import com.neilturner.perfview.data.adb.LibAdbAccessManager
import com.neilturner.perfview.data.adb.LibAdbShellClient
import com.neilturner.perfview.data.cpu.repository.CpuRepositoryImpl
import com.neilturner.perfview.data.cpu.source.AdbTopCpuReader
import com.neilturner.perfview.domain.cpu.repository.CpuRepository
import org.koin.dsl.module

val dataModule = module {
    single<AdbAccessManager> { LibAdbAccessManager(get()) }
    single<AdbShellClient> { LibAdbShellClient(get(), get()) }
    single { AdbTopCpuReader(get()) }
    single<CpuRepository> { CpuRepositoryImpl(get()) }
}
