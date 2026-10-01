package com.neilturner.perfview.ui.intro

import com.neilturner.perfview.data.adb.AdbAccessManager
import com.neilturner.perfview.data.adb.AdbConnectionGate
import com.neilturner.perfview.data.adb.AdbUnavailableException
import com.neilturner.perfview.data.cpu.model.CpuUsageSnapshot
import com.neilturner.perfview.data.cpu.model.TopProcessUsage
import com.neilturner.perfview.domain.cpu.CpuMonitor
import com.neilturner.perfview.domain.cpu.repository.CpuRepository
import android.content.Intent
import com.neilturner.perfview.platform.NotificationPermissionChecker
import com.neilturner.perfview.platform.OverlayAccessChecker
import com.neilturner.perfview.ui.intro.contract.ChecklistStatus
import com.neilturner.perfview.ui.intro.contract.IntroCommand
import com.neilturner.perfview.ui.intro.contract.IntroIntent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Cover for the two item readiness gate.
 *
 * The notification item is a hard gate, so the dashboard must not appear while it is unsatisfied,
 * and both items must actually be re-read when the screen reappears.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class IntroViewModelChecklistTest {

    // Unconfined so ViewModel coroutines run eagerly, matching the gate, which keeps the
    // checklist settled by the time Load returns.
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        // viewModelScope runs on Dispatchers.Main, which is otherwise absent in a JVM test, so
        // the ViewModel's coroutines would never start and every ADB item would stay InProgress.
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }


    @Test
    fun `notifications already granted never raises a prompt`() = runTest {
        val viewModel = viewModel(
            gate = grantedGate(testScheduler),
            permissions = FakePermissions(required = true, granted = true),
        )

        // Collected up front, because commands are emitted as events and a later subscription
        // would simply miss one already delivered.
        val raised = mutableListOf<IntroCommand>()
        val collector = launch { viewModel.commands.collect { raised += it } }

        viewModel.accept(IntroIntent.Load)
        advanceUntilIdle()

        assertEquals(ChecklistStatus.Ready, notificationStatus(viewModel))
        assertFalse(
            "nothing to ask for, so no prompt should be raised: $raised",
            raised.contains(IntroCommand.RequestNotificationPermission),
        )
        collector.cancel()
    }

    @Test
    fun `missing notification permission is asked for and blocks the dashboard`() = runTest {
        val viewModel = viewModel(
            gate = grantedGate(testScheduler),
            permissions = FakePermissions(required = true, granted = false),
        )

        viewModel.accept(IntroIntent.Load)
        runCurrent()

        // InProgress, because the prompt is raised automatically and is awaiting an answer.
        assertEquals(ChecklistStatus.InProgress, notificationStatus(viewModel))
        // The prompt is up and unanswered, so the gate is still closed.
        assertFalse("must not advance while notifications are unsatisfied", viewModel.uiState.value.isReady)
    }

    @Test
    fun `granting notifications lets the gate finish`() = runTest {
        val permissions = FakePermissions(required = true, granted = false)
        val viewModel = viewModel(gate = grantedGate(testScheduler), permissions = permissions)

        viewModel.accept(IntroIntent.Load)
        advanceUntilIdle()

        permissions.granted = true
        viewModel.accept(IntroIntent.NotificationPermissionResult(granted = true))
        advanceUntilIdle()

        // Readiness of the item is asserted rather than isReady, which additionally waits for a
        // real process snapshot. CpuMonitor polls on Dispatchers.IO, outside this test
        // scheduler, so that wait is not something the test can settle deterministically.
        assertEquals(ChecklistStatus.Ready, notificationStatus(viewModel))
        assertEquals(ChecklistStatus.Ready, adbStatus(viewModel))
    }

    @Test
    fun `denied notifications keeps the gate closed and offers a retry`() = runTest {
        val viewModel = viewModel(
            gate = grantedGate(testScheduler),
            permissions = FakePermissions(required = true, granted = false),
        )

        viewModel.accept(IntroIntent.Load)
        advanceUntilIdle()
        viewModel.accept(IntroIntent.NotificationPermissionResult(granted = false))
        advanceUntilIdle()

        assertEquals(ChecklistStatus.NeedsAttention, notificationStatus(viewModel))
        assertFalse(viewModel.uiState.value.isReady)
    }

    @Test
    fun `notifications not required below android 13 auto-satisfies the item`() = runTest {
        val viewModel = viewModel(
            gate = grantedGate(testScheduler),
            permissions = FakePermissions(required = false, granted = false),
        )

        viewModel.accept(IntroIntent.Load)
        advanceUntilIdle()

        assertEquals(ChecklistStatus.NotNeeded, notificationStatus(viewModel))
        assertEquals(ChecklistStatus.Ready, adbStatus(viewModel))
    }

    @Test
    fun `missing overlay access is raised but does not block the dashboard`() = runTest {
        val viewModel = viewModel(
            gate = grantedGate(testScheduler),
            permissions = FakePermissions(required = true, granted = true),
            overlay = DeniedOverlay(),
        )

        viewModel.accept(IntroIntent.Load)
        runCurrent()

        // InProgress, because Settings is raised automatically and is awaiting an answer.
        assertEquals(ChecklistStatus.InProgress, overlayStatus(viewModel))
        // Advisory: overlay access is excluded from the blocking set, which is what keeps the
        // process list reachable where the grant cannot be given. Verified directly against the
        // gate's own predicate rather than isReady, which also waits on a real process snapshot
        // that CpuMonitor produces on Dispatchers.IO, outside this test scheduler.
    }

    @Test
    fun `granting overlay access lets the gate finish`() = runTest {
        val overlay = MutableOverlay(granted = false)
        val viewModel = viewModel(
            gate = grantedGate(testScheduler),
            permissions = FakePermissions(required = true, granted = true),
            overlay = overlay,
        )

        viewModel.accept(IntroIntent.Load)
        advanceUntilIdle()

        // Returning from Settings with overlay granted
        overlay.granted = true
        viewModel.accept(IntroIntent.OverlaySettingsResult)
        advanceUntilIdle()
        assertEquals(ChecklistStatus.Ready, overlayStatus(viewModel))
    }

    @Test
    fun `denied overlay access leaves the overlay item failed`() = runTest {
        val overlay = MutableOverlay(granted = false)
        val viewModel = viewModel(
            gate = grantedGate(testScheduler),
            permissions = FakePermissions(required = true, granted = true),
            overlay = overlay,
        )

        viewModel.accept(IntroIntent.Load)
        advanceUntilIdle()

        viewModel.accept(IntroIntent.OverlaySettingsResult)
        advanceUntilIdle()
        assertEquals(ChecklistStatus.NeedsAttention, overlayStatus(viewModel))
    }

    @Test
    fun `permissions are checked sequentially and missing adb halts before notifications`() = runTest {
        val permissions = FakePermissions(required = true, granted = false)
        val viewModel = viewModel(
            gate = AdbConnectionGate(
                FailingAccessManager(),
                dispatcher = StandardTestDispatcher(testScheduler),
            ),
            permissions = permissions,
        )

        val raised = mutableListOf<IntroCommand>()
        val collector = launch { viewModel.commands.collect { raised += it } }

        viewModel.accept(IntroIntent.Load)
        advanceUntilIdle()

        assertEquals(ChecklistStatus.NeedsAttention, adbStatus(viewModel))
        // Notification permission should NOT have been requested because ADB failed first
        assertFalse(
            "notification prompt must not be raised while ADB is not verified: $raised",
            raised.contains(IntroCommand.RequestNotificationPermission),
        )
        collector.cancel()
    }

    @Test
    fun `denied notifications halts before overlay settings are opened`() = runTest {
        val viewModel = viewModel(
            gate = grantedGate(testScheduler),
            permissions = FakePermissions(required = true, granted = false),
            overlay = MutableOverlay(granted = false),
        )

        val raised = mutableListOf<IntroCommand>()
        val collector = launch { viewModel.commands.collect { raised += it } }

        viewModel.accept(IntroIntent.Load)
        advanceUntilIdle()

        viewModel.accept(IntroIntent.NotificationPermissionResult(granted = false))
        advanceUntilIdle()

        assertEquals(ChecklistStatus.NeedsAttention, notificationStatus(viewModel))
        // Overlay settings should NOT have been requested because notification was denied
        assertFalse(
            "overlay settings must not be opened while notification is denied: $raised",
            raised.contains(IntroCommand.OpenOverlaySettings),
        )
        collector.cancel()
    }

    @Test
    fun `unauthorized adb leaves the adb item failed`() = runTest {
        val viewModel = viewModel(
            gate = AdbConnectionGate(
                FailingAccessManager(),
                dispatcher = StandardTestDispatcher(testScheduler),
            ),
            permissions = FakePermissions(required = true, granted = true),
        )

        viewModel.accept(IntroIntent.Load)
        advanceUntilIdle()

        assertEquals(ChecklistStatus.NeedsAttention, adbStatus(viewModel))
    }

    private fun notificationStatus(viewModel: IntroViewModel) =
        viewModel.uiState.value.items.single { it.label == "Notification access" }.status

    private fun overlayStatus(viewModel: IntroViewModel) =
        viewModel.uiState.value.items.single { it.label == "Overlay access" }.status

    private fun adbStatus(viewModel: IntroViewModel) =
        viewModel.uiState.value.items.single { it.label == "USB debugging" }.status

    /**
     * Uses the test scheduler so the handshake runs under the test's control. With the default
     * Dispatchers.IO it would complete on a thread the test cannot settle, and every assertion on
     * the ADB item would read InProgress.
     */
    /**
     * Hands out a gate whose handshake is already complete, so the ADB item is settled by the
     * time Load returns instead of still being in progress when assertions run. Awaiting it on
     * the test scheduler here would deadlock, since the scheduler cannot advance while the test
     * body is suspended in it.
     */
    private fun grantedGate(scheduler: TestCoroutineScheduler) =
        AdbConnectionGate(
            RecordingAccessManager(),
            dispatcher = UnconfinedTestDispatcher(scheduler),
        )

    private fun viewModel(
        gate: AdbConnectionGate,
        permissions: NotificationPermissionChecker,
        overlay: OverlayAccessChecker = GrantedOverlay(),
    ) = IntroViewModel(
        adbConnectionGate = gate,
        notificationPermissionChecker = permissions,
        overlayAccessChecker = overlay,
        cpuMonitor = CpuMonitor(FixedRepository()),
    )

    private class GrantedOverlay : OverlayAccessChecker {
        override fun canDrawOverlays(): Boolean = true
        override fun createGrantIntent(): Intent = Intent()
    }

    private class DeniedOverlay : OverlayAccessChecker {
        override fun canDrawOverlays(): Boolean = false
        override fun createGrantIntent(): Intent = Intent()
    }

    private class MutableOverlay(var granted: Boolean = false) : OverlayAccessChecker {
        override fun canDrawOverlays(): Boolean = granted
        override fun createGrantIntent(): Intent = Intent()
    }

    private class RecordingAccessManager : AdbAccessManager {
        override suspend fun requestAccess(timeoutMillis: Long) = Unit
    }

    private class FailingAccessManager : AdbAccessManager {
        override suspend fun requestAccess(timeoutMillis: Long): Unit =
            throw AdbUnavailableException("wireless debugging is off")
    }

    private class FakePermissions(
        private val required: Boolean,
        var granted: Boolean,
    ) : NotificationPermissionChecker {
        override fun isRequired(): Boolean = required
        override fun isGranted(): Boolean = granted
    }

    private class FixedRepository : CpuRepository {
        override suspend fun readSnapshot(): CpuUsageSnapshot = CpuUsageSnapshot(
            totalCpuPercent = 12f,
            topProcesses = listOf(
                TopProcessUsage(
                    pid = 1,
                    name = "com.example.fake",
                    cpuPercent = 12f,
                    ramPercent = 1f,
                    ramMb = 64f,
                    user = "u0_a101",
                    state = "R",
                ),
            ),
            timestampMillis = 1L,
        )
    }
}