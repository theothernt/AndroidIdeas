package com.neilturner.overlayparty.ui.overlay

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Position enum for the 4 screen corners where overlays can be placed.
 */
enum class OverlayPosition {
    TOP_START,
    TOP_END,
    BOTTOM_START,
    BOTTOM_END
}

/**
 * Sealed interface representing different types of overlay content.
 * This allows any overlay type to be assigned to any corner position.
 */
sealed interface OverlayContent {
    /**
     * Simple text-only overlay content.
     */
    data class TextOnly(val text: String) : OverlayContent

    /**
     * Overlay content with an icon and text.
     */
    data class IconWithText(val text: String, val icon: ImageVector) : OverlayContent
}
