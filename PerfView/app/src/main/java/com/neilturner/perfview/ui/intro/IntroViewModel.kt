package com.neilturner.perfview.ui.intro

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neilturner.perfview.data.adb.AdbConnectionGate
import com.neilturner.perfview.domain.cpu.CpuMonitor
import com.neilturner.perfview.platform.NotificationPermissionChecker
import com.neilturner.perfview.platform.OverlayAccessChecker
import com.neilturner.perfview.ui.intro.contract.ChecklistItem
import com.neilturner.perfview.ui.intro.contract.ChecklistStatus
import com.neilturner.perfview.ui.intro.contract.IntroChecklistItem
import com.neilturner.perfview.ui.intro.contract.IntroCommand
import com.neilturner.perfview.ui.intro.contract.IntroIntent
import com.neilturner.perfview.ui.intro.contract.IntroViewState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Readiness gate shown before the dashboard.
 *
 * Everything the app needs is settled here, in one place, so nothing asks for a permission later
 * from a screen that is in the middle of a task:
 *
 * 1. Notification access, because the overlay's only way to be stopped is its notification.
 * 2. Overlay access, because the background overlay cannot draw without it.
 * 3. ADB debugging, which has to be authorized and then proven against real process data.
 *
 * There are no buttons: each permission is raised as soon as it is reached, and the row being
 * worked on shows a spinner until the user has answered. The two permission prompts are sequenced
 * rather than fired together, because the system will not show two dialogs at once.
 */
class IntroViewModel(
    private val adbConnectionGate: AdbConnectionGate,
    private val notificationPermissionChecker: NotificationPermissionChecker,
    private val overlayAccessChecker: OverlayAccessChecker,
    private val cpuMonitor: CpuMonitor,
) : ViewModel() {

    private val _uiState = MutableStateFlow(IntroViewState())
    val uiState: StateFlow<IntroViewState> = _uiState.asStateFlow()

    /**
 * One-shot UI commands.
 *
 * A Channel rather than a SharedFlow, and this matters: SharedFlow.tryEmit reports success into
 * its internal buffer even when nothing is collecting, so with replay = 0 the value is silently
 * dropped. That is exactly what stopped the permission prompts from ever appearing. A Channel
 * genuinely holds the command until the UI collects it.
 */
private val _commands = Channel<IntroCommand>(capacity = Channel.BUFFERED)
    val commands = _commands.receiveAsFlow()

    private var adbJob: Job? = null
    private var verifyJob: Job? = null
    private var promptWatchdogJob: Job? = null
    private var isMonitoringAcquired = false
    private var hasNavigated = false

    /**
     * Guards against re-raising a prompt the user has already come back from.
     *
     * Without this, returning from the notification dialog or from Settings without granting
     * would immediately re-open it, and the user could never leave the gate.
     */
    private var hasRequestedNotifications = false
    private var hasRequestedOverlayAccess = false

    fun accept(intent: IntroIntent) {
        when (intent) {
            IntroIntent.Load -> checkReadiness()

            is IntroIntent.NotificationPermissionResult -> onNotificationPermissionResult(intent)

            // The overlay grant is decided in Settings, not by a result code.
            IntroIntent.OverlaySettingsResult -> onOverlaySettingsResult()

        }
    }

    /**
     * Re-reads everything from live platform state sequentially.
     *
     * Permissions and connections are checked one by one rather than in parallel,
     * so dialogs and settings activities do not collide or race.
     * Once a permission is verified, the gate advances to the next item:
     * 1. USB debugging (AdbDebugging)
     * 2. Notification access (Notifications)
     * 3. Overlay access (OverlayAccess)
     */
    private fun checkReadiness() {
        adbJob?.cancel()
        verifyJob?.cancel()
        cancelPromptWatchdog()

        if (_uiState.value.items.isEmpty()) {
            _uiState.value = IntroViewState(
                items = ChecklistItem.entries.map {
                    IntroChecklistItem(
                        label = labelFor(it),
                        status = ChecklistStatus.InProgress,
                        detail = "Checking",
                    )
                },
                isReady = false,
            )
        }

        hasNavigated = false
        checkAdb()
    }

    private fun checkAdb() {
        if (adbConnectionGate.isAuthorized) {
            update(ChecklistItem.AdbDebugging, ChecklistStatus.Ready, "Connected")
            checkNotifications()
            return
        }

        update(ChecklistItem.AdbDebugging, ChecklistStatus.InProgress, "Checking")
        adbJob = viewModelScope.launch {
            adbConnectionGate.ensureAuthorized()
                .onSuccess {
                    update(ChecklistItem.AdbDebugging, ChecklistStatus.Ready, "Connected")
                    checkNotifications()
                }
                .onFailure { error ->
                    Log.w(TAG, "ADB access was not granted", error)
                    update(ChecklistItem.AdbDebugging, ChecklistStatus.NeedsAttention, "Not authorized")
                }
        }
    }

    private fun checkNotifications() {
        if (!notificationPermissionChecker.isRequired()) {
            update(ChecklistItem.Notifications, ChecklistStatus.NotNeeded, "Not needed on this version")
            checkOverlayAccess()
            return
        }

        if (notificationPermissionChecker.isGranted()) {
            update(ChecklistItem.Notifications, ChecklistStatus.Ready, "Granted")
            checkOverlayAccess()
            return
        }

        if (hasRequestedNotifications) {
            update(ChecklistItem.Notifications, ChecklistStatus.NeedsAttention, "Not granted")
            return
        }

        update(ChecklistItem.Notifications, ChecklistStatus.InProgress, "Waiting for your answer")
        hasRequestedNotifications = true
        emit(IntroCommand.RequestNotificationPermission)
        startPromptWatchdog()
    }

    private fun onNotificationPermissionResult(intent: IntroIntent.NotificationPermissionResult) {
        // Trust the platform's answer, but re-read the permission rather than the boolean: the
        // user may have answered a dialog that was already showing for something else, and the
        // permission state is what the rest of the app depends on.
        val granted = intent.granted && notificationPermissionChecker.isGranted()

        update(
            ChecklistItem.Notifications,
            if (granted) ChecklistStatus.Ready else ChecklistStatus.NeedsAttention,
            if (granted) "Granted" else "Not granted",
        )

        onPromptAnswered()

        // The next prompt is only raised once this one has been verified and granted.
        if (granted) {
            checkOverlayAccess()
        }
    }

    private fun checkOverlayAccess() {
        if (overlayAccessChecker.canDrawOverlays()) {
            update(ChecklistItem.OverlayAccess, ChecklistStatus.Ready, "Granted")
            advanceWhenReady()
            return
        }

        if (hasRequestedOverlayAccess) {
            update(ChecklistItem.OverlayAccess, ChecklistStatus.NeedsAttention, "Not granted")
            return
        }

        // Always raised. Not pre-checked with resolveActivity: that check is unreliable here,
        // because it depends on package visibility and on whether the Settings activity declares
        // a matching data scheme. Treating it as the answer meant the prompt was silently skipped
        // on devices where it does work. The launch itself is guarded instead.
        update(ChecklistItem.OverlayAccess, ChecklistStatus.InProgress, "Waiting for your answer")
        hasRequestedOverlayAccess = true
        emit(IntroCommand.OpenOverlaySettings)
        startPromptWatchdog()
    }

    private fun onOverlaySettingsResult() {
        // Re-read rather than trust the result code: the user may have navigated away without
        // changing anything, and Settings reports success either way.
        val granted = overlayAccessChecker.canDrawOverlays()
        update(
            ChecklistItem.OverlayAccess,
            if (granted) ChecklistStatus.Ready else ChecklistStatus.NeedsAttention,
            if (granted) "Granted" else "Not granted",
        )

        onPromptAnswered()

        if (granted) {
            advanceWhenReady()
        }
    }

    /**
     * Stops an outstanding prompt from spinning forever.
     *
     * The system decides when, or whether, it actually shows the permission screen, and a build
     * can start the intent without ever calling back. Rather than leave the row spinning
     * indefinitely, an unanswered prompt settles so the state on screen stays truthful.
     */
    private fun startPromptWatchdog() {
        promptWatchdogJob?.cancel()
        promptWatchdogJob = viewModelScope.launch {
            delay(PROMPT_WATCHDOG_MILLIS)
            if (hasRequestedOverlayAccess && _uiState.value.isOverlayAccessInProgress) {
                Log.w(TAG, "Overlay permission screen did not come back, settling the row")
                update(ChecklistItem.OverlayAccess, ChecklistStatus.NeedsAttention, "Not granted")
            }
            if (hasRequestedNotifications && _uiState.value.isNotificationsInProgress) {
                Log.w(TAG, "Notification prompt did not come back, settling the row")
                update(ChecklistItem.Notifications, ChecklistStatus.NeedsAttention, "Not granted")
            }
        }
    }

    private fun cancelPromptWatchdog() {
        promptWatchdogJob?.cancel()
        promptWatchdogJob = null
    }

    private fun onPromptAnswered() {
        cancelPromptWatchdog()
    }

    /**
     * Everything is granted on paper. The connection still has to prove itself against a real
     * reading, so the dashboard never takes over a session that authorizes but cannot run a
     * shell command.
     */
    /**
 * Whether every item is satisfied. All three gate the dashboard, including overlay access: the
 * background overlay cannot work without it, and the app should not present itself as ready when
 * a feature it offers is unusable.
 */
    private fun advanceWhenReady() {
        val state = _uiState.value
        val allSatisfied = state.items.isNotEmpty() && state.items.all {
            it.status == ChecklistStatus.Ready || it.status == ChecklistStatus.NotNeeded
        }
        if (!allSatisfied || state.isReady || hasNavigated || verifyJob?.isActive == true) return

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
                update(ChecklistItem.AdbDebugging, ChecklistStatus.NeedsAttention, "No process data arrived")
                return@launch
            }

        _uiState.update { it.copy(isReady = true) }
            navigateToDashboard()
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

    /** Non-blocking send; the Channel holds it until the UI collects. */
    private fun emit(command: IntroCommand) {
        _commands.trySend(command)
    }

    private fun labelFor(item: ChecklistItem): String = when (item) {
        ChecklistItem.AdbDebugging -> "USB debugging"
        ChecklistItem.Notifications -> "Notification access"
        ChecklistItem.OverlayAccess -> "Overlay access"
    }

    private fun navigateToDashboard() {
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
        cancelPromptWatchdog()
        releaseMonitoring()
    }

    private companion object {
        /**
         * How long an outstanding prompt may stay unanswered before the row stops claiming it
         * is still in progress.
         */
        private const val PROMPT_WATCHDOG_MILLIS = 90_000L

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
