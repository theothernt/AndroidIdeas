package com.neilturner.navstate.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.neilturner.navstate.ui.theme.NavStateTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * These cover the behaviour that `rememberViewModelStoreNavEntryDecorator` is supposed to provide:
 * one `ViewModelStore` per `NavKey`, cleared when that key is popped off the back stack.
 */
@RunWith(AndroidJUnit4::class)
class AppNavigationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    /**
     * `waitForIdle` lets the 1s slide transition finish, so only the current entry is composed and
     * each label below resolves to exactly one node.
     */
    private fun click(label: String) {
        composeTestRule.onNodeWithText(label).performClick()
        composeTestRule.waitForIdle()
    }

    private fun assertScreen(number: Int, counter: Int) {
        composeTestRule.onNodeWithText("Screen $number").assertIsDisplayed()
        composeTestRule.onNodeWithText("Counter: $counter").assertIsDisplayed()
    }

    @Test
    fun eachScreenHasItsOwnCounter() {
        composeTestRule.setContent { NavStateTheme { AppNavigation() } }
        assertScreen(number = 1, counter = 0)

        click("Increment")
        assertScreen(number = 1, counter = 1)

        click("Go to Screen 2")
        assertScreen(number = 2, counter = 0)

        click("Increment")
        assertScreen(number = 2, counter = 1)

        click("Go to Screen 3")
        assertScreen(number = 3, counter = 0)
    }

    @Test
    fun counterSurvivesWhileItsScreenStaysOnTheBackStack() {
        composeTestRule.setContent { NavStateTheme { AppNavigation() } }

        click("Increment")
        assertScreen(number = 1, counter = 1)

        click("Go to Screen 2")
        click("Increment")
        assertScreen(number = 2, counter = 1)

        click("Go to Screen 3")
        assertScreen(number = 3, counter = 0)

        click("Back to Screen 2")
        assertScreen(number = 2, counter = 1)
    }

    @Test
    fun counterIsClearedWhenItsScreenIsPopped() {
        composeTestRule.setContent { NavStateTheme { AppNavigation() } }

        click("Go to Screen 2")
        click("Increment")
        click("Increment")
        assertScreen(number = 2, counter = 2)

        click("Back to Screen 1")
        assertScreen(number = 1, counter = 0)

        // Popping cleared Screen Two's store, so re-entering it starts from scratch.
        click("Go to Screen 2")
        assertScreen(number = 2, counter = 0)
    }
}
