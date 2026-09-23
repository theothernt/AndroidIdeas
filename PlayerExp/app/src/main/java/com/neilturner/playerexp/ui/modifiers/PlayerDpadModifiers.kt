package com.neilturner.playerexp.ui.modifiers

import androidx.compose.foundation.focusable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type

/**
 * Small, reusable behavior modifier to handle playback D-pad navigation and media remote keys.
 *
 * Adheres to Compose for TV patterns:
 * - Uses [Modifier.onKeyEvent] with Compose native [Key] and [KeyEventType].
 * - Only consumes events ([true]) on KeyDown when an action is executed.
 * - Leaves directional navigation to default focus traversal when not handled.
 */
fun Modifier.playerDpadControls(
    onSeekBackward: () -> Unit,
    onSeekForward: () -> Unit,
    onTogglePlayPause: (() -> Unit)? = null
): Modifier = this
    .focusable()
    .onKeyEvent { keyEvent ->
        if (keyEvent.type == KeyEventType.KeyDown) {
            when (keyEvent.key) {
                Key.DirectionLeft, Key.MediaRewind -> {
                    onSeekBackward()
                    true
                }
                Key.DirectionRight, Key.MediaFastForward -> {
                    onSeekForward()
                    true
                }
                Key.DirectionCenter, Key.Enter, Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> {
                    if (onTogglePlayPause != null) {
                        onTogglePlayPause()
                        true
                    } else {
                        false
                    }
                }
                else -> false
            }
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
