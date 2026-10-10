package com.neilturner.playerexp.ui.modifiers

import androidx.compose.foundation.focusable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type

/**
 * Hands every D-pad and media-remote press on the player to [onRevealControls], which is what
 * shows the hidden progress overlay. Back and Escape are left alone so the screen can still be
 * left the usual way, and every other key is consumed: the player has nothing to focus, so
 * directional presses should reveal the overlay rather than wander.
 */
fun Modifier.playerOverlayControls(onRevealControls: () -> Unit): Modifier = this
    .focusable()
    .onKeyEvent { keyEvent ->
        if (keyEvent.type != KeyEventType.KeyDown) {
            false
        } else when (keyEvent.key) {
            Key.Back, Key.Escape -> false
            else -> {
                onRevealControls()
                true
            }
        }
    }

/**
 * Reusable modifier for clickable/selectable D-pad items (DirectionCenter or Enter).
 */
fun Modifier.dpadSelectable(
    onSelect: () -> Unit
): Modifier = this
    .focusable()
    .onKeyEvent { keyEvent ->
        if (keyEvent.type == KeyEventType.KeyDown &&
            (keyEvent.key == Key.DirectionCenter || keyEvent.key == Key.Enter)
        ) {
            onSelect()
            true
        } else {
            false
        }
    }
