package com.neilturner.perfview.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/** Shared button metrics, matching the sizing used across the other AndroidIdeas TV apps. */
private val TvButtonWidth: Dp = 360.dp
val TvButtonHeight: Dp = 56.dp

/**
 * TV Material 3 scales a focused button by 1.1 by default. At this width that reads as a jump
 * rather than a highlight, so the emphasis is dialled back.
 */
private const val FOCUSED_SCALE = 1.03f
private const val PRESSED_SCALE = 0.97f

/**
 * Filled TV button, matching the style used in the sibling TV apps: a fixed height with the
 * label centred in the full button rather than relying on the button's own content padding.
 *
 * Requires [com.neilturner.perfview.ui.theme.PerfViewTvTheme] in the tree, since TV Material 3
 * reads its colours from its own theme rather than the phone Material 3 one.
 */
@Composable
fun TvActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = TvButtonWidth,
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .width(width)
            .height(TvButtonHeight),
        scale = ButtonDefaults.scale(
            focusedScale = FOCUSED_SCALE,
            pressedScale = PRESSED_SCALE,
        ),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Lower emphasis variant for secondary actions such as leaving a screen. Uses the unfocused
 * container colour for both states so it stays visibly quieter than [TvActionButton] while
 * still gaining the focus scale and press behaviour that make it read as a TV control.
 */
@Composable
fun TvSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = TvButtonWidth,
) {
    val container = MaterialTheme.colorScheme.surfaceVariant
    val onContainer = MaterialTheme.colorScheme.onSurfaceVariant

    Button(
        onClick = onClick,
        modifier = modifier
            .width(width)
            .height(TvButtonHeight),
        scale = ButtonDefaults.scale(
            focusedScale = FOCUSED_SCALE,
            pressedScale = PRESSED_SCALE,
        ),
        colors = ButtonDefaults.colors(
            containerColor = container,
            contentColor = onContainer,
            focusedContainerColor = onContainer,
            focusedContentColor = container,
        ),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}