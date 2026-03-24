package com.neilturner.perfview.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.neilturner.perfview.data.adb.AdbAccessManager
import com.neilturner.perfview.domain.cpu.CpuUsageResult
import com.neilturner.perfview.domain.cpu.ObserveCpuUsageUseCase
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
    private val observeCpuUsage: ObserveCpuUsageUseCase,
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

    fun accept(intent: PerfViewIntent) {
        when (intent) {
            PerfViewIntent.Load -> {
                if (adbAccessManager.hasGrantedAccess()) {
                    ensureConnectedThenObserve()
                } else {
                    showPermissionRationale()
                }
            }
            PerfViewIntent.RequestAdbAccess -> requestAdbAccess()
            PerfViewIntent.RunInBackgroundClicked -> runInBackground()
            PerfViewIntent.OverlayPermissionResult -> handleOverlayPermissionResult()
            PerfViewIntent.AppOpenedToForeground -> handleAppOpenedToForeground()
        }
    }

    private fun handleAppOpenedToForeground() {
        Log.d(TAG, "App opened to foreground, stopping overlay service")
        _commands.tryEmit(PerfViewCommand.StopBackgroundOverlay)
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

    private fun ensureConnectedThenObserve() {
        _uiState.update {
            it.copy(
                isLoading = true,
                statusMessage = "Checking ADB connection...",
                sourceLabel = "Verifying connection",
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

    private fun runInBackground() {
        if (overlayPermissionManager.canDrawOverlays()) {
            overlayPermissionPollJob?.cancel()
            _uiState.update { it.copy(backgroundActionMessage = null) }
            _commands.tryEmit(PerfViewCommand.StartBackgroundOverlay)
            return
        }

        _uiState.update {
            it.copy(
                backgroundActionMessage =
                    "Allow Perf View to display over other apps so it can keep the top CPU list visible after this screen closes."
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
            _uiState.update { it.copy(backgroundActionMessage = null) }
            _commands.tryEmit(PerfViewCommand.StartBackgroundOverlay)
        } else {
            _uiState.update {
                it.copy(
                    backgroundActionMessage =
                        "Overlay permission was not granted. Perf View needs that permission to stay visible in the background."
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
                    _uiState.update { it.copy(backgroundActionMessage = null) }
                    _commands.emit(PerfViewCommand.StartBackgroundOverlay)
                    return@launch
                }

                if (attempt == OVERLAY_PERMISSION_POLL_ATTEMPTS - 1) {
                    _uiState.update {
                        it.copy(
                            backgroundActionMessage =
                                "Overlay permission was not granted within 20 seconds. You can try again when ready."
                        )
                    }
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
                            statusMessage = "Top process usage via ADB",
                            lastUpdatedLabel = timeFormatter.format(Date(observation.collectedAtMillis)),
                            sourceLabel = "ADB shell",
                            backgroundActionMessage = current.backgroundActionMessage,
                        )
                    }

                    is CpuUsageResult.Unsupported -> _uiState.update {
                        Log.w(TAG, "Observation failed: ${result.message}")
                        if (shouldReturnToPermissionGate(result.message)) {
                            PerfViewViewState(
                                screen = PerfViewScreen.AuthorizationFailed,
                                isLoading = false,
                                isSupported = false,
                                statusMessage = result.message,
                                sourceLabel = "ADB access failed",
                                permissionTitle = "ADB access needs approval",
                                permissionMessage = result.message + " Press the button to reconnect.",
                                permissionButtonLabel = "Retry ADB access",
                            )
                        } else {
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
