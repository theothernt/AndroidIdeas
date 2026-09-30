package com.neilturner.perfview.ui.dashboard

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neilturner.perfview.data.adb.AdbAuthorizationRequiredException
import com.neilturner.perfview.data.adb.AdbConnectionGate
import com.neilturner.perfview.data.adb.AdbUnavailableException
import com.neilturner.perfview.domain.cpu.CpuMonitor
import com.neilturner.perfview.domain.cpu.model.CpuUsageResult
import com.neilturner.perfview.overlay.OverlayPermissionManager
import com.neilturner.perfview.ui.dashboard.contract.PerfViewCommand
import com.neilturner.perfview.ui.dashboard.contract.PerfViewIntent
import com.neilturner.perfview.ui.dashboard.contract.PerfViewViewState
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
    private val adbConnectionGate: AdbConnectionGate,
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
    private var connectJob: Job? = null
    private var overlayPermissionPollJob: Job? = null
    private var isMonitoringActive = false
    private var isHandoffRequested = false

    fun accept(intent: PerfViewIntent) {
        when (intent) {
            PerfViewIntent.Load -> startConnecting()
            PerfViewIntent.RunInBackgroundClicked -> runInBackground()
            PerfViewIntent.OverlayPermissionResult -> handleOverlayPermissionResult()
            PerfViewIntent.ExitApp -> _commands.tryEmit(PerfViewCommand.ExitApp)
        }
    }

    /**
     * Whether this is a resume over data that is already on screen. ON_START fires every time
     * the activity comes back to the foreground, so a plain reload would replace a populated
     * process list with a "Connecting to ADB..." placeholder each time.
     */
    private fun hasLiveDataToPreserve(): Boolean =
        isMonitoringActive && cpuMonitor.results.value is CpuUsageResult.Success

    private fun startConnecting() {
        val isResume = hasLiveDataToPreserve()

        // A handshake left in flight across a stop would race the one started here, so it is
        // always cancelled. Only the visible reset is conditional.
        connectJob?.cancel()
        isHandoffRequested = false

        if (!isResume) {
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
        }

        connectJob = viewModelScope.launch {
            adbConnectionGate.ensureAuthorized()
                .onSuccess {
                    Log.d(TAG, "ADB connection established")
                    startObserving()
                }
                .onFailure { error ->
                    Log.w(TAG, "ADB connect failed", error)
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
                // Only forget the session when the failure looks like the connection itself
                // going away, for example wireless debugging being switched off. A single failed
                // read is transient, and discarding the session on one would drop the user back
                // to the authorization gate on the next foreground pass.
                if (result.message.containsAny(CONNECTION_LOST_MARKERS)) {
                    adbConnectionGate.markUnauthorized()
                }
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

    /**
* Runs the overlay only if the permission is held.
     *
     * The grant is settled on the intro screen and re-checked here because it is revocable. A
     * refusal is shown inline rather than as a modal dialog: interrupting the dashboard with a
     * permission prompt is exactly what moving these asks onto the intro screen was for.
     */
    private fun runInBackground() {
        if (!overlayPermissionManager.canDrawOverlays()) {
            Log.d(TAG, "Overlay permission is not granted, staying on the dashboard")
            _uiState.update { state ->
                state.copy(
                    dashboardState = DashboardUiState(
                        sourceLabel = "Permission needed",
                        statusLabel = OVERLAY_PERMISSION_MESSAGE,
                        isPolling = state.dashboardState?.isPolling == true,
                        content = DashboardContentState.Loading(
                            message = OVERLAY_PERMISSION_MESSAGE,
                        ),
                    )
                )
            }
            return
        }

        overlayPermissionPollJob?.cancel()
        requestOverlayHandoff()
    }

    private fun handleOverlayPermissionResult() {
        if (overlayPermissionManager.canDrawOverlays()) {
            overlayPermissionPollJob?.cancel()
            requestOverlayHandoff()
        }
    }

    /**
     * The settings activity result and the permission poll can both observe the grant,
     * so the handoff is latched to fire exactly once per foreground session. A second
     * emission would try to launch the notification permission dialog over itself.
     */
    private fun requestOverlayHandoff() {
        if (isHandoffRequested) return
        isHandoffRequested = true
        _commands.tryEmit(PerfViewCommand.StartBackgroundOverlay)
    }

    private fun startOverlayPermissionPolling() {
        overlayPermissionPollJob?.cancel()
        overlayPermissionPollJob = viewModelScope.launch {
            repeat(OVERLAY_PERMISSION_POLL_ATTEMPTS) {
                delay(OVERLAY_PERMISSION_POLL_INTERVAL_MILLIS)
                if (overlayPermissionManager.canDrawOverlays()) {
                    requestOverlayHandoff()
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
        connectJob?.cancel()
        observeJob?.cancel()
        overlayPermissionPollJob?.cancel()
    }

    private fun String.containsAny(markers: List<String>): Boolean {
        val lower = lowercase()
        return markers.any { lower.contains(it) }
    }

    private companion object {
        /**
         * Substrings that indicate the ADB connection itself is gone, as opposed to a single
         * read that failed. Matched case-insensitively against the failure message.
         */
        private val CONNECTION_LOST_MARKERS = listOf(
            "connection refused",
            "connection reset",
            "broken pipe",
            "connection closed",
            "not connected",
            "device offline",
            "wireless debugging",
            "protocol fault",
            "closed",
        )
        private const val OVERLAY_PERMISSION_POLL_INTERVAL_MILLIS = 1_000L
        private const val OVERLAY_PERMISSION_POLL_ATTEMPTS = 20
        private const val ADB_UNAVAILABLE_MESSAGE =
            "Could not connect to ADB. Enable wireless debugging and try again."
        private const val OVERLAY_PERMISSION_MESSAGE =
            "Grant overlay access on the intro screen to run in the background"
        private const val TAG = "PerfViewVm"
    }
}
