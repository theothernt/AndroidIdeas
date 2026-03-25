package com.neilturner.perfview.domain.cpu

import com.neilturner.perfview.data.cpu.CpuRepository
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
                val result = runCatching {
                    withTimeout(SNAPSHOT_TIMEOUT_MILLIS) {
                        cpuRepository.readSnapshot()
                    }
                }.fold(
                    onSuccess = { snapshot ->
                        CpuUsageResult.Success(
                            observation = CpuObservation(
                                percent = snapshot.totalCpuPercent,
                                topProcesses = snapshot.topProcesses,
                                collectedAtMillis = snapshot.timestampMillis,
                            )
                        )
                    },
                    onFailure = { error ->
                        CpuUsageResult.Unsupported(
                            message = error.message ?: DEFAULT_ERROR_MESSAGE,
                        )
                    }
                )

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
        }
    }

    fun close() {
        scope.cancel()
    }

    private companion object {
        private const val POLL_INTERVAL_MILLIS = 1_000L
        private const val SNAPSHOT_TIMEOUT_MILLIS = 10_000L
        private const val DEFAULT_ERROR_MESSAGE =
            "Unable to connect to ADB. Enable wireless debugging or adb tcpip 5555 first."
    }
}
