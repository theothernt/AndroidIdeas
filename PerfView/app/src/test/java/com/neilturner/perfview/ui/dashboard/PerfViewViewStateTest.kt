package com.neilturner.perfview.ui.dashboard

import com.neilturner.perfview.ui.dashboard.contract.BackgroundActionUiState
import com.neilturner.perfview.ui.dashboard.contract.DashboardContentState
import com.neilturner.perfview.ui.dashboard.contract.DashboardUiState
import com.neilturner.perfview.ui.dashboard.contract.PerfViewViewState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PerfViewViewStateTest {

    @Test
    fun `default view state has no dashboard and a default background action state`() {
        val state = PerfViewViewState()

        assertNull(state.dashboardState)
        assertEquals(BackgroundActionUiState(), state.backgroundActionState)
    }

    @Test
    fun `DashboardUiState default values are correct`() {
        val state = DashboardUiState()

        assertEquals("Starting", state.sourceLabel)
        assertEquals("Starting process monitor", state.statusLabel)
        assertFalse(state.isPolling)
        assertTrue(state.content is DashboardContentState.Loading)
    }

    @Test
    fun `DashboardUiState defaults to a loading message`() {
        val state = DashboardUiState()

        val content = state.content as DashboardContentState.Loading
        assertEquals("Connecting to ADB and reading top process usage", content.message)
    }

    @Test
    fun `BackgroundActionUiState default message is null`() {
        assertNull(BackgroundActionUiState().backgroundActionMessage)
    }
}
