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
 * left the usual way.
 *
 * While the overlay is hidden a press is consumed: the player has nothing to focus, so a
 * directional press should reveal the overlay rather than wander. Once it is up the press only
 * counts as activity, which restarts the auto-hide timer, and is left unconsumed.
 *
 * A Back press while the overlay is visible is not handled here: the overlay draws nothing
 * focusable, so the press reaches the screen's back handler, which dismisses the overlay instead
 * of leaving playback.
 */
fun Modifier.playerOverlayControls(
    controlsVisible: Boolean,
    onRevealControls: () -> Unit
): Modifier = this
    .focusable()
    .onKeyEvent { keyEvent ->
        if (keyEvent.type != KeyEventType.KeyDown) {
            false
        } else when (keyEvent.key) {
            Key.Back, Key.Escape -> false
            else -> {
                onRevealControls()
                !controlsVisible
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
