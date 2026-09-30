package com.neilturner.perfview.ui.intro

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neilturner.perfview.data.adb.AdbAccessManager
import com.neilturner.perfview.domain.cpu.CpuMonitor
import com.neilturner.perfview.ui.intro.contract.IntroCommand
import com.neilturner.perfview.ui.intro.contract.IntroContentState
import com.neilturner.perfview.ui.intro.contract.IntroIntent
import com.neilturner.perfview.ui.intro.contract.IntroViewState
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
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
 * Gates the dashboard behind ADB authorization.
 *
 * The gate separates two cases the dashboard cannot: a connection that already works,
 * which should pass straight through, and one that needs the user to approve the system
 * debugging dialog. Probing first avoids re-raising a prompt the user already accepted.
 */
class IntroViewModel(
    private val adbAccessManager: AdbAccessManager,
    private val cpuMonitor: CpuMonitor,
) : ViewModel() {

    private val _uiState = MutableStateFlow(IntroViewState())
    val uiState: StateFlow<IntroViewState> = _uiState.asStateFlow()

    private val _commands = MutableSharedFlow<IntroCommand>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val commands: SharedFlow<IntroCommand> = _commands.asSharedFlow()

    private var gateJob: Job? = null
    private var isMonitoringAcquired = false
    private var hasNavigated = false

    fun accept(intent: IntroIntent) {
        when (intent) {
            IntroIntent.Load,
            IntroIntent.RetryClicked,
            -> startGate()

            IntroIntent.ExitApp -> _commands.tryEmit(IntroCommand.ExitApp)
        }
    }

    /**
     * One connect attempt serves as both the existence check and the authorization request,
     * because a fresh key cannot be queried without triggering the system dialog: probing
     * first and prompting on a button press would raise two dialogs per run wherever the
     * key is not remembered, such as an emulator.
     *
     * A key that is already trusted connects in well under the grace period, so that case
     * passes through with only the initial spinner and never raises a dialog. A key that is
     * not trusted leaves the connect blocked on the dialog, which is detected by the grace
     * period elapsing and is reported as [IntroContentState.Authorizing].
     */
    private fun startGate() {
        gateJob?.cancel()
        _uiState.value = IntroViewState(content = IntroContentState.Checking)

        gateJob = viewModelScope.launch {
            val access = async {
                runCatching {
                    adbAccessManager.requestAccess(timeoutMillis = AUTHORIZE_TIMEOUT_MILLIS)
                }
            }

            delay(TRUSTED_CONNECT_GRACE_MILLIS)
            if (!access.isCompleted) {
                _uiState.value = IntroViewState(
                    content = IntroContentState.Authorizing(message = "Waiting for approval"),
                )
            }

            val granted = access.await().isSuccess
            if (!granted) {
                Log.d(TAG, "ADB access was not granted")
                _uiState.value = IntroViewState(content = IntroContentState.NeedsAuthorization)
                return@launch
            }

            verifyConnection()
        }
    }

    /**
     * Holds the spinner until a real process reading arrives, so the dashboard never takes
     * over a connection that authorizes but cannot actually run a shell command.
     *
     * Any snapshot counts, including an Unsupported one: reaching that state proves the shell
     * transport works, and the dashboard already renders the failure with detail.
     */
    private suspend fun verifyConnection() {
        _uiState.value = IntroViewState(
            content = IntroContentState.Verifying(message = "Checking connection"),
        )

        acquireMonitoring()

        val snapshot = withTimeoutOrNull(VERIFY_TIMEOUT_MILLIS) {
            cpuMonitor.results.filterNotNull().first()
        }

        if (snapshot == null) {
            Log.w(TAG, "No snapshot within ${VERIFY_TIMEOUT_MILLIS}ms, failing verification")
            releaseMonitoring()
            _uiState.value = IntroViewState(
                content = IntroContentState.Failed(
                    message = "Connected, but no process data arrived. Try again.",
                ),
            )
            return
        }

        navigateToDashboard()
    }

    private fun navigateToDashboard() {
        if (hasNavigated) return
        hasNavigated = true
        _commands.tryEmit(IntroCommand.NavigateToDashboard)
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
        gateJob?.cancel()
        releaseMonitoring()
    }

    private companion object {
        /**
         * How long an already-trusted key is given to connect before the attempt is assumed
         * to be waiting on the user. Loopback connects in milliseconds, so this only has to
         * cover scheduling jitter, not real network latency.
         */
        private const val TRUSTED_CONNECT_GRACE_MILLIS = 750L

        /**
         * The user decides how long to take over a debugging dialog, so the connect is not
         * abandoned underneath them.
         */
        private const val AUTHORIZE_TIMEOUT_MILLIS = 300_000L

        private const val VERIFY_TIMEOUT_MILLIS = 15_000L
        private const val TAG = "PerfViewIntro"
    }
}