package com.neilturner.overlayparty

import android.app.Application
import com.neilturner.overlayparty.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class OverlayApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@OverlayApplication)
            modules(appModule)
        }
    }
}
