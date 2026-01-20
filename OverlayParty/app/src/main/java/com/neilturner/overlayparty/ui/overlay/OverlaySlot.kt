package com.neilturner.overlayparty.ui.overlay

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.neilturner.overlayparty.ui.components.TextBlock

/**
 * A composable slot that renders any [OverlayContent] type.
 * This provides a unified way to display different overlay content types.
 *
 * @param content The overlay content to render, or null for an empty slot
 * @param modifier Modifier to apply to the overlay
 */
@Composable
fun OverlaySlot(
    content: OverlayContent?,
    modifier: Modifier = Modifier
) {
    if (content == null) return

    when (content) {
        is OverlayContent.TextOnly -> TextBlock(
            text = content.text,
            modifier = modifier
        )
        is OverlayContent.IconWithText -> TextBlock(
            text = content.text,
            icon = content.icon,
            modifier = modifier
        )
    }
}
