package com.neilturner.perfview.data.adb

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import io.github.muntashirakon.adb.AdbPairingRequiredException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.NetworkInterface

class LibAdbAccessManager(
    private val context: Context,
) : AdbAccessManager {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun hasGrantedAccess(): Boolean = preferences.getBoolean(KEY_ACCESS_GRANTED, false)

    override suspend fun requestAccess(timeoutMillis: Long) = withContext(Dispatchers.IO) {
        val manager = PerfViewAdbConnectionManager.getInstance(context)
        
        // Don't disconnect - keep existing connection if present to avoid
        // triggering ADB authorization dialog for a "new" connection
        if (!manager.isConnected) {
            connect(manager = manager, timeoutMillis = timeoutMillis)
        }
        persistGrantedAccess(true)
    }

    override suspend fun ensureConnected(timeoutMillis: Long) = withContext(Dispatchers.IO) {
        val manager = PerfViewAdbConnectionManager.getInstance(context)
        if (manager.isConnected) return@withContext

        try {
            connect(manager = manager, timeoutMillis = timeoutMillis)
            persistGrantedAccess(true)
        } catch (error: Exception) {
            // Don't clear access flag on transient errors (ADB server restart,
            // wireless debugging toggled, etc.). The flag represents user consent,
            // not connection state. Connection failures are handled by the caller.
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
                throw IllegalStateException(
                    "Perf View could not get ADB access. Make sure wireless debugging is enabled and try again."
                )
            }
            Log.d(TAG, "Direct ADB connect succeeded")
        } catch (error: AdbPairingRequiredException) {
            Log.w(TAG, "ADB pairing is required", error)
            throw IllegalStateException(
                "Wireless debugging needs pairing before Perf View can use ADB."
            )
        } catch (error: Exception) {
            Log.e(TAG, "ADB connection failed: ${error.javaClass.simpleName} - ${error.message}", error)
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
