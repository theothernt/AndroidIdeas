package com.neilturner.perfview.data.adb

interface AdbAccessManager {
    fun hasGrantedAccess(): Boolean

    @Throws(Exception::class)
    suspend fun requestAccess(timeoutMillis: Long)

    @Throws(Exception::class)
    suspend fun ensureConnected(timeoutMillis: Long = DEFAULT_CONNECT_TIMEOUT_MILLIS)

    companion object {
        const val DEFAULT_CONNECT_TIMEOUT_MILLIS = 2_500L
    }
}
