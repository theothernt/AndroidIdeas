package com.neilturner.perfview.ui.dashboard

import com.neilturner.perfview.ui.dashboard.contract.DashboardContentState
import com.neilturner.perfview.ui.dashboard.contract.DashboardUiState
import com.neilturner.perfview.ui.dashboard.contract.PermissionPhase
import com.neilturner.perfview.ui.dashboard.contract.PermissionUiState
import com.neilturner.perfview.ui.dashboard.contract.PerfViewViewState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PerfViewViewStateTest {

    @Test
    fun `default state has permission state and null dashboard state`() {
        val state = PerfViewViewState()

        assertEquals(PermissionPhase.Rationale, state.permissionState?.phase)
        assertEquals(null, state.dashboardState)
    }

    @Test
    fun `DashboardUiState default values are correct`() {
        val state = DashboardUiState()

        assertEquals("Starting", state.sourceLabel)
        assertEquals("Starting process monitor", state.statusLabel)
        assertEquals(null, state.lastUpdatedLabel)
        assertTrue(state.isPolling.not())
        assertTrue(state.content is DashboardContentState.Loading)
    }

    @Test
    fun `PermissionUiState default values are correct`() {
        val state = PermissionUiState()

        assertEquals(PermissionPhase.Rationale, state.phase)
        assertEquals("ADB access is needed", state.title)
        assertTrue(state.message.contains("loopback ADB access"))
        assertEquals("Grant ADB access", state.buttonLabel)
    }
}
