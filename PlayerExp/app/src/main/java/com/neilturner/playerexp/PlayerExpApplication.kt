package com.neilturner.playerexp

import android.app.Application
import com.neilturner.playerexp.data.plex.PlexAccountStore
import com.neilturner.playerexp.data.plex.PlexSessionCleanup

class PlayerExpApplication : Application() {
    val plexSessionCleanup: PlexSessionCleanup by lazy {
        PlexSessionCleanup(PlexAccountStore(applicationContext).clientIdentifier())
    }

    override fun onTerminate() {
        plexSessionCleanup.close()
        super.onTerminate()
    }
}
