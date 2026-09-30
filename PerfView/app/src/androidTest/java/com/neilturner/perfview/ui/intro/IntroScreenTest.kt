package com.neilturner.perfview.ui.intro

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.neilturner.perfview.ui.intro.contract.ChecklistStatus
import com.neilturner.perfview.ui.intro.contract.IntroChecklistItem
import com.neilturner.perfview.ui.intro.contract.IntroViewState
import com.neilturner.perfview.ui.theme.PerfViewTheme
import com.neilturner.perfview.ui.theme.PerfViewTvTheme
import org.junit.Rule
import org.junit.Test

class IntroScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setContent(items: List<IntroChecklistItem>) {
        composeTestRule.setContent {
            PerfViewTheme(dynamicColor = false) {
                PerfViewTvTheme {
                    IntroScreen(uiState = IntroViewState(items = items))
                }
            }
        }
    }

    @Test
    fun allThreeItems_areListed() {
        setContent(
            listOf(
                IntroChecklistItem("USB debugging", ChecklistStatus.Ready, "Connected"),
                IntroChecklistItem("Notification access", ChecklistStatus.Ready, "Granted"),
                IntroChecklistItem("Overlay access", ChecklistStatus.Ready, "Granted"),
            )
        )

        composeTestRule.onNodeWithText("USB debugging").assertIsDisplayed()
        composeTestRule.onNodeWithText("Notification access").assertIsDisplayed()
        composeTestRule.onNodeWithText("Overlay access").assertIsDisplayed()
    }

    @Test
    fun theItemBeingAskedFor_showsItIsAwaitingAnAnswer() {
        setContent(
            listOf(
                IntroChecklistItem("USB debugging", ChecklistStatus.Ready, "Connected"),
                IntroChecklistItem(
                    label = "Notification access",
                    status = ChecklistStatus.InProgress,
                    detail = "Waiting for your answer",
                ),
                IntroChecklistItem("Overlay access", ChecklistStatus.InProgress, "Checking"),
            )
        )

        composeTestRule.onNodeWithText("Waiting for your answer").assertIsDisplayed()
    }

    @Test
    fun aRefusedItem_showsTheReasonInAmber() {
        setContent(
            listOf(
                IntroChecklistItem("USB debugging", ChecklistStatus.Ready, "Connected"),
                IntroChecklistItem("Notification access", ChecklistStatus.NeedsAttention, "Not granted"),
                IntroChecklistItem("Overlay access", ChecklistStatus.Ready, "Granted"),
            )
        )

        composeTestRule.onNodeWithText("Not granted").assertIsDisplayed()
    }

    @Test
    fun noControls_areOffered() {
        setContent(
            listOf(
                IntroChecklistItem("USB debugging", ChecklistStatus.Ready, "Connected"),
                IntroChecklistItem("Notification access", ChecklistStatus.NeedsAttention, "Not granted"),
                IntroChecklistItem("Overlay access", ChecklistStatus.NeedsAttention, "Not granted"),
            )
        )

        // The screen is a passive checklist: every permission is raised by the gate itself, so
        // there is nothing for the user to press here, not even a way out.
        composeTestRule.onNodeWithText("Exit app").assertDoesNotExist()
        composeTestRule.onNodeWithText("Try again").assertDoesNotExist()
        composeTestRule.onNodeWithText("Open Settings").assertDoesNotExist()
        composeTestRule.onNodeWithText("Allow notifications").assertDoesNotExist()
    }
}