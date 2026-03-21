package com.neilturner.perfview.data.adb

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LibAdbShellClient(
    private val context: Context,
    private val adbAccessManager: AdbAccessManager,
) : AdbShellClient {
    override suspend fun run(command: String): String = withContext(Dispatchers.IO) {
        Log.d(TAG, "ADB command requested: $command")
        adbAccessManager.ensureConnected()
        val manager = PerfViewAdbConnectionManager.getInstance(context)

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

    private companion object {
        private const val POLL_DELAY_MILLIS = 50L
        private const val MAX_IDLE_CYCLES = 20
        private const val TAG = "PerfViewAdb"
    }
}
