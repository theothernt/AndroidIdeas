package com.neilturner.playerexp.data.plex

import android.content.Context
import android.util.Log
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration.Companion.milliseconds

/**
 * Process-scoped observer for Plex's notification firehose. Observation only: frames are logged raw,
 * nothing is parsed and no REST calls or state updates are triggered.
 */
object PlexWebSocketObserver {

    private const val LOG_TAG = "PlexWebSocket"
    private const val RECONNECT_DELAY_MS = 3_000L

    private var scope: CoroutineScope? = null
    private var observerJob: Job? = null
    private var api: PlexApi? = null
    private val lock = Any()

    fun start(context: Context) {
        synchronized(lock) {
            if (observerJob?.isActive == true) {
                Log.d(LOG_TAG, "Already running, ignoring start")
                return
            }
            val store = PlexAccountStore(context.applicationContext)
            val ownedApi = PlexApi(store.clientIdentifier())
            api = ownedApi
            val ownedScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            scope = ownedScope
            observerJob = ownedScope.launch { observe(store, ownedApi) }
        }
    }

    fun stop() {
        synchronized(lock) {
            observerJob?.cancel()
            observerJob = null
            scope?.coroutineContext?.get(Job)?.cancel()
            scope = null
            api?.close()
            api = null
        }
    }

    private suspend fun observe(store: PlexAccountStore, api: PlexApi) {
        while (currentCoroutineContext().isActive) {
            try {
                val serverUrl = store.serverUrl()
                val accountToken = store.accountToken()
                if (serverUrl.isNullOrBlank() || accountToken.isNullOrBlank()) {
                    Log.d(LOG_TAG, "No stored Plex server URL or token yet, waiting to connect")
                } else {
                    Log.d(LOG_TAG, "Connecting to notification stream")
                    val closeReason = api.observeNotificationFrames(serverUrl, accountToken) { frame ->
                        logFrame(frame)
                    }
                    Log.d(
                        LOG_TAG,
                        if (closeReason == null) {
                            "Stream closed by server without a reason"
                        } else {
                            "Stream closed: code=${closeReason.code}, reason=${closeReason.message}"
                        }
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(LOG_TAG, "Connection failed: type=${e.javaClass.simpleName}, message=${e.message}", e)
            }
            Log.d(LOG_TAG, "Reconnecting in ${RECONNECT_DELAY_MS}ms")
            delay(RECONNECT_DELAY_MS.milliseconds)
        }
    }

    private fun logFrame(frame: Frame) {
        when (frame) {
            is Frame.Text -> Log.d(LOG_TAG, "Frame: ${frame.readText()}")
            is Frame.Binary -> Log.d(LOG_TAG, "Binary frame: ${frame.data.size} bytes")
            else -> Log.d(LOG_TAG, "Frame: ${frame::class.simpleName}")
        }
    }
}
