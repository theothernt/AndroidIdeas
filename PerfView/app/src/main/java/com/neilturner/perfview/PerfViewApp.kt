package com.neilturner.perfview

import android.app.Application
import com.neilturner.perfview.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class PerfViewApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@PerfViewApp)
            modules(appModule)
        }
    }
}
