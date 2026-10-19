package com.neilturner.playerexp.ui.modifiers

import androidx.compose.foundation.focusable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import android.view.KeyEvent as NativeKeyEvent

/**
 * Hands every D-pad and media-remote press on the player to [onRevealControls], which is what
 * shows the hidden progress overlay. Back and Escape are left alone so the screen can still be
 * left the usual way.
 *
 * While the overlay is hidden a press is consumed: the player has nothing to focus, so a
 * directional press should reveal the overlay rather than wander. Once it is up the press only
 * counts as activity, which restarts the auto-hide timer, and is left unconsumed.
 *
 * Right is the one direction that does something of its own: it is the way from the video to the
 * rail of buttons down the right-hand edge, so it hands the focus to [onMoveRight] as well as
 * revealing the overlay.
 *
 * A Back press while the overlay is visible is not handled here: the overlay draws nothing
 * focusable, so the press reaches the screen's back handler, which dismisses the overlay instead
 * of leaving playback.
 *
 * Back is recognised by its native key code rather than by [Key.Back]. On Android TV the two do
 * not agree — a remote's Back arrives as KEYCODE_BACK but Compose does not map it onto `Key.Back`,
 * so a comparison against the named key never matches and Back gets treated as an ordinary D-pad
 * press. That is not cosmetic: calling [onRevealControls] writes overlay state from inside key
 * dispatch, which rebuilds this modifier's own element while the event is still travelling through
 * it, and the press is dropped on the floor instead of reaching the screen's back handler. TV
 * Material 3 carries the same check for the same reason.
 */
fun Modifier.playerOverlayControls(
    controlsVisible: Boolean,
    onRevealControls: () -> Unit,
    onMoveRight: () -> Unit = {}
): Modifier = this
    .focusable()
    .onKeyEvent { keyEvent ->
        if (keyEvent.type != KeyEventType.KeyDown) {
            false
        } else {
            when (keyEvent.nativeKeyEvent.keyCode) {
                NativeKeyEvent.KEYCODE_BACK, NativeKeyEvent.KEYCODE_ESCAPE -> false
                NativeKeyEvent.KEYCODE_DPAD_RIGHT -> {
                    onMoveRight()
                    !controlsVisible
                }
                else -> {
                    onRevealControls()
                    !controlsVisible
                }
            }
        }
    }

/**
 * Hands a Back press to [onBack] on whichever control actually holds focus, rather than letting
 * the platform route it to the screen's back handler.
 *
 * It has to be caught here. A Back press travels up the focus chain looking for something to
 * consume it, and on Android TV an ancestor marked [focusable] — which the player surface is, so
 * that the D-pad can reveal the overlay — swallows the press on its way past instead of letting it
 * reach the activity. The screen's back handler then never hears about it, and closing a menu
 * costs two presses: the first is eaten, the second arrives once focus has moved somewhere that
 * does let it through. Catching it on the focused control is what makes one press do the obvious
 * thing.
 */
fun Modifier.catchBackPress(onBack: () -> Unit): Modifier =
    onPreviewKeyEvent { keyEvent ->
        if (keyEvent.type == KeyEventType.KeyDown &&
            keyEvent.nativeKeyEvent.keyCode == NativeKeyEvent.KEYCODE_BACK
        ) {
            onBack()
            true
        } else {
            false
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
