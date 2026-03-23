package com.neilturner.perfview.domain.cpu

import com.neilturner.perfview.data.cpu.CpuRepository
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeout

class ObserveCpuUsageUseCase(
    private val cpuRepository: CpuRepository,
) {
    operator fun invoke(intervalMillis: Long = DEFAULT_INTERVAL_MILLIS): Flow<CpuUsageResult> = flow {
        try {
            while (currentCoroutineContext().isActive) {
                val snapshot = withTimeout(SNAPSHOT_TIMEOUT_MILLIS) {
                    cpuRepository.readSnapshot()
                }
                emit(
                    CpuUsageResult.Success(
                        observation = CpuObservation(
                            percent = snapshot.totalCpuPercent,
                            topProcesses = snapshot.topProcesses,
                            collectedAtMillis = snapshot.timestampMillis,
                        )
                    )
                )
                delay(intervalMillis)
            }
        } catch (error: Exception) {
            emit(
                CpuUsageResult.Unsupported(
                    message = error.message ?: DEFAULT_ERROR_MESSAGE
                )
            )
        }
    }

    private companion object {
        private const val DEFAULT_INTERVAL_MILLIS = 1_000L
        private const val SNAPSHOT_TIMEOUT_MILLIS = 4_000L
        private const val DEFAULT_ERROR_MESSAGE =
            "Unable to connect to ADB. Enable wireless debugging or adb tcpip 5555 first."
    }
}

sealed interface CpuUsageResult {
    data class Success(
        val observation: CpuObservation,
    ) : CpuUsageResult

    data class Unsupported(
        val message: String,
    ) : CpuUsageResult
}
