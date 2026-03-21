package com.neilturner.perfview.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.neilturner.perfview.data.cpu.CpuDataSource
import com.neilturner.perfview.domain.cpu.CpuUsageResult
import com.neilturner.perfview.domain.cpu.ObserveCpuUsageUseCase
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PerfViewViewModel(
    private val observeCpuUsage: ObserveCpuUsageUseCase,
) : ViewModel() {
    private val timeFormatter = DateFormat.getTimeInstance(DateFormat.MEDIUM)

    private val _uiState = MutableStateFlow(PerfViewViewState())
    val uiState: StateFlow<PerfViewViewState> = _uiState.asStateFlow()

    private var observeJob: Job? = null
    private var autoRetryJob: Job? = null
    private var autoRetryCount = 0

    fun accept(intent: PerfViewIntent) {
        when (intent) {
            PerfViewIntent.Load -> {
                autoRetryCount = 0
                startObserving()
            }
        }
    }

    private fun startObserving() {
        observeJob?.cancel()
        autoRetryJob?.cancel()
        Log.d(TAG, "Starting process observation")
        _uiState.value = PerfViewViewState(
            isLoading = true,
            sourceLabel = "ADB shell",
            statusMessage = "Connecting to ADB and reading top process usage",
        )

        observeJob = viewModelScope.launch {
            observeCpuUsage().collect { result ->
                when (result) {
                    is CpuUsageResult.Success -> _uiState.update { current ->
                        autoRetryJob?.cancel()
                        autoRetryCount = 0
                        val observation = result.observation
                        Log.d(TAG, "Observation success with ${observation.topProcesses.size} processes")
                        current.copy(
                            isLoading = false,
                            topProcesses = observation.topProcesses,
                            isSupported = true,
                            statusMessage = buildStatusMessage(observation.dataSource),
                            lastUpdatedLabel = timeFormatter.format(Date(observation.collectedAtMillis)),
                            sourceLabel = buildSourceLabel(observation.dataSource),
                        )
                    }

                    is CpuUsageResult.Unsupported -> _uiState.update {
                        Log.w(TAG, "Observation failed: ${result.message}")
                        it.copy(
                            isLoading = false,
                            isSupported = false,
                            statusMessage = result.message,
                            topProcesses = emptyList(),
                            sourceLabel = "Unavailable",
                        )
                    }.also {
                        scheduleAutoRetryIfNeeded()
                    }
                }
            }
        }
    }

    private fun scheduleAutoRetryIfNeeded() {
        if (autoRetryCount >= MAX_AUTO_RETRIES) return

        autoRetryJob?.cancel()
        autoRetryJob = viewModelScope.launch {
            autoRetryCount += 1
            Log.d(TAG, "Scheduling auto retry #$autoRetryCount")
            _uiState.update {
                it.copy(
                    statusMessage = "Waiting for ADB authorization, retrying automatically",
                    sourceLabel = "ADB shell",
                )
            }
            delay(AUTO_RETRY_DELAY_MILLIS)
            startObserving()
        }
    }

    private fun buildStatusMessage(dataSource: CpuDataSource): String =
        if (dataSource == CpuDataSource.ADB_SHELL) "Top process usage via ADB" else "Top process usage"

    private fun buildSourceLabel(dataSource: CpuDataSource): String = when (dataSource) {
        CpuDataSource.ADB_SHELL -> "ADB shell"
    }

    private companion object {
        private const val MAX_AUTO_RETRIES = Int.MAX_VALUE
        private const val AUTO_RETRY_DELAY_MILLIS = 3_000L
        private const val TAG = "PerfViewVm"
    }
}
