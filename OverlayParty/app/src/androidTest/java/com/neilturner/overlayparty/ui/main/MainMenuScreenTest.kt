package com.neilturner.overlayparty.ui.main

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
        composeTestRule.onNodeWithText("Start Overlay Party").performClick()

        composeTestRule.waitUntil(timeoutMillis = 5000) {
            runCatching {
                composeTestRule.onNodeWithText("Start Overlay Party").assertDoesNotExist()
            }.isSuccess
        }
    }

    @Test
    fun backButtonReturnsToMainMenu() {
        composeTestRule.onNodeWithText("Start Overlay Party").performClick()

        composeTestRule.waitUntil(timeoutMillis = 5000) {
            runCatching {
                composeTestRule.onNodeWithText("Start Overlay Party").assertDoesNotExist()
            }.isSuccess
        }

        composeTestRule.activity.onBackPressed()

        composeTestRule.waitUntil(timeoutMillis = 5000) {
            runCatching {
                composeTestRule.onNodeWithText("Start Overlay Party").assertIsDisplayed()
            }.isSuccess
        }
    }
}
