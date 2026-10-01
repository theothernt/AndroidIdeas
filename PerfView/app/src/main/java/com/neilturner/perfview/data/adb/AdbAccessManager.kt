package com.neilturner.perfview.data.adb

interface AdbAccessManager {

    @Throws(Exception::class)
    suspend fun requestAccess(timeoutMillis: Long)
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
