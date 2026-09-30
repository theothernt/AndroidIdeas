package com.neilturner.perfview.domain.cpu

import android.util.Log
import com.neilturner.perfview.data.cpu.model.CpuUsageSnapshot
import com.neilturner.perfview.domain.cpu.model.CpuObservation
import com.neilturner.perfview.domain.cpu.model.CpuUsageResult
import com.neilturner.perfview.domain.cpu.repository.CpuRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class CpuMonitor(
    private val cpuRepository: CpuRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _results = MutableStateFlow<CpuUsageResult?>(null)

    val results: StateFlow<CpuUsageResult?> = _results.asStateFlow()

    private var pollJob: Job? = null
    private var activeClients = 0

    @Synchronized
    fun acquire() {
        activeClients += 1
        if (pollJob?.isActive == true) return

        pollJob = scope.launch {
            while (isActive) {
                var errorMessage: String? = null

                // Cancellation is a lifecycle event, not a device failure. runCatching would
                // otherwise capture the CancellationException raised when this poll is torn down
                // and report it as Unsupported, which made a healthy session look unauthorized
                // and sent the next foreground pass back through the authorization gate.
                val failure = try {
                    withTimeout(SNAPSHOT_TIMEOUT_MILLIS) {
                        cpuRepository.readSnapshot()
                    }.also {
                        errorMessage = null
                    }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: Exception) {
                    errorMessage = error.message ?: DEFAULT_ERROR_MESSAGE
                    Log.w(TAG, "Snapshot read failed: $errorMessage", error)
                    null
                }

                val result = if (failure != null) {
                    CpuUsageResult.Success(
                        observation = CpuObservation(
                            percent = failure.totalCpuPercent,
                            topProcesses = failure.topProcesses,
                            collectedAtMillis = failure.timestampMillis,
                        )
                    )
                } else {
                    CpuUsageResult.Unsupported(
                        message = errorMessage ?: DEFAULT_ERROR_MESSAGE,
                    )
                }

                _results.value = result
                delay(POLL_INTERVAL_MILLIS)
            }
        }
    }

    @Synchronized
    fun release() {
        if (activeClients > 0) {
            activeClients -= 1
        }
        if (activeClients == 0) {
            pollJob?.cancel()
            pollJob = null
            // Drop the last snapshot so a client that re-acquires does not render
            // a previous session's processes as if they were current.
            _results.value = null
        }
    }

    fun close() {
        scope.cancel()
    }

    private companion object {
        private const val TAG = "PerfViewCpuMonitor"
        private const val POLL_INTERVAL_MILLIS = 1_000L
        private const val SNAPSHOT_TIMEOUT_MILLIS = 10_000L
        private const val DEFAULT_ERROR_MESSAGE =
            "Unable to connect to ADB. Enable wireless debugging or adb tcpip 5555 first."
    }
}
