package com.neilturner.perfview

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.neilturner.perfview.ui.dashboard.contract.DashboardContentState
import com.neilturner.perfview.ui.dashboard.contract.DashboardUiState
import com.neilturner.perfview.ui.dashboard.contract.PerfViewViewState
import com.neilturner.perfview.ui.dashboard.PerfViewScreen
import com.neilturner.perfview.ui.theme.PerfViewTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun permissionState_isDisplayed() {
        composeTestRule.setContent {
            PerfViewTheme {
                PerfViewScreen(
                    uiState = PerfViewViewState(
                        permissionState = com.neilturner.perfview.ui.dashboard.contract.PermissionUiState(
                            phase = com.neilturner.perfview.ui.dashboard.contract.PermissionPhase.Rationale,
                            title = "ADB access is needed",
                            message = "Grant access to continue",
                        ),
                        dashboardState = null,
                    )
                )
            }
        }

        composeTestRule
            .onNodeWithText("ADB access is needed")
            .assertExists()
            .assertIsDisplayed()
    }
}
