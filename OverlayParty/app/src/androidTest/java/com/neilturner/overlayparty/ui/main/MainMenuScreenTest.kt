package com.neilturner.overlayparty.ui.main

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.neilturner.overlayparty.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainMenuScreenTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun mainMenuDisplaysTwoButtons() {
        composeTestRule.onNodeWithText("Start Overlay Party").assertIsDisplayed()
        composeTestRule.onNodeWithText("Screen Two").assertIsDisplayed()
    }

    @Test
    fun clickingStartOverlayPartyNavigatesToOverlayScreen() {
        composeTestRule.onNodeWithText("Start Overlay Party").performKeyInput {
            pressKey(Key.DirectionCenter)
        }
        composeTestRule.waitForIdle()
        composeTestRule.mainClock.advanceTimeBy(1_000)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Start Overlay Party").assertDoesNotExist()
    }

    @Test
    fun backButtonReturnsToMainMenu() {
        composeTestRule.onNodeWithText("Start Overlay Party").performKeyInput {
            pressKey(Key.DirectionCenter)
        }
        composeTestRule.waitForIdle()
        composeTestRule.mainClock.advanceTimeBy(1_000)
        composeTestRule.waitForIdle()

        composeTestRule.runOnIdle {
            composeTestRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeTestRule.mainClock.advanceTimeBy(1_000)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Start Overlay Party").assertIsDisplayed()
    }
}
