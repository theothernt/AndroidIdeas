package com.neilturner.perfview.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus

/**
 * Activates a button the way a TV user does: take focus, then press enter.
 *
 * The TV Material 3 button deliberately does not use a clickable modifier, because a disabled
 * TV control still needs to be focusable. It has no pointer input, so a synthetic tap from
 * performClick never reaches its handler; the click is wired through the OnClick semantics action
 * and through a key handler that inspects the native key code.
 *
 * Driving focus plus enter exercises the real user path and would also catch a regression where
 * the button could not take focus at all.
 */
fun SemanticsNodeInteraction.performTvClick(): SemanticsNodeInteraction {
    requestFocus()
    return performKeyInput { pressKey(Key.Enter) }
}
