package com.neilturner.perfview.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.neilturner.perfview.data.adb.AdbAccessManager
import com.neilturner.perfview.data.cpu.CpuDataSource
import com.neilturner.perfview.domain.cpu.CpuUsageResult
import com.neilturner.perfview.domain.cpu.ObserveCpuUsageUseCase
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PerfViewViewModel(
    private val adbAccessManager: AdbAccessManager,
    private val observeCpuUsage: ObserveCpuUsageUseCase,
) : ViewModel() {
    private val timeFormatter = DateFormat.getTimeInstance(DateFormat.MEDIUM)

    private val _uiState = MutableStateFlow(PerfViewViewState())
    val uiState: StateFlow<PerfViewViewState> = _uiState.asStateFlow()

    private var observeJob: Job? = null

    fun accept(intent: PerfViewIntent) {
        when (intent) {
            PerfViewIntent.Load -> {
                if (adbAccessManager.hasGrantedAccess()) {
                    startObserving()
                } else {
                    showPermissionRationale()
                }
            }
            PerfViewIntent.RequestAdbAccess -> requestAdbAccess()
        }
    }

    private fun showPermissionRationale() {
        observeJob?.cancel()
        _uiState.value = PerfViewViewState(
            screen = PerfViewScreen.PermissionRationale,
            isLoading = false,
            sourceLabel = "ADB access required",
            statusMessage = "Waiting for authorization",
            permissionTitle = "Allow loopback ADB access",
            permissionMessage = "Perf View uses Android's local ADB loopback connection to read process CPU usage from this device. Press the button below, then approve the system debugging prompt.",
            permissionButtonLabel = "Grant ADB access",
        )
    }

    private fun requestAdbAccess() {
        observeJob?.cancel()
        _uiState.update {
            it.copy(
                screen = PerfViewScreen.Authorizing,
                isLoading = true,
                statusMessage = "Waiting for ADB authorization. This can take up to 30 seconds.",
                sourceLabel = "Requesting ADB access",
                permissionTitle = "Waiting for approval",
                permissionMessage = "Approve the debugging prompt on the device to continue.",
                permissionButtonLabel = "Try again",
            )
        }

        viewModelScope.launch {
            runCatching {
                adbAccessManager.requestAccess(timeoutMillis = ADB_REQUEST_TIMEOUT_MILLIS)
            }.onSuccess {
                Log.d(TAG, "ADB authorization succeeded")
                startObserving()
            }.onFailure { error ->
                Log.w(TAG, "ADB authorization failed", error)
                _uiState.update {
                    it.copy(
                        screen = PerfViewScreen.AuthorizationFailed,
                        isLoading = false,
                        isSupported = false,
                        sourceLabel = "ADB access failed",
                        statusMessage = error.message ?: AUTHORIZATION_FAILED_MESSAGE,
                        permissionTitle = "ADB access was not granted",
                        permissionMessage = (error.message ?: AUTHORIZATION_FAILED_MESSAGE) +
                            " Press the button to try again.",
                        permissionButtonLabel = "Retry ADB access",
                        topProcesses = emptyList(),
                    )
                }
            }
        }
    }

    private fun startObserving() {
        observeJob?.cancel()
        Log.d(TAG, "Starting process observation")
        _uiState.value = PerfViewViewState(
            screen = PerfViewScreen.Content,
            isLoading = true,
            sourceLabel = "ADB shell",
            statusMessage = "Connecting to ADB and reading top process usage",
        )

        observeJob = viewModelScope.launch {
            observeCpuUsage().collect { result ->
                when (result) {
                    is CpuUsageResult.Success -> _uiState.update { current ->
                        val observation = result.observation
                        Log.d(TAG, "Observation success with ${observation.topProcesses.size} processes")
                        current.copy(
                            screen = PerfViewScreen.Content,
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
                    }
                }
            }
        }
    }

    private fun buildStatusMessage(dataSource: CpuDataSource): String =
        if (dataSource == CpuDataSource.ADB_SHELL) "Top process usage via ADB" else "Top process usage"

    private fun buildSourceLabel(dataSource: CpuDataSource): String = when (dataSource) {
        CpuDataSource.ADB_SHELL -> "ADB shell"
    }

    private companion object {
        private const val ADB_REQUEST_TIMEOUT_MILLIS = 30_000L
        private const val AUTHORIZATION_FAILED_MESSAGE =
            "Perf View could not get ADB access within 30 seconds."
        private const val TAG = "PerfViewVm"
    }
}
