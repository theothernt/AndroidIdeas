package com.neilturner.perfview.data.adb

import android.content.Context
import android.os.Build
import android.util.Log
import io.github.muntashirakon.adb.AdbPairingRequiredException
import io.github.muntashirakon.adb.android.AndroidUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LibAdbShellClient(
    private val context: Context,
) : AdbShellClient {
    override suspend fun run(command: String): String = withContext(Dispatchers.IO) {
        Log.d(TAG, "ADB command requested: $command")
        val manager = PerfViewAdbConnectionManager.getInstance(context)
        ensureConnected(manager)

        val stream = manager.openStream("shell:$command")
        try {
            stream.openInputStream().bufferedReader().use { reader ->
                val output = StringBuilder()
                var receivedAnyData = false
                var idleCycles = 0

                while (idleCycles < MAX_IDLE_CYCLES) {
                    if (reader.ready()) {
                        val line = reader.readLine() ?: break
                        output.appendLine(line)
                        receivedAnyData = true
                        idleCycles = 0
                    } else {
                        idleCycles += 1
                        Thread.sleep(POLL_DELAY_MILLIS)
                    }
                }

                val result = output.toString().trim()
                if (!receivedAnyData || result.isEmpty()) {
                    Log.w(TAG, "ADB command produced no readable output: $command")
                    error("ADB command returned no output: $command")
                }

                Log.d(TAG, "ADB command completed: $command (${result.lineSequence().count()} lines)")
                result
            }
        } finally {
            Log.d(TAG, "Closing ADB stream for command: $command")
            stream.close()
        }
    }

    private fun ensureConnected(manager: PerfViewAdbConnectionManager) {
        if (manager.isConnected) return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Log.d(TAG, "Attempting ADB autoConnect")
                runCatching { manager.autoConnect(context, AUTO_CONNECT_TIMEOUT_MILLIS) }
                    .getOrDefault(false)
                    .also { connected ->
                        Log.d(TAG, "ADB autoConnect result: $connected")
                        if (connected) return
                    }
            }

            val host = AndroidUtils.getHostIpAddress(context)
            Log.d(TAG, "Attempting direct ADB connect to $host:$DEFAULT_ADB_PORT")
            val connected = manager.connect(host, DEFAULT_ADB_PORT)
            if (!connected) {
                Log.w(TAG, "Direct ADB connect returned false for $host:$DEFAULT_ADB_PORT")
                throw IllegalStateException("ADB is not connected on $host:$DEFAULT_ADB_PORT")
            }
            Log.d(TAG, "Direct ADB connect succeeded")
        } catch (error: AdbPairingRequiredException) {
            Log.w(TAG, "ADB pairing is required", error)
            throw IllegalStateException(
                "Wireless debugging needs pairing before Perf View can use ADB."
            )
        } catch (error: Exception) {
            Log.e(TAG, "ADB connection failed", error)
            throw error
        }
    }

    private companion object {
        private const val DEFAULT_ADB_PORT = 5555
        private const val AUTO_CONNECT_TIMEOUT_MILLIS = 2_500L
        private const val POLL_DELAY_MILLIS = 50L
        private const val MAX_IDLE_CYCLES = 20
        private const val TAG = "PerfViewAdb"
    }
}
