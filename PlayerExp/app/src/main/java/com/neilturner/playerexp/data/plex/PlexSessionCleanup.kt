package com.neilturner.playerexp.data.plex

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Owns best-effort Plex session cleanup for the lifetime of the application process. */
class PlexSessionCleanup(clientIdentifier: String) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val api = PlexApi(clientIdentifier)

    fun stopUniversalTranscodeSession(
        serverUrl: String,
        accountToken: String,
        sessionIdentifier: String
    ) {
        scope.launch {
            try {
                api.stopUniversalTranscodeSession(serverUrl, accountToken, sessionIdentifier)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(
                    LOG_TAG,
                    "Application cleanup failed: type=${e.javaClass.simpleName}, message=${e.message}"
                )
            }
        }
    }

    fun close() {
        scope.cancel()
        api.close()
    }

    private companion object {
        const val LOG_TAG = "PlexSessionCleanup"
    }
}
