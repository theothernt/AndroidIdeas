package com.neilturner.perfview.di

import com.neilturner.perfview.data.adb.AdbAccessManager
import com.neilturner.perfview.data.adb.AdbConnectionGate
import com.neilturner.perfview.data.adb.AdbShellClient
import com.neilturner.perfview.data.adb.LibAdbAccessManager
import com.neilturner.perfview.data.adb.LibAdbShellClient
import com.neilturner.perfview.data.cpu.repository.CpuRepositoryImpl
import com.neilturner.perfview.data.cpu.source.AdbTopCpuReader
import com.neilturner.perfview.domain.cpu.repository.CpuRepository
import com.neilturner.perfview.platform.AndroidNotificationPermissionChecker
import com.neilturner.perfview.platform.AndroidOverlayAccessChecker
import com.neilturner.perfview.platform.NotificationPermissionChecker
import com.neilturner.perfview.platform.OverlayAccessChecker
import org.koin.dsl.module

val dataModule = module {
    single<AdbAccessManager> { LibAdbAccessManager(get()) }
    single { AdbConnectionGate(get()) }
    single<AdbShellClient> { LibAdbShellClient(get(), get()) }
    single { AdbTopCpuReader(get()) }
    single<CpuRepository> { CpuRepositoryImpl(get()) }
    single<NotificationPermissionChecker> { AndroidNotificationPermissionChecker(get()) }
    single<OverlayAccessChecker> { AndroidOverlayAccessChecker(get()) }
}
