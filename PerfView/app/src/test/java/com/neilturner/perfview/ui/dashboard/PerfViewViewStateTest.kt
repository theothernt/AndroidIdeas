package com.neilturner.perfview.ui.dashboard

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
    fun `default view state has no dashboard state`() {
        assertNull(PerfViewViewState().dashboardState)
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
    fun `DashboardUiState carries its content through unchanged`() {
        val content = DashboardContentState.Unsupported(message = "Wireless debugging is off")

        val state = DashboardUiState(sourceLabel = "Unavailable", content = content)

        assertEquals("Unavailable", state.sourceLabel)
        assertEquals(content, state.content)
    }
}
