package com.neilturner.perfview.data.adb

import android.content.Context
import android.util.Log
import io.github.muntashirakon.adb.AdbPairingRequiredException
import io.github.muntashirakon.adb.AdbStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LibAdbAccessManager(
    private val context: Context,
) : AdbAccessManager {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun hasGrantedAccess(): Boolean = preferences.getBoolean(KEY_ACCESS_GRANTED, false)

    override suspend fun requestAccess(timeoutMillis: Long) = withContext(Dispatchers.IO) {
        val manager = PerfViewAdbConnectionManager.getInstance(context)

        try {
            // Keep the existing connection if present so we reuse the same identity,
            // but always run a shell probe before treating access as granted.
            if (!manager.isConnected) {
                connect(manager = manager, timeoutMillis = timeoutMillis)
            }
            verifyShellAccess(manager)
            persistGrantedAccess(true)
        } catch (error: AdbAuthorizationRequiredException) {
            persistGrantedAccess(false)
            throw error
        }
    }

    override suspend fun ensureConnected(timeoutMillis: Long) = withContext(Dispatchers.IO) {
        val manager = PerfViewAdbConnectionManager.getInstance(context)
        try {
            if (!manager.isConnected) {
                connect(manager = manager, timeoutMillis = timeoutMillis)
            }
            verifyShellAccess(manager)
            persistGrantedAccess(true)
        } catch (error: AdbAuthorizationRequiredException) {
            // If authorization was revoked, don't keep telling startup that access
            // was previously granted.
            persistGrantedAccess(false)
            throw error
        } catch (error: Exception) {
            // Don't clear access flag on transient availability errors. The flag
            // still represents prior user consent, not current connection state.
            throw error
        }
    }

    private fun connect(
        manager: PerfViewAdbConnectionManager,
        timeoutMillis: Long,
    ) {
        if (manager.isConnected) return

        try {
            val host = "127.0.0.1"
            Log.d(TAG, "Attempting direct ADB connect to $host:$DEFAULT_ADB_PORT")
            val connected = manager.connect(host, DEFAULT_ADB_PORT)
            if (!connected) {
                Log.w(TAG, "Direct ADB connect returned false for $host:$DEFAULT_ADB_PORT")
                throw AdbUnavailableException(
                    "Perf View could not get ADB access. Make sure wireless debugging is enabled and try again."
                )
            }
            Log.d(TAG, "Direct ADB connect succeeded")
        } catch (error: AdbPairingRequiredException) {
            Log.w(TAG, "ADB pairing is required", error)
            throw AdbAuthorizationRequiredException(
                "Wireless debugging needs pairing before Perf View can use ADB.",
                error,
            )
        } catch (error: SecurityException) {
            Log.w(TAG, "ADB authorization is required", error)
            throw AdbAuthorizationRequiredException(
                "Approve the debugging prompt before Perf View can use ADB.",
                error,
            )
        } catch (error: Exception) {
            Log.e(TAG, "ADB connection failed: ${error.javaClass.simpleName} - ${error.message}", error)
            throw classifyAsAccessException(error)
        }
    }

    private fun verifyShellAccess(manager: PerfViewAdbConnectionManager) {
        val stream = try {
            manager.openStream("shell:echo $PROBE_TOKEN")
        } catch (error: IllegalStateException) {
            throw classifyAsAccessException(error)
        } catch (error: Exception) {
            throw classifyAsAccessException(error)
        }

        try {
            val output = stream.readFully()
            if (PROBE_TOKEN !in output) {
                Log.w(TAG, "ADB probe returned unexpected output: $output")
                throw AdbUnavailableException(
                    "Perf View connected to ADB but could not verify shell access."
                )
            }
            Log.d(TAG, "ADB shell probe succeeded")
        } catch (error: AdbAccessException) {
            throw error
        } catch (error: Exception) {
            throw classifyAsAccessException(error)
        } finally {
            runCatching { stream.close() }
        }
    }

    private fun AdbStream.readFully(): String {
        openInputStream().bufferedReader().use { reader ->
            val output = StringBuilder()
            var idleCycles = 0

            while (idleCycles < MAX_IDLE_CYCLES) {
                if (reader.ready()) {
                    val line = reader.readLine() ?: break
                    output.appendLine(line)
                    idleCycles = 0
                } else {
                    idleCycles += 1
                    Thread.sleep(POLL_DELAY_MILLIS)
                }
            }

            return output.toString().trim()
        }
    }

    private fun classifyAsAccessException(error: Throwable): AdbAccessException {
        if (error is AdbAccessException) {
            return error
        }

        val message = error.message.orEmpty()
        val normalized = message.lowercase()
        return if (
            error is AdbPairingRequiredException ||
            error is SecurityException ||
            "unauthoriz" in normalized ||
            "authoriz" in normalized ||
            "debugging" in normalized ||
            "pair" in normalized
        ) {
            AdbAuthorizationRequiredException(
                if (message.isNotBlank()) message else "Approve the debugging prompt before Perf View can use ADB.",
                error,
            )
        } else {
            AdbUnavailableException(
                if (message.isNotBlank()) message else "Perf View could not connect to ADB right now.",
                error,
            )
        }
    }

    private fun persistGrantedAccess(granted: Boolean) {
        preferences.edit().putBoolean(KEY_ACCESS_GRANTED, granted).apply()
    }

    private fun PerfViewAdbConnectionManager.disconnectIfNeeded() {
        if (!isConnected) return
        runCatching { disconnect() }
    }

    private companion object {
        private const val DEFAULT_ADB_PORT = 5555
        private const val PREFS_NAME = "perfview_adb_access"
        private const val KEY_ACCESS_GRANTED = "adb_access_granted"
        private const val PROBE_TOKEN = "perfview_probe_ok"
        private const val POLL_DELAY_MILLIS = 50L
        private const val MAX_IDLE_CYCLES = 20
        private const val TAG = "PerfViewAdbAccess"
    }
}
