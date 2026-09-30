package com.neilturner.perfview.ui.intro

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neilturner.perfview.data.adb.AdbConnectionGate
import com.neilturner.perfview.domain.cpu.CpuMonitor
import com.neilturner.perfview.domain.cpu.model.CpuUsageResult
import com.neilturner.perfview.platform.NotificationPermissionChecker
import com.neilturner.perfview.platform.OverlayAccessChecker
import com.neilturner.perfview.ui.intro.contract.ChecklistAction
import com.neilturner.perfview.ui.intro.contract.ChecklistItem
import com.neilturner.perfview.ui.intro.contract.ChecklistStatus
import com.neilturner.perfview.ui.intro.contract.IntroChecklistItem
import com.neilturner.perfview.ui.intro.contract.IntroCommand
import com.neilturner.perfview.ui.intro.contract.IntroIntent
import com.neilturner.perfview.ui.intro.contract.IntroViewState
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Readiness gate shown before the dashboard.
 *
 * Everything the app needs is settled here, in one place, so nothing asks for a permission later
 * from a screen that is in the middle of a task:
 *
 * 1. ADB debugging is authorized and the connection actually works.
 * 2. Notification access is granted, because the overlay's only way to be stopped is its
 *    foreground service notification.
 * 3. Overlay access is granted, because the background overlay cannot draw without it and there
 *    is no dialog that can request it.
 *
 * All three are re-checked from live state whenever this screen appears, so revoking a permission
 * brings the checklist back rather than leaving the app quietly broken.
 */
class IntroViewModel(
    private val adbConnectionGate: AdbConnectionGate,
    private val notificationPermissionChecker: NotificationPermissionChecker,
    private val overlayAccessChecker: OverlayAccessChecker,
    private val cpuMonitor: CpuMonitor,
) : ViewModel() {

    private val _uiState = MutableStateFlow(IntroViewState())
    val uiState: StateFlow<IntroViewState> = _uiState.asStateFlow()

    private val _commands = MutableSharedFlow<IntroCommand>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val commands: SharedFlow<IntroCommand> = _commands.asSharedFlow()

    private var pendingCommand: IntroCommand? = null
    private var adbJob: Job? = null
    private var verifyJob: Job? = null
    private var isMonitoringAcquired = false
    private var hasNavigated = false

    fun accept(intent: IntroIntent) {
        when (intent) {
            IntroIntent.Load -> checkReadiness()

            IntroIntent.ActionClicked -> runOutstandingAction()

            is IntroIntent.NotificationPermissionResult -> onNotificationPermissionResult(intent)

            // The grant is decided in Settings, not by a result code, so this simply re-checks.
            IntroIntent.OverlaySettingsResult -> checkReadiness()

            IntroIntent.ExitApp -> viewModelScope.launch { emit(IntroCommand.ExitApp) }
        }
    }

    /**
     * Settles what can be settled locally first, then starts the ADB work.
     *
     * Permissions are checked before ADB because they are instant local reads, and because the
     * user should not be left watching a connection spinner while a permission decision is
     * waiting to be made.
     */
    private fun checkReadiness() {
        adbJob?.cancel()
        verifyJob?.cancel()

        _uiState.value = IntroViewState(
            items = ChecklistItem.entries.map {
                IntroChecklistItem(
                    label = labelFor(it),
                    status = ChecklistStatus.InProgress,
                    detail = "Checking",
                )
            },
            isReady = false,
            action = null,
        )

        checkNotifications()
        checkOverlayAccess()
        checkAdb()
    }

    private fun checkNotifications() {
        if (!notificationPermissionChecker.isRequired()) {
            update(ChecklistItem.Notifications, ChecklistStatus.NotNeeded, "Not needed on this version")
            refreshAction()
            return
        }

        if (notificationPermissionChecker.isGranted()) {
            update(ChecklistItem.Notifications, ChecklistStatus.Ready, "Granted")
            refreshAction()
            return
        }

        // Asked on demand rather than immediately, so the first item the user sees is a prompt
        // they chose to answer rather than one that appeared before they had read anything.
        update(ChecklistItem.Notifications, ChecklistStatus.NeedsAttention, "Tap Allow notifications")
        refreshAction()
    }

    private fun onNotificationPermissionResult(intent: IntroIntent.NotificationPermissionResult) {
        // Trust the platform's answer, but re-read the permission rather than the boolean: the
        // user may have answered a dialog that was already showing for something else, and the
        // permission state is what the rest of the app depends on.
        val granted = intent.granted && notificationPermissionChecker.isGranted()

        update(
            ChecklistItem.Notifications,
            if (granted) ChecklistStatus.Ready else ChecklistStatus.NeedsAttention,
            if (granted) "Granted" else "Denied, tap to ask again",
        )

        advanceWhenReady()
    }

    private fun checkOverlayAccess() {
        if (overlayAccessChecker.canDrawOverlays()) {
            update(ChecklistItem.OverlayAccess, ChecklistStatus.Ready, "Granted")
            refreshAction()
            return
        }

        update(ChecklistItem.OverlayAccess, ChecklistStatus.NeedsAttention, "Tap Open Settings to allow")
        refreshAction()
    }

    private fun checkAdb() {
        if (adbConnectionGate.isAuthorized) {
            update(ChecklistItem.AdbDebugging, ChecklistStatus.Ready, "Connected")
            refreshAction()
            advanceWhenReady()
            return
        }

        adbJob = viewModelScope.launch {
            adbConnectionGate.ensureAuthorized()
                .onSuccess {
                    update(ChecklistItem.AdbDebugging, ChecklistStatus.Ready, "Connected")
                    refreshAction()
                    advanceWhenReady()
                }
                .onFailure { error ->
                    Log.w(TAG, "ADB access was not granted", error)
                    update(ChecklistItem.AdbDebugging, ChecklistStatus.NeedsAttention, "Not authorized, tap to retry")
                    refreshAction()
                }
        }
    }

    /**
     * Runs whatever the user asked for.
     *
     * The ADB handshake is not given a dedicated action, because it needs no user input: it is
     * retried directly. Only the two permissions can require a prompt or a Settings trip.
     */
    private fun runOutstandingAction() {
        when (val action = _uiState.value.action) {
            ChecklistAction.RequestNotifications -> {
                update(ChecklistItem.Notifications, ChecklistStatus.InProgress, "Waiting for your answer")
                viewModelScope.launch { emit(IntroCommand.RequestNotificationPermission) }
            }

            ChecklistAction.OpenOverlaySettings -> viewModelScope.launch {
                emit(IntroCommand.OpenOverlaySettings)
            }

            ChecklistAction.Retry, null -> checkAdb()
        }
    }

    /**
     * Everything is authorized on paper. The connection still has to prove itself against a real
     * reading, so the dashboard never takes over a session that authorizes but cannot run a
     * shell command.
     */
    private fun advanceWhenReady() {
        val state = _uiState.value
        val allSatisfied = state.items.isNotEmpty() && state.items.all {
            it.status == ChecklistStatus.Ready || it.status == ChecklistStatus.NotNeeded
        }
        if (!allSatisfied || state.isReady || hasNavigated) return

        _uiState.update { it.copy(isCheckingAdb = true) }

        verifyJob = viewModelScope.launch {
            acquireMonitoring()

            val snapshot = withTimeoutOrNull(VERIFY_TIMEOUT_MILLIS) {
                cpuMonitor.results.filterNotNull().first()
            }

            // Any snapshot proves the shell transport works, including an Unsupported one,
            // which the dashboard renders with detail.
            if (snapshot == null) {
                Log.w(TAG, "No snapshot within ${VERIFY_TIMEOUT_MILLIS}ms, failing verification")
                releaseMonitoring()
                update(ChecklistItem.AdbDebugging, ChecklistStatus.NeedsAttention, "Connected, but no process data arrived")
                refreshAction()
                _uiState.update { it.copy(isCheckingAdb = false) }
                return@launch
            }

            _uiState.update { it.copy(isReady = true, isCheckingAdb = false) }
            navigateToDashboard()
        }
    }

    /**
     * Recomputes the single action button from the current checklist.
     *
     * Derived from state rather than passed in, because more than one item can become outstanding
     * during a pass and the topmost one has to win. The user works the list top to bottom, so a
     * later item does not take over the button while an earlier one is still unanswered.
     */
    private fun refreshAction() {
        _uiState.update { state ->
            val firstOutstanding = ChecklistItem.entries
                .mapNotNull { item -> state.items.find { it.label == labelFor(item) } }
                .firstOrNull { it.status == ChecklistStatus.NeedsAttention }
            state.copy(
                action = when {
                    firstOutstanding == null -> null
                    firstOutstanding.label == labelFor(ChecklistItem.Notifications) ->
                        ChecklistAction.RequestNotifications

                    firstOutstanding.label == labelFor(ChecklistItem.OverlayAccess) ->
                        ChecklistAction.OpenOverlaySettings

                    else -> ChecklistAction.Retry
                }
            )
        }
    }

    private fun update(
        item: ChecklistItem,
        status: ChecklistStatus,
        detail: String,
    ) {
        _uiState.update { state ->
            state.copy(
                items = state.items.map {
                    if (it.label == labelFor(item)) it.copy(status = status, detail = detail) else it
                }
            )
        }
    }

    /**
     * Emits a command, buffering it if the UI has not subscribed yet.
     *
     * A plain tryEmit against a SharedFlow with no replay drops the command silently, which for a
     * permission request means the checklist spins forever with no prompt and no error. The
     * pending slot is held until something collects it.
     */
    private suspend fun emit(command: IntroCommand) {
        if (_commands.tryEmit(command)) return
        pendingCommand = command
        _commands.subscriptionCount.first { it > 0 }
        if (!_commands.tryEmit(pendingCommand ?: return)) {
            Log.w(TAG, "Command $command could not be delivered")
        }
        pendingCommand = null
    }

    private fun labelFor(item: ChecklistItem): String = when (item) {
        ChecklistItem.AdbDebugging -> "USB debugging"
        ChecklistItem.Notifications -> "Notification access"
        ChecklistItem.OverlayAccess -> "Overlay access"
    }

    private suspend fun navigateToDashboard() {
        if (hasNavigated) return
        hasNavigated = true
        emit(IntroCommand.NavigateToDashboard)
    }

    private fun acquireMonitoring() {
        if (isMonitoringAcquired) return
        cpuMonitor.acquire()
        isMonitoringAcquired = true
    }

    private fun releaseMonitoring() {
        if (!isMonitoringAcquired) return
        cpuMonitor.release()
        isMonitoringAcquired = false
    }

    override fun onCleared() {
        adbJob?.cancel()
        verifyJob?.cancel()
        releaseMonitoring()
    }

    private companion object {
        private const val VERIFY_TIMEOUT_MILLIS = 15_000L
        private const val TAG = "PerfViewIntro"
    }
}

private inline fun <T> MutableStateFlow<T>.update(transform: (T) -> T) {
    while (true) {
        val current = value
        if (compareAndSet(current, transform(current))) return
    }
}
