package com.neilturner.persistentlist

import android.app.Application
import com.neilturner.persistentlist.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class PersistentListApp : Application() {
    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidLogger()
            androidContext(this@PersistentListApp)
            modules(appModule)
        }
    }
}
