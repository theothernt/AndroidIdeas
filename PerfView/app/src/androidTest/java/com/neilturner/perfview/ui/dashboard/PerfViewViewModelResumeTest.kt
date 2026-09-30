package com.neilturner.perfview.ui.dashboard

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.neilturner.perfview.data.adb.AdbAccessManager
import com.neilturner.perfview.data.adb.AdbConnectionGate
import com.neilturner.perfview.domain.cpu.CpuMonitor
import com.neilturner.perfview.overlay.FakeCpuRepository
import com.neilturner.perfview.overlay.OverlayPermissionManager
import com.neilturner.perfview.ui.dashboard.contract.DashboardContentState
import com.neilturner.perfview.ui.dashboard.contract.PerfViewIntent
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression cover for the resume path.
 *
 * ON_START fires on every return to the foreground, so a second Load must not replace an
 * already populated process list with a "Connecting to ADB..." placeholder.
 *
 * Instrumented rather than a JVM unit test because [OverlayPermissionManager] needs a real
 * Context, and the project has no Robolectric or coroutines-test dependency.
 */
@RunWith(AndroidJUnit4::class)
class PerfViewViewModelResumeTest {

    @Test
    fun resumeOverLiveData_keepsTheProcessList() = runBlocking {
        val viewModel = PerfViewViewModel(
            adbConnectionGate = authorizedGate(),
            overlayPermissionManager = OverlayPermissionManager(
                ApplicationProvider.getApplicationContext(),
            ),
            cpuMonitor = CpuMonitor(FakeCpuRepository()),
        )

        viewModel.accept(PerfViewIntent.Load)
        awaitContent(viewModel)

        // ON_START fires again on every return to the foreground.
        viewModel.accept(PerfViewIntent.Load)
        awaitContent(viewModel)

        val content = viewModel.uiState.value.dashboardState?.content
        assertTrue("resume replaced the list with $content", content is DashboardContentState.Data)
    }

    /**
     * Waits for the first populated poll. The monitor polls on a one second interval, so the
     * first snapshot is not available synchronously after Load.
     */
    private suspend fun awaitContent(viewModel: PerfViewViewModel) {
        val deadline = System.currentTimeMillis() + TIMEOUT_MILLIS
        while (System.currentTimeMillis() < deadline) {
            if (viewModel.uiState.value.dashboardState?.content is DashboardContentState.Data) {
                return
            }
            kotlinx.coroutines.delay(POLL_INTERVAL_MILLIS)
        }
        throw AssertionError(
            "no process data within ${TIMEOUT_MILLIS}ms: " +
                "${viewModel.uiState.value.dashboardState?.content}"
        )
    }

    private fun authorizedGate() = AdbConnectionGate(PermissiveAdbAccessManager)

    private object PermissiveAdbAccessManager : AdbAccessManager {
        override suspend fun requestAccess(timeoutMillis: Long) = Unit
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
        const val POLL_INTERVAL_MILLIS = 100L
    }
}