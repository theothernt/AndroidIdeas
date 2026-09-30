package com.neilturner.perfview.ui.intro

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.neilturner.perfview.ui.intro.contract.ChecklistAction
import com.neilturner.perfview.ui.intro.contract.ChecklistStatus
import com.neilturner.perfview.ui.intro.contract.IntroChecklistItem
import com.neilturner.perfview.ui.intro.contract.IntroViewState
import com.neilturner.perfview.ui.performTvClick
import com.neilturner.perfview.ui.theme.PerfViewTheme
import com.neilturner.perfview.ui.theme.PerfViewTvTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class IntroScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun state(
        adbStatus: ChecklistStatus,
        adbDetail: String = "Checking",
        notificationStatus: ChecklistStatus,
        notificationDetail: String = "Checking",
        overlayStatus: ChecklistStatus = ChecklistStatus.Ready,
        overlayDetail: String = "Granted",
        action: ChecklistAction? = null,
    ) = IntroViewState(
        items = listOf(
            IntroChecklistItem("USB debugging", adbStatus, adbDetail),
            IntroChecklistItem("Notification access", notificationStatus, notificationDetail),
            IntroChecklistItem("Overlay access", overlayStatus, overlayDetail),
        ),
        action = action,
    )

    private fun setContent(
        uiState: IntroViewState,
        onAction: () -> Unit = {},
        onExitApp: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            PerfViewTheme(dynamicColor = false) {
                PerfViewTvTheme {
                    IntroScreen(
                        uiState = uiState,
                        onAction = onAction,
                        onExitApp = onExitApp,
                    )
                }
            }
        }
    }

    @Test
    fun bothItemsInProgress_showsBothLabelsAndNoRetry() {
        setContent(
            state(
                adbStatus = ChecklistStatus.InProgress,
                notificationStatus = ChecklistStatus.InProgress,
            )
        )

        composeTestRule.onNodeWithText("USB debugging").assertIsDisplayed()
        composeTestRule.onNodeWithText("Notification access").assertIsDisplayed()
        composeTestRule.onNodeWithText("Try again").assertDoesNotExist()
    }

    @Test
    fun outstandingNotifications_showsTheReasonAndOffersTheAction() {
        setContent(
            state(
                adbStatus = ChecklistStatus.Ready,
                adbDetail = "Connected",
                notificationStatus = ChecklistStatus.NeedsAttention,
                notificationDetail = "Tap Allow notifications",
                action = ChecklistAction.RequestNotifications,
            )
        )

        composeTestRule.onNodeWithText("Tap Allow notifications").assertExists()
        composeTestRule.onNodeWithText("Allow notifications").assertIsDisplayed()
    }

    @Test
    fun missingOverlayAccess_showsTheOverlayRowAndSettingsAction() {
        setContent(
            state(
                adbStatus = ChecklistStatus.Ready,
                adbDetail = "Connected",
                notificationStatus = ChecklistStatus.Ready,
                notificationDetail = "Granted",
                overlayStatus = ChecklistStatus.NeedsAttention,
                overlayDetail = "Tap Open Settings to allow",
                action = ChecklistAction.OpenOverlaySettings,
            )
        )

        composeTestRule.onNodeWithText("Overlay access").assertIsDisplayed()
        composeTestRule.onNodeWithText("Tap Open Settings to allow").assertExists()
        composeTestRule.onNodeWithText("Open Settings").assertIsDisplayed()
    }

    @Test
    fun retryAction_invokesCallback() {
        var clicks = 0
        setContent(
            state(
                adbStatus = ChecklistStatus.NeedsAttention,
                adbDetail = "Not authorized, tap to retry",
                notificationStatus = ChecklistStatus.Ready,
                action = ChecklistAction.Retry,
            ),
            onAction = { clicks++ },
        )

        composeTestRule.onNodeWithText("Try again").performTvClick()

        assertEquals(1, clicks)
    }

    @Test
    fun notNeededRow_isShownAsSuch() {
        setContent(
            state(
                adbStatus = ChecklistStatus.Ready,
                adbDetail = "Connected",
                notificationStatus = ChecklistStatus.NotNeeded,
                notificationDetail = "Not needed on this version",
            )
        )

        composeTestRule.onNodeWithText("Not needed on this version").assertExists()
        composeTestRule.onNodeWithText("Try again").assertDoesNotExist()
    }

    @Test
    fun exitAppButton_isAlwaysAvailable() {
        var clicks = 0
        setContent(
            state(
                adbStatus = ChecklistStatus.InProgress,
                notificationStatus = ChecklistStatus.InProgress,
            ),
            onExitApp = { clicks++ },
        )

        composeTestRule.onNodeWithText("Exit app").assertIsDisplayed().performTvClick()

        assertEquals(1, clicks)
    }

    @Test
    fun anActionIsOfferedOnlyAfterSomethingNeedsAttention() {
        assertFalse(
            state(
                adbStatus = ChecklistStatus.InProgress,
                notificationStatus = ChecklistStatus.InProgress,
            ).canAct
        )

        assertFalse(
            state(
                adbStatus = ChecklistStatus.Ready,
                notificationStatus = ChecklistStatus.NotNeeded,
            ).canAct
        )

        assertTrue(
            state(
                adbStatus = ChecklistStatus.Ready,
                notificationStatus = ChecklistStatus.NeedsAttention,
            ).canAct
        )
    }
}