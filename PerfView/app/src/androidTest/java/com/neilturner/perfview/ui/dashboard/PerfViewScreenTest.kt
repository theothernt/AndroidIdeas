package com.neilturner.perfview.ui.dashboard

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.neilturner.perfview.data.cpu.model.TopProcessUsage
import com.neilturner.perfview.ui.dashboard.contract.DashboardContentState
import com.neilturner.perfview.ui.dashboard.contract.DashboardUiState
import com.neilturner.perfview.ui.dashboard.contract.PerfViewViewState
import com.neilturner.perfview.ui.theme.PerfViewTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PerfViewScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setContent(state: PerfViewViewState, onRunInBackground: () -> Unit = {}, onExitApp: () -> Unit = {}) {
        composeTestRule.setContent {
            PerfViewTheme(dynamicColor = false) {
                PerfViewScreen(
                    uiState = state,
                    onRunInBackground = onRunInBackground,
                    onExitApp = onExitApp,
                )
            }
        }
    }

    @Test
    fun loadingState_showsItsMessage() {
        setContent(
            PerfViewViewState(
                dashboardState = DashboardUiState(
                    content = DashboardContentState.Loading(message = "Reading top process usage"),
                ),
            ),
        )

        composeTestRule.onNodeWithText("Reading top process usage").assertExists()
    }

    @Test
    fun dataState_showsProcessNames() {
        setContent(
            PerfViewViewState(
                dashboardState = DashboardUiState(
                    content = DashboardContentState.Data(
                        processes = listOf(
                            process(pid = 101, name = "com.example.heavy", cpuPercent = 87.4f, ramMb = 512f),
                            process(pid = 202, name = "com.example.light", cpuPercent = 3.1f, ramMb = 64f),
                        ),
                    ),
                ),
            ),
        )

        composeTestRule.onNodeWithText("com.example.heavy", substring = true).assertExists()
        composeTestRule.onNodeWithText("com.example.light", substring = true).assertExists()
    }

    @Test
    fun dataState_onlyShowsTheTopFiveProcesses() {
        val processes = (1..8).map { index ->
            process(pid = index, name = "com.example.proc$index", cpuPercent = 50f, ramMb = 10f)
        }

        setContent(
            PerfViewViewState(
                dashboardState = DashboardUiState(
                    content = DashboardContentState.Data(processes = processes),
                ),
            ),
        )

        composeTestRule.onNodeWithText("com.example.proc5", substring = true).assertExists()
        composeTestRule.onNodeWithText("com.example.proc6", substring = true).assertDoesNotExist()
    }

    @Test
    fun emptyState_showsItsMessage() {
        setContent(
            PerfViewViewState(
                dashboardState = DashboardUiState(
                    content = DashboardContentState.Empty(message = "No active processes"),
                ),
            ),
        )

        composeTestRule.onNodeWithText("No active processes").assertExists()
    }

    @Test
    fun unsupportedState_showsAdbUnavailableAndMessage() {
        setContent(
            PerfViewViewState(
                dashboardState = DashboardUiState(
                    content = DashboardContentState.Unsupported(message = "Wireless debugging is off"),
                ),
            ),
        )

        composeTestRule.onNodeWithText("ADB unavailable").assertExists()
        composeTestRule.onNodeWithText("Wireless debugging is off").assertExists()
    }

    @Test
    fun nullDashboardState_showsPlaceholderRows() {
        setContent(PerfViewViewState())

        composeTestRule.onAllNodesWithText("--").assertCountEquals(5)
    }

    @Test
    fun runInBackgroundButton_invokesCallback() {
        var clicks = 0
        setContent(PerfViewViewState(), onRunInBackground = { clicks++ })

        composeTestRule.onNodeWithText("Run in the background").assertIsDisplayed().performClick()

        assertEquals(1, clicks)
    }

    @Test
    fun exitAppButton_invokesCallback() {
        var clicks = 0
        setContent(PerfViewViewState(), onExitApp = { clicks++ })

        composeTestRule.onNodeWithText("Exit app").performClick()

        assertEquals(1, clicks)
    }

    private fun process(
        pid: Int,
        name: String,
        cpuPercent: Float,
        ramMb: Float,
    ) = TopProcessUsage(
        pid = pid,
        name = name,
        cpuPercent = cpuPercent,
        ramPercent = 1f,
        ramMb = ramMb,
        user = "u0_a101",
        state = "R",
    )
}
