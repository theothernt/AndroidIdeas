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

        try {
            adbAccessManager.ensureConnected()
        } catch (exception: Exception) {
            Log.e(TAG, "Failed to ensure ADB connection", exception)
            throw AdbShellException.ConnectionException(command, exception)
        }

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
                    throw AdbShellException.NoOutputException(command)
                }

                Log.d(TAG, "ADB command completed: $command (${result.lineSequence().count()} lines)")
                result
            }
        } catch (exception: AdbShellException) {
            throw exception
        } catch (exception: Exception) {
            Log.e(TAG, "Unexpected error during ADB command execution", exception)
            throw AdbShellException.UnknownException(
                command = command,
                message = exception.message ?: "Unknown error during command execution",
                cause = exception
            )
        } finally {
            Log.d(TAG, "Closing ADB stream for command: $command")
            runCatching { stream.close() }
        }
    }

    private companion object {
        private const val POLL_DELAY_MILLIS = 50L
        private const val MAX_IDLE_CYCLES = 20
        private const val TAG = "PerfViewAdb"
    }
}
