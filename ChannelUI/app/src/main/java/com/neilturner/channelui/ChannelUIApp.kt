package com.neilturner.channelui

import android.app.Application
import com.neilturner.channelui.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class ChannelUIApp : Application() {
    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidLogger()
            androidContext(this@ChannelUIApp)
            modules(appModule)
        }
    }
}
