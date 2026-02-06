package com.neilturner.fourlayers

import android.app.Application
import com.neilturner.fourlayers.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class FourLayersApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidLogger()
            androidContext(this@FourLayersApplication)
            modules(appModule)
        }
    }
}
