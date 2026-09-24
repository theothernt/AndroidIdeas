package com.neilturner.playerexp.data.plex

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlin.math.pow

interface SessionCleanup {
    fun enqueueTranscodeStop(serverUrl: String, accountToken: String, sessionIdentifier: String)
    fun enqueueFullCleanup(
        serverUrl: String,
        accountToken: String,
        sessionIdentifier: String,
        ratingKey: String,
        finalPositionMillis: Long,
        durationMillis: Long
    )
    fun shutdown()
}

object SessionCleanupManager {

    private const val LOG_TAG = "SessionCleanupManager"
    private const val MAX_RETRIES = 3
    private const val BASE_DELAY_MS = 1000L

    private var _instance: SessionCleanup? = null
    private val instanceLock = Any()

    fun initialize(clientIdentifier: String) {
        synchronized(instanceLock) {
            if (_instance == null) {
                _instance = Impl(clientIdentifier)
            }
        }
    }

    fun getInstance(): SessionCleanup {
        return _instance ?: throw IllegalStateException("SessionCleanupManager not initialized. Call initialize() first.")
    }

    fun shutdown() {
        synchronized(instanceLock) {
            _instance?.shutdown()
            _instance = null
        }
    }

    private class Impl(clientIdentifier: String) : SessionCleanup {

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val api = PlexApi(clientIdentifier)
        private val queue = Channel<CleanupRequest>(Channel.UNLIMITED)

        init {
            startProcessor()
        }

        override fun enqueueTranscodeStop(
            serverUrl: String,
            accountToken: String,
            sessionIdentifier: String
        ) {
            queue.trySend(CleanupRequest(
                serverUrl = serverUrl,
                accountToken = accountToken,
                sessionIdentifier = sessionIdentifier
            ))
        }

        override fun enqueueFullCleanup(
            serverUrl: String,
            accountToken: String,
            sessionIdentifier: String,
            ratingKey: String,
            finalPositionMillis: Long,
            durationMillis: Long
        ) {
            queue.trySend(CleanupRequest(
                serverUrl = serverUrl,
                accountToken = accountToken,
                sessionIdentifier = sessionIdentifier,
                ratingKey = ratingKey,
                finalPositionMillis = finalPositionMillis,
                durationMillis = durationMillis
            ))
        }

        private fun startProcessor() {
            scope.launch {
                for (request in queue) {
                    processRequest(request)
                }
            }
        }

        private suspend fun processRequest(request: CleanupRequest) {
            var attempt = 0
            while (attempt < MAX_RETRIES) {
                try {
                    // Try stopping the universal transcode session if one was active.
                    // For DirectPlay there is no active transcode session, so this may 404 or fail;
                    // do not let that prevent reporting timeline state = "stopped".
                    try {
                        api.stopUniversalTranscodeSession(
                            request.serverUrl,
                            request.accountToken,
                            request.sessionIdentifier
                        )
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.d(LOG_TAG, "Transcode stop request returned error or not applicable: ${e.message}")
                    }

                    if (request.ratingKey != null) {
                        val duration = if (request.durationMillis > 0L) request.durationMillis else 0L
                        val position = if (request.finalPositionMillis >= 0L) request.finalPositionMillis else duration
                        api.reportTimeline(
                            serverUrl = request.serverUrl,
                            accountToken = request.accountToken,
                            ratingKey = request.ratingKey,
                            state = "stopped",
                            timeMillis = position,
                            durationMillis = duration,
                            sessionIdentifier = request.sessionIdentifier
                        )
                    }

                    Log.d(LOG_TAG, "Session cleanup completed: ${request.sessionIdentifier}")
                    return

                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    attempt++
                    Log.w(LOG_TAG, "Cleanup attempt $attempt failed for ${request.sessionIdentifier}: ${e.message}")
                    if (attempt >= MAX_RETRIES) {
                        Log.e(LOG_TAG, "Session cleanup failed permanently: ${request.sessionIdentifier}")
                        return
                    }
                    val delayMs = BASE_DELAY_MS * (2.0.pow((attempt - 1).toDouble())).toLong()
                    delay(delayMs)
                }
            }
        }

        override fun shutdown() {
            scope.coroutineContext[Job]?.cancel()
            queue.close()
            api.close()
        }

        private data class CleanupRequest(
            val serverUrl: String,
            val accountToken: String,
            val sessionIdentifier: String,
            val ratingKey: String? = null,
            val finalPositionMillis: Long = -1,
            val durationMillis: Long = -1
        )
    }
}