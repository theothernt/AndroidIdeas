package com.neilturner.perfview.ui.intro

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.neilturner.perfview.ui.intro.contract.IntroContentState
import com.neilturner.perfview.ui.intro.contract.IntroViewState
import com.neilturner.perfview.ui.theme.PerfViewTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class IntroScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setContent(
        content: IntroContentState,
        onRetry: () -> Unit = {},
        onExitApp: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            PerfViewTheme(dynamicColor = false) {
                IntroScreen(
                    uiState = IntroViewState(content = content),
                    onRetry = onRetry,
                    onExitApp = onExitApp,
                )
            }
        }
    }

    @Test
    fun checkingState_showsProgressCopyAndNoRetryButton() {
        setContent(IntroContentState.Checking)

        composeTestRule.onNodeWithText("Checking connection").assertExists()
        composeTestRule.onNodeWithText("Try again").assertDoesNotExist()
    }

    @Test
    fun needsAuthorization_reportsTheRejectionAndOffersRetry() {
        setContent(IntroContentState.NeedsAuthorization)

        composeTestRule.onNodeWithText("Permission needed").assertExists()
        composeTestRule.onNodeWithText("Try again").assertIsDisplayed()
    }

    @Test
    fun retryButton_invokesCallback() {
        var clicks = 0
        setContent(IntroContentState.NeedsAuthorization, onRetry = { clicks++ })

        composeTestRule.onNodeWithText("Try again").performClick()

        assertEquals(1, clicks)
    }

    @Test
    fun authorizingState_showsProgressCopyAndNoRetryButton() {
        setContent(IntroContentState.Authorizing(message = "Waiting for approval"))

        composeTestRule.onNodeWithText("Waiting for approval").assertExists()
        composeTestRule.onNodeWithText("Try again").assertDoesNotExist()
    }

    @Test
    fun verifyingState_showsProgressCopyAndNoRetryButton() {
        setContent(IntroContentState.Verifying(message = "Checking connection"))

        composeTestRule.onNodeWithText("Waiting for the first process reading").assertExists()
        composeTestRule.onNodeWithText("Try again").assertDoesNotExist()
    }

    @Test
    fun failedState_showsMessageAndOffersRetry() {
        setContent(IntroContentState.Failed(message = "Connected, but no process data arrived."))

        composeTestRule.onNodeWithText("Connection failed").assertExists()
        composeTestRule.onNodeWithText("Connected, but no process data arrived.").assertExists()
        composeTestRule.onNodeWithText("Try again").assertIsDisplayed()
    }

    @Test
    fun failedState_retryButton_invokesCallback() {
        var clicks = 0
        setContent(IntroContentState.Failed(message = "Nope"), onRetry = { clicks++ })

        composeTestRule.onNodeWithText("Try again").performClick()

        assertEquals(1, clicks)
    }

    @Test
    fun exitAppButton_invokesCallback() {
        var clicks = 0
        setContent(IntroContentState.Checking, onExitApp = { clicks++ })

        composeTestRule.onNodeWithText("Exit app").assertIsDisplayed().performClick()

        assertEquals(1, clicks)
    }
}