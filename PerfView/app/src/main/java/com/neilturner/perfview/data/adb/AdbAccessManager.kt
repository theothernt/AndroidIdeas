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

open class AdbAccessException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

class AdbAuthorizationRequiredException(
    message: String,
    cause: Throwable? = null,
) : AdbAccessException(message, cause)

class AdbUnavailableException(
    message: String,
    cause: Throwable? = null,
) : AdbAccessException(message, cause)
