package com.neilturner.dreamcompose.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager

/**
 * Moves the focus to [focusRequester] once the screen appears.
 *
 * The focus system hands the focus to the first focusable item as soon as it is added, which
 * happens before the surface that draws the focused state starts to listen for the focus
 * interaction. The item would then have the focus without being drawn as focused, because the
 * interaction was emitted before anyone was listening for it. The focus is given again once
 * that surface is listening, so that the item is drawn as focused.
 */
@Composable
fun FocusOnAppear(focusRequester: FocusRequester) {
	val focusManager = LocalFocusManager.current
	LaunchedEffect(focusRequester) {
		withFrameNanos { }
		focusManager.clearFocus(force = true)
		withFrameNanos { }
		focusRequester.requestFocus()
	}
}
