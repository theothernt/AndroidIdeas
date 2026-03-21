package com.neilturner.perfview.data.adb

import android.content.Context
import android.os.Build
import android.util.Log
import io.github.muntashirakon.adb.AdbPairingRequiredException
import io.github.muntashirakon.adb.android.AndroidUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LibAdbAccessManager(
    private val context: Context,
) : AdbAccessManager {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun hasGrantedAccess(): Boolean = preferences.getBoolean(KEY_ACCESS_GRANTED, false)

    override suspend fun requestAccess(timeoutMillis: Long) = withContext(Dispatchers.IO) {
        val manager = PerfViewAdbConnectionManager.getInstance(context)
        manager.disconnectIfNeeded()
        connect(manager = manager, timeoutMillis = timeoutMillis)
        persistGrantedAccess(true)
    }

    override suspend fun ensureConnected(timeoutMillis: Long) = withContext(Dispatchers.IO) {
        val manager = PerfViewAdbConnectionManager.getInstance(context)
        if (manager.isConnected) return@withContext

        try {
            connect(manager = manager, timeoutMillis = timeoutMillis)
            persistGrantedAccess(true)
        } catch (error: Exception) {
            persistGrantedAccess(false)
            throw error
        }
    }

    private fun connect(
        manager: PerfViewAdbConnectionManager,
        timeoutMillis: Long,
    ) {
        if (manager.isConnected) return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Log.d(TAG, "Attempting ADB autoConnect with timeout=$timeoutMillis")
                runCatching { manager.autoConnect(context, timeoutMillis) }
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
                throw IllegalStateException(
                    "Perf View could not get ADB access. Approve the loopback debugging prompt and try again."
                )
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
        private const val TAG = "PerfViewAdbAccess"
    }
}
