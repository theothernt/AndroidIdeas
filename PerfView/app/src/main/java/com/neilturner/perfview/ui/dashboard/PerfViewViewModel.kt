package com.neilturner.perfview.ui.dashboard

import android.app.Activity
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neilturner.perfview.data.adb.AdbAccessManager
import com.neilturner.perfview.data.adb.AdbAuthorizationRequiredException
import com.neilturner.perfview.data.adb.AdbUnavailableException
import com.neilturner.perfview.domain.cpu.CpuMonitor
import com.neilturner.perfview.domain.cpu.model.CpuUsageResult
import com.neilturner.perfview.overlay.OverlayPermissionManager
import com.neilturner.perfview.ui.dashboard.contract.PerfViewCommand
import com.neilturner.perfview.ui.dashboard.contract.PerfViewIntent
import com.neilturner.perfview.ui.dashboard.contract.PerfViewViewState
import com.neilturner.perfview.ui.dashboard.contract.BackgroundActionUiState
import com.neilturner.perfview.ui.dashboard.contract.DashboardContentState
import com.neilturner.perfview.ui.dashboard.contract.DashboardUiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive

class PerfViewViewModel(
    private val adbAccessManager: AdbAccessManager,
    private val overlayPermissionManager: OverlayPermissionManager,
    private val cpuMonitor: CpuMonitor,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PerfViewViewState())
    val uiState: StateFlow<PerfViewViewState> = _uiState.asStateFlow()

    private val _commands = MutableSharedFlow<PerfViewCommand>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val commands = _commands.asSharedFlow()

    private var observeJob: Job? = null
    private var overlayPermissionPollJob: Job? = null
    private var isMonitoringActive = false

    fun accept(intent: PerfViewIntent) {
        when (intent) {
            PerfViewIntent.Load -> startConnecting()
            PerfViewIntent.RunInBackgroundClicked -> runInBackground()
            PerfViewIntent.OverlayPermissionResult -> handleOverlayPermissionResult()
            PerfViewIntent.ExitApp -> _commands.tryEmit(PerfViewCommand.ExitApp)
        }
    }

    private fun startConnecting() {
        stopMonitoring()
        observeJob?.cancel()

        _uiState.value = PerfViewViewState(
            dashboardState = DashboardUiState(
                sourceLabel = "Connecting...",
                statusLabel = "Establishing ADB connection",
                content = DashboardContentState.Loading(
                    message = "Connecting to ADB...",
                ),
            ),
        )

        viewModelScope.launch {
            runCatching {
                adbAccessManager.requestAccess(timeoutMillis = ADB_REQUEST_TIMEOUT_MILLIS)
            }.onSuccess {
                Log.d(TAG, "ADB connection established")
                startObserving()
            }.onFailure { error ->
                Log.w(TAG, "requestAccess failed", error)
                showAdbError(error)
            }
        }
    }

    private fun startObserving() {
        observeJob?.cancel()
        startMonitoring()

        cpuMonitor.results.value?.let(::applyCpuResult) ?: run {
            _uiState.value = PerfViewViewState(
                dashboardState = DashboardUiState(
                    sourceLabel = "ADB shell",
                    statusLabel = "Reading process usage",
                    isPolling = true,
                    content = DashboardContentState.Loading(
                        message = "Reading top process usage",
                    ),
                ),
            )
        }

        observeJob = viewModelScope.launch {
            cpuMonitor.results.collect { result ->
                result?.let(::applyCpuResult)
            }
        }
    }

    private fun applyCpuResult(result: CpuUsageResult) {
        when (result) {
            is CpuUsageResult.Success -> _uiState.update {
                val observation = result.observation
                it.copy(
                    dashboardState = DashboardUiState(
                        sourceLabel = "ADB shell",
                        statusLabel = "Top process usage via ADB",
                        isPolling = true,
                        content = if (observation.topProcesses.isEmpty()) {
                            DashboardContentState.Empty(message = "No active processes")
                        } else {
                            DashboardContentState.Data(processes = observation.topProcesses)
                        },
                    ),
                )
            }

            is CpuUsageResult.Unsupported -> _uiState.update {
                it.copy(
                    dashboardState = DashboardUiState(
                        sourceLabel = "Unavailable",
                        statusLabel = result.message,
                        isPolling = false,
                        content = DashboardContentState.Unsupported(message = result.message),
                    ),
                )
            }
        }
    }

    private fun runInBackground() {
        if (overlayPermissionManager.canDrawOverlays()) {
            overlayPermissionPollJob?.cancel()
            _uiState.update { it.copy(backgroundActionState = BackgroundActionUiState()) }
            _commands.tryEmit(PerfViewCommand.StartBackgroundOverlay)
            return
        }

        _commands.tryEmit(
            PerfViewCommand.OpenOverlayPermissionSettings(
                intent = overlayPermissionManager.createPermissionIntent()
            )
        )
        startOverlayPermissionPolling()
    }

    private fun handleOverlayPermissionResult() {
        if (overlayPermissionManager.canDrawOverlays()) {
            overlayPermissionPollJob?.cancel()
            _uiState.update { it.copy(backgroundActionState = BackgroundActionUiState()) }
            _commands.tryEmit(PerfViewCommand.StartBackgroundOverlay)
        }
    }

    private fun startOverlayPermissionPolling() {
        overlayPermissionPollJob?.cancel()
        overlayPermissionPollJob = viewModelScope.launch {
            repeat(OVERLAY_PERMISSION_POLL_ATTEMPTS) {
                delay(OVERLAY_PERMISSION_POLL_INTERVAL_MILLIS)
                if (overlayPermissionManager.canDrawOverlays()) {
                    _uiState.update { it.copy(backgroundActionState = BackgroundActionUiState()) }
                    _commands.emit(PerfViewCommand.StartBackgroundOverlay)
                    return@launch
                }
            }
        }
    }

    private fun showAdbError(error: Throwable) {
        val message = when (error) {
            is AdbAuthorizationRequiredException -> "ADB access needs approval"
            is AdbUnavailableException -> error.message ?: ADB_UNAVAILABLE_MESSAGE
            else -> error.message ?: ADB_UNAVAILABLE_MESSAGE
        }
        _uiState.update {
            it.copy(
                dashboardState = DashboardUiState(
                    sourceLabel = "Unavailable",
                    statusLabel = message,
                    isPolling = false,
                    content = DashboardContentState.Unsupported(message = message),
                ),
            )
        }
    }

    private fun startMonitoring() {
        if (isMonitoringActive) return
        cpuMonitor.acquire()
        isMonitoringActive = true
    }

    private fun stopMonitoring() {
        if (!isMonitoringActive) return
        cpuMonitor.release()
        isMonitoringActive = false
    }

    override fun onCleared() {
        stopMonitoring()
        super.onCleared()
    }

    private companion object {
        private const val ADB_REQUEST_TIMEOUT_MILLIS = 30_000L
        private const val OVERLAY_PERMISSION_POLL_INTERVAL_MILLIS = 1_000L
        private const val OVERLAY_PERMISSION_POLL_ATTEMPTS = 20
        private const val ADB_UNAVAILABLE_MESSAGE =
            "Could not connect to ADB. Enable wireless debugging and try again."
        private const val TAG = "PerfViewVm"
    }
}
