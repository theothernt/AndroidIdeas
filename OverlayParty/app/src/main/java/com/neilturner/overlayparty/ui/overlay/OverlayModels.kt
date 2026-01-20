package com.neilturner.overlayparty.ui.overlay

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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
 * Position of the icon relative to the text.
 */
enum class IconPosition {
    LEADING,
    TRAILING
}

/**
 * Items that can be used in a flexible multi-item overlay.
 */
sealed interface OverlayItem {
    data class Text(val text: String, val scale: Float = 1f) : OverlayItem
    data class Icon(val icon: ImageVector) : OverlayItem
}

/**
 * Animation types for overlay updates.
 */
enum class OverlayAnimationType {
    NONE,             // No animation
    RESIZE, // Standard size animation, no fade
    FADE  // Crossfade between content updates
}

/**
 * Sealed interface representing different types of overlay content.
 * This allows any overlay type to be assigned to any corner position.
 */
sealed interface OverlayContent {
    val animationType: OverlayAnimationType
    val padding: Dp

    /**
     * Simple text-only overlay content.
     */
    data class TextOnly(
        val text: String,
        val scale: Float = 1f,
        override val animationType: OverlayAnimationType = OverlayAnimationType.NONE,
        override val padding: Dp = 8.dp
    ) : OverlayContent

    /**
     * Overlay content with an icon and text.
     */
    data class IconWithText(
        val text: String, 
        val icon: ImageVector,
        val iconPosition: IconPosition = IconPosition.LEADING,
        val scale: Float = 1f,
        override val animationType: OverlayAnimationType = OverlayAnimationType.NONE,
        override val padding: Dp = 8.dp
    ) : OverlayContent

    /**
     * Flexible content that can contain multiple items (text, icons, etc.).
     */
    data class MultiItemContent(
        val items: List<OverlayItem>,
        override val animationType: OverlayAnimationType = OverlayAnimationType.NONE,
        override val padding: Dp = 8.dp
    ) : OverlayContent

    /**
     * Vertical stack of independent overlay contents.
     */
    data class VerticalStack(
        val items: List<OverlayContent>,
        override val animationType: OverlayAnimationType = OverlayAnimationType.NONE,
        override val padding: Dp = 8.dp
    ) : OverlayContent
}
