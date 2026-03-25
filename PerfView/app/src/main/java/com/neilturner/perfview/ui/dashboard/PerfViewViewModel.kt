package com.neilturner.perfview.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.neilturner.perfview.data.adb.AdbAccessManager
import com.neilturner.perfview.domain.cpu.CpuMonitor
import com.neilturner.perfview.domain.cpu.CpuUsageResult
import com.neilturner.perfview.overlay.OverlayPermissionManager
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PerfViewViewModel(
    private val adbAccessManager: AdbAccessManager,
    private val overlayPermissionManager: OverlayPermissionManager,
    private val cpuMonitor: CpuMonitor,
) : ViewModel() {
    private val timeFormatter = DateFormat.getTimeInstance(DateFormat.MEDIUM)

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
            PerfViewIntent.Load -> {
                if (adbAccessManager.hasGrantedAccess()) {
                    ensureConnectedThenObserve()
                } else {
                    showPermissionRationale()
                }
            }
            PerfViewIntent.ResumeObserving -> {
                // App came back to foreground, resume polling
                startObserving()
            }
            PerfViewIntent.RequestAdbAccess -> requestAdbAccess()
            PerfViewIntent.RunInBackgroundClicked -> runInBackground()
            PerfViewIntent.OverlayPermissionResult -> handleOverlayPermissionResult()
            PerfViewIntent.AppOpenedToForeground -> handleAppOpenedToForeground()
            PerfViewIntent.AppBackgrounded -> handleAppBackgrounded()
        }
    }

    private fun handleAppOpenedToForeground() {
        Log.d(TAG, "App opened to foreground, stopping overlay service")
        _commands.tryEmit(PerfViewCommand.StopBackgroundOverlay)
    }

    private fun handleAppBackgrounded() {
        Log.d(TAG, "App backgrounded, stopping CPU polling")
        stopMonitoring()
        observeJob?.cancel()
        observeJob = null
    }

    private fun showPermissionRationale() {
        stopMonitoring()
        observeJob?.cancel()
        _uiState.value = PerfViewViewState(
            permissionState = PermissionUiState(
                phase = PermissionPhase.Rationale,
                title = "Allow loopback ADB access",
                message = "Perf View uses Android's local ADB loopback connection to read process CPU usage from this device. Press the button below, then approve the system debugging prompt.",
                buttonLabel = "Grant ADB access",
                detailMessage = "ADB access required",
            ),
        )
    }

    private fun ensureConnectedThenObserve() {
        _uiState.update {
            it.copy(
                permissionState = null,
                dashboardState = DashboardUiState(
                    sourceLabel = "Verifying connection",
                    statusLabel = "Checking ADB connection...",
                    content = DashboardContentState.Loading(
                        message = "Checking ADB connection...",
                    ),
                ),
            )
        }

        viewModelScope.launch {
            runCatching {
                adbAccessManager.ensureConnected(timeoutMillis = ADB_REQUEST_TIMEOUT_MILLIS)
            }.onSuccess {
                Log.d(TAG, "ADB connection verified")
                startObserving()
            }.onFailure { error ->
                Log.w(TAG, "ADB connection check failed", error)
                // Connection failed but user previously granted access.
                // Try to start observing anyway - the actual error will be shown
                // if the ADB commands fail.
                startObserving()
            }
        }
    }

    private fun requestAdbAccess() {
        stopMonitoring()
        observeJob?.cancel()
        _uiState.update {
            it.copy(
                permissionState = PermissionUiState(
                    phase = PermissionPhase.Authorizing,
                    title = "Waiting for approval",
                    message = "Approve the debugging prompt on the device to continue.",
                    buttonLabel = "Try again",
                    detailMessage = "Waiting for ADB authorization. This can take up to 30 seconds.",
                ),
                dashboardState = null,
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
                        permissionState = PermissionUiState(
                            phase = PermissionPhase.Failed,
                            title = "ADB access was not granted",
                            message = (error.message ?: AUTHORIZATION_FAILED_MESSAGE) +
                                " Press the button to try again.",
                            buttonLabel = "Retry ADB access",
                            detailMessage = error.message ?: AUTHORIZATION_FAILED_MESSAGE,
                        ),
                        dashboardState = null,
                    )
                }
            }
        }
    }

    private fun runInBackground() {
        if (overlayPermissionManager.canDrawOverlays()) {
            overlayPermissionPollJob?.cancel()
            _uiState.update {
                it.copy(backgroundActionState = BackgroundActionUiState())
            }
            _commands.tryEmit(PerfViewCommand.StartBackgroundOverlay)
            return
        }

        _uiState.update {
            it.copy(
                backgroundActionState = BackgroundActionUiState(
                    backgroundActionMessage =
                        "Allow Perf View to display over other apps so it can keep the top CPU list visible after this screen closes."
                )
            )
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
            _uiState.update {
                it.copy(backgroundActionState = BackgroundActionUiState())
            }
            _commands.tryEmit(PerfViewCommand.StartBackgroundOverlay)
        } else {
            _uiState.update {
                it.copy(
                    backgroundActionState = BackgroundActionUiState(
                        backgroundActionMessage =
                            "Overlay permission was not granted. Perf View needs that permission to stay visible in the background."
                    )
                )
            }
        }
    }

    private fun startOverlayPermissionPolling() {
        overlayPermissionPollJob?.cancel()
        overlayPermissionPollJob = viewModelScope.launch {
            repeat(OVERLAY_PERMISSION_POLL_ATTEMPTS) { attempt ->
                delay(OVERLAY_PERMISSION_POLL_INTERVAL_MILLIS)
                if (overlayPermissionManager.canDrawOverlays()) {
                    Log.d(TAG, "Overlay permission granted while polling")
                    _uiState.update {
                        it.copy(backgroundActionState = BackgroundActionUiState())
                    }
                    _commands.emit(PerfViewCommand.StartBackgroundOverlay)
                    return@launch
                }

                if (attempt == OVERLAY_PERMISSION_POLL_ATTEMPTS - 1) {
                    _uiState.update {
                        it.copy(
                            backgroundActionState = BackgroundActionUiState(
                                backgroundActionMessage =
                                    "Overlay permission was not granted within 20 seconds. You can try again when ready."
                            )
                        )
                    }
                }
            }
        }
    }

    private fun startObserving() {
        observeJob?.cancel()
        startMonitoring()
        Log.d(TAG, "Starting process observation")
        cpuMonitor.results.value?.let(::applyCpuResult) ?: run {
            _uiState.value = PerfViewViewState(
                permissionState = null,
                dashboardState = DashboardUiState(
                    sourceLabel = "ADB shell",
                    statusLabel = "Connecting to ADB and reading top process usage",
                    content = DashboardContentState.Loading(
                        message = "Connecting to ADB and reading top process usage",
                    ),
                ),
                backgroundActionState = _uiState.value.backgroundActionState,
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
            is CpuUsageResult.Success -> _uiState.update { current ->
                val observation = result.observation
                Log.d(TAG, "Observation success with ${observation.topProcesses.size} processes")
                current.copy(
                    permissionState = null,
                    dashboardState = DashboardUiState(
                        sourceLabel = "ADB shell",
                        statusLabel = "Top process usage via ADB",
                        lastUpdatedLabel = timeFormatter.format(Date(observation.collectedAtMillis)),
                        content = if (observation.topProcesses.isEmpty()) {
                            DashboardContentState.Empty(
                                message = "No active process usage was returned from ADB.",
                            )
                        } else {
                            DashboardContentState.Data(
                                processes = observation.topProcesses,
                            )
                        },
                    ),
                )
            }

            is CpuUsageResult.Unsupported -> _uiState.update {
                Log.w(TAG, "Observation failed: ${result.message}")
                if (shouldReturnToPermissionGate(result.message)) {
                    PerfViewViewState(
                        permissionState = PermissionUiState(
                            phase = PermissionPhase.Failed,
                            title = "ADB access needs approval",
                            message = result.message + " Press the button to reconnect.",
                            buttonLabel = "Retry ADB access",
                            detailMessage = result.message,
                        ),
                        backgroundActionState = it.backgroundActionState,
                    )
                } else {
                    it.copy(
                        permissionState = null,
                        dashboardState = DashboardUiState(
                            sourceLabel = "Unavailable",
                            statusLabel = result.message,
                            content = DashboardContentState.Unsupported(
                                message = result.message,
                            ),
                        ),
                    )
                }
            }
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

    private fun shouldReturnToPermissionGate(message: String): Boolean {
        val normalized = message.lowercase()
        return "adb" in normalized || "debugging" in normalized || "authoriz" in normalized
    }

    private companion object {
        private const val ADB_REQUEST_TIMEOUT_MILLIS = 30_000L
        private const val OVERLAY_PERMISSION_POLL_INTERVAL_MILLIS = 1_000L
        private const val OVERLAY_PERMISSION_POLL_ATTEMPTS = 20
        private const val AUTHORIZATION_FAILED_MESSAGE =
            "Perf View could not get ADB access within 30 seconds."
        private const val TAG = "PerfViewVm"
    }
}
