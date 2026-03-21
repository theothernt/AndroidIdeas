package com.neilturner.perfview.data.adb

fun interface AdbShellClient {
    @Throws(Exception::class)
    suspend fun run(command: String): String
}
