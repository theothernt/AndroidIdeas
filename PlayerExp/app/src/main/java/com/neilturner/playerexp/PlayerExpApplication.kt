package com.neilturner.playerexp

import android.app.Application
import com.neilturner.playerexp.data.plex.PlexAccountStore
import com.neilturner.playerexp.data.plex.PlexWebSocketObserver
import com.neilturner.playerexp.data.plex.SessionCleanupManager

class PlayerExpApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        SessionCleanupManager.initialize(PlexAccountStore(applicationContext).clientIdentifier())
        PlexWebSocketObserver.start(applicationContext)
    }

    override fun onTerminate() {
        PlexWebSocketObserver.stop()
        SessionCleanupManager.shutdown()
        super.onTerminate()
    }
}
