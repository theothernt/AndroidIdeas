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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext
import kotlin.math.min
import kotlin.time.Duration.Companion.milliseconds

/**
 * Process-scoped observer for Plex's notification firehose.
 *
 * Frames are parsed into [PlexLibraryEvent]s, published on [events] and summarised in the log. A
 * library or queue change also marks [PlexLibraryChanges], so a row fetched before the event is not
 * trusted as fresh. Playback progress is published but does not dirty the cache: it only redraws a
 * bar, and the row it belongs to is either on screen or irrelevant.
 *
 * Raw frames are no longer logged. The socket carries every client's activity, several frames a
 * second while something is playing, which drowned the log and cost more than the parsing does.
 */
object PlexWebSocketObserver {

    private const val LOG_TAG = "PlexWebSocket"
    private const val RECONNECT_DELAY_MS = 3_000L
    private const val MAX_RECONNECT_DELAY_MS = 60_000L

    private val _events = MutableSharedFlow<PlexLibraryEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<PlexLibraryEvent> = _events.asSharedFlow()

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
        var reconnectDelayMs = RECONNECT_DELAY_MS
        while (currentCoroutineContext().isActive) {
            try {
                val serverUrl = store.serverUrl()
                val accountToken = store.accountToken()
                if (serverUrl.isNullOrBlank() || accountToken.isNullOrBlank()) {
                    Log.d(LOG_TAG, "No stored Plex server URL or token yet, waiting to connect")
                } else {
                    Log.d(LOG_TAG, "Connecting to notification stream")
                    val closeReason = api.observeNotificationFrames(serverUrl, accountToken) { frame ->
                        handleFrame(frame)
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
                reconnectDelayMs = RECONNECT_DELAY_MS
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(LOG_TAG, "Connection failed: type=${e.javaClass.simpleName}, message=${e.message}", e)
            }
            // Back off so a server that is down is not asked again on a fixed, fast loop.
            Log.d(LOG_TAG, "Reconnecting in ${reconnectDelayMs}ms")
            delay(reconnectDelayMs.milliseconds)
            reconnectDelayMs = min(reconnectDelayMs * 2, MAX_RECONNECT_DELAY_MS)
        }
    }

    private suspend fun handleFrame(frame: Frame) {
        val text = when (frame) {
            is Frame.Text -> frame.readText()
            is Frame.Binary -> {
                Log.d(LOG_TAG, "Binary frame: ${frame.data.size} bytes")
                return
            }

            else -> return
        }

        val events = PlexNotificationParser.parse(text)
        if (events.isEmpty()) return

        val dirty = events.any { it is PlexLibraryEvent.LibraryChanged }
        if (dirty) PlexLibraryChanges.markDirty()
        events.forEach { _events.emit(it) }

        val summary = events.joinToString(", ") { event ->
            when (event) {
                is PlexLibraryEvent.LibraryChanged -> "libraryChanged(${event.source})"
                is PlexLibraryEvent.ProgressChanged ->
                    "progressChanged(ratingKey=${event.ratingKey}, viewOffset=${event.viewOffset})"
            }
        }
        Log.d(
            LOG_TAG,
            if (dirty) "$summary, On Deck marked stale" else summary
        )
    }
}
